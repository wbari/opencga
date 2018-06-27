/*
 * Copyright 2015-2017 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.catalog.managers;

import org.apache.commons.lang3.StringUtils;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.catalog.audit.AuditManager;
import org.opencb.opencga.catalog.audit.AuditRecord;
import org.opencb.opencga.catalog.auth.authentication.LDAPUtils;
import org.opencb.opencga.catalog.auth.authorization.AuthorizationManager;
import org.opencb.opencga.catalog.db.DBAdaptorFactory;
import org.opencb.opencga.catalog.db.api.*;
import org.opencb.opencga.catalog.exceptions.CatalogAuthorizationException;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.exceptions.CatalogIOException;
import org.opencb.opencga.catalog.io.CatalogIOManager;
import org.opencb.opencga.catalog.io.CatalogIOManagerFactory;
import org.opencb.opencga.catalog.utils.CatalogAnnotationsValidator;
import org.opencb.opencga.catalog.utils.ParamUtils;
import org.opencb.opencga.core.common.TimeUtils;
import org.opencb.opencga.core.config.AuthenticationOrigin;
import org.opencb.opencga.core.config.Configuration;
import org.opencb.opencga.core.models.*;
import org.opencb.opencga.core.models.acls.AclParams;
import org.opencb.opencga.core.models.acls.permissions.*;
import org.opencb.opencga.core.models.stats.FileStats;
import org.opencb.opencga.core.models.stats.StudyStats;
import org.opencb.opencga.core.models.summaries.StudySummary;
import org.opencb.opencga.core.models.summaries.VariableSetSummary;
import org.opencb.opencga.core.models.summaries.VariableSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.naming.NamingException;
import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.opencb.opencga.catalog.auth.authorization.CatalogAuthorizationManager.checkPermissions;

/**
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class StudyManager extends AbstractManager {

    private static final String MEMBERS = "@members";
    private static final String ADMINS = "@admins";
    //[A-Za-z]([-_.]?[A-Za-z0-9]
    private static final String USER_PATTERN = "[A-Za-z][[-_.]?[A-Za-z0-9]?]*";
    private static final String PROJECT_PATTERN = "[A-Za-z0-9][[-_.]?[A-Za-z0-9]?]*";
    private static final String STUDY_PATTERN = "[A-Za-z0-9\\-_.]+|\\*";
    private static final Pattern USER_PROJECT_STUDY_PATTERN = Pattern.compile("^(" + USER_PATTERN + ")@(" + PROJECT_PATTERN + "):("
            + STUDY_PATTERN + ")$");
    private static final Pattern PROJECT_STUDY_PATTERN = Pattern.compile("^(" + PROJECT_PATTERN + "):(" + STUDY_PATTERN + ")$");

    protected Logger logger;

    StudyManager(AuthorizationManager authorizationManager, AuditManager auditManager, CatalogManager catalogManager,
                 DBAdaptorFactory catalogDBAdaptorFactory, CatalogIOManagerFactory ioManagerFactory, Configuration configuration) {
        super(authorizationManager, auditManager, catalogManager, catalogDBAdaptorFactory, ioManagerFactory, configuration);

        logger = LoggerFactory.getLogger(StudyManager.class);
    }

    public Long getProjectId(long studyId) throws CatalogException {
        return studyDBAdaptor.getProjectIdByStudyId(studyId);
    }

    public List<Study> resolveIds(List<String> studyList, String userId) throws CatalogException {
        if (studyList == null || studyList.isEmpty() || (studyList.size() == 1 && studyList.get(0).endsWith("*"))) {
            String studyStr = "*";
            if (studyList != null && !studyList.isEmpty()) {
                studyStr = studyList.get(0);
            }

            return smartResolutor(studyStr, userId).getResult();
        }

        List<Study> returnList = new ArrayList<>(studyList.size());
        for (String study : studyList) {
            returnList.add(resolveId(study, userId));
        }
        return returnList;
    }

    public Study resolveId(String studyStr, String userId) throws CatalogException {
        QueryResult<Study> studyQueryResult = smartResolutor(studyStr, userId);

        if (studyQueryResult.getNumResults() > 1) {
            throw new CatalogException("More than one study found. Please, be more specific. The accepted pattern is "
                    + "[ownerId@projectId:studyId]");
        }

        return studyQueryResult.first();
    }

    private QueryResult<Study> smartResolutor(String studyStr, String userId) throws CatalogException {
        String owner = null;
        String project = null;
        String study = null;

        if (StringUtils.isNotEmpty(studyStr)) {
            Matcher matcher = USER_PROJECT_STUDY_PATTERN.matcher(studyStr);
            if (matcher.find()) {
                // studyStr contains the full path (owner@project:study)
                owner = matcher.group(1);
                project = matcher.group(2);
                study = matcher.group(3);
            } else {
                matcher = PROJECT_STUDY_PATTERN.matcher(studyStr);
                if (matcher.find()) {
                    // studyStr contains the path (project:study)
                    project = matcher.group(1);
                    study = matcher.group(2);
                } else {
                    // studyStr only contains the study information
                    study = studyStr;
                }
            }
        }

        // Empty study if we are actually asking for all possible
        if (!StringUtils.isEmpty(study) && study.equals("*")) {
            study = null;
        }

        Query query = new Query();
        query.putIfNotEmpty(StudyDBAdaptor.QueryParams.OWNER.key(), owner);
        query.putIfNotEmpty(StudyDBAdaptor.QueryParams.PROJECT_ID.key(), project);
        query.putIfNotEmpty(StudyDBAdaptor.QueryParams.ID.key(), study);
        query.putIfNotEmpty(StudyDBAdaptor.QueryParams.ALIAS.key(), study);

        QueryOptions options = new QueryOptions(QueryOptions.INCLUDE, Arrays.asList(
                StudyDBAdaptor.QueryParams.ID.key(), StudyDBAdaptor.QueryParams.UID.key(), StudyDBAdaptor.QueryParams.ALIAS.key(),
                StudyDBAdaptor.QueryParams.CREATION_DATE.key(), StudyDBAdaptor.QueryParams.FQN.key(), StudyDBAdaptor.QueryParams.URI.key()
        ));

        QueryResult<Study> studyQueryResult = studyDBAdaptor.get(query, options, userId);

        if (studyQueryResult.getNumResults() == 0) {
            studyQueryResult = studyDBAdaptor.get(query, options);
            if (studyQueryResult.getNumResults() == 0) {
                throw new CatalogException("No study found or the user " + userId + " does not have permissions to view any.");
            } else {
                throw CatalogAuthorizationException.deny(userId, "view", "study", studyQueryResult.first().getFqn(), null);
            }
        }

        return studyQueryResult;
    }

    public QueryResult<Study> create(String projectStr, String id, String alias, String name, Study.Type type, String creationDate,
                                     String description, Status status, String cipher, String uriScheme, URI uri,
                                     Map<File.Bioformat, DataStore> datastores, Map<String, Object> stats, Map<String, Object> attributes,
                                     QueryOptions options, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(name, "name");
        ParamUtils.checkParameter(id, "id");
        ParamUtils.checkObj(type, "type");
        ParamUtils.checkAlias(id, "id");

        String userId = catalogManager.getUserManager().getUserId(sessionId);
        Project project = catalogManager.getProjectManager().resolveId(projectStr, userId);

        long projectId = project.getUid();

        description = ParamUtils.defaultString(description, "");
//        creatorId = ParamUtils.defaultString(creatorId, userId);
        creationDate = ParamUtils.defaultString(creationDate, TimeUtils.getTime());
        status = ParamUtils.defaultObject(status, Status::new);
        cipher = ParamUtils.defaultString(cipher, "none");
        if (uri != null) {
            if (uri.getScheme() == null) {
                throw new CatalogException("StudyUri must specify the scheme");
            } else {
                if (uriScheme != null && !uriScheme.isEmpty()) {
                    if (!uriScheme.equals(uri.getScheme())) {
                        throw new CatalogException("StudyUri must specify the scheme");
                    }
                } else {
                    uriScheme = uri.getScheme();
                }
            }
        } else {
            uriScheme = catalogIOManagerFactory.getDefaultCatalogScheme();
        }
        datastores = ParamUtils.defaultObject(datastores, HashMap<File.Bioformat, DataStore>::new);
        stats = ParamUtils.defaultObject(stats, HashMap<String, Object>::new);
        attributes = ParamUtils.defaultObject(attributes, HashMap<String, Object>::new);

        CatalogIOManager catalogIOManager = catalogIOManagerFactory.get(uriScheme);

//        String projectOwnerId = projectDBAdaptor.getProjectOwnerId(projectId);


        /* Check project permissions */
        if (!project.getFqn().startsWith(userId + "@")) {
            throw new CatalogException("Permission denied: Only the owner of the project can create studies.");
        }

        LinkedList<File> files = new LinkedList<>();
        LinkedList<Experiment> experiments = new LinkedList<>();
        LinkedList<Job> jobs = new LinkedList<>();

        File rootFile = new File(".", File.Type.DIRECTORY, null, null, "", "study root folder",
                new File.FileStatus(File.FileStatus.READY), 0, project.getCurrentRelease());
        files.add(rootFile);

        // We set all the permissions for the owner of the study.
        // StudyAcl studyAcl = new StudyAcl(userId, AuthorizationManager.getAdminAcls());

        Study study = new Study(id, name, alias, type, creationDate, description, status, TimeUtils.getTime(),
                0, cipher, Arrays.asList(new Group(MEMBERS, Collections.emptyList()), new Group(ADMINS, Collections.emptyList())),
                experiments, files, jobs, new LinkedList<>(), new LinkedList<>(), new LinkedList<>(), new LinkedList<>(),
                Collections.emptyList(), new LinkedList<>(), null, null, datastores, project.getCurrentRelease(), stats,
                attributes);

        /* CreateStudy */
        QueryResult<Study> result = studyDBAdaptor.insert(project, study, options);
        study = result.getResult().get(0);

        //URI studyUri;
        if (uri == null) {
            try {
                uri = catalogIOManager.createStudy(userId, Long.toString(projectId), Long.toString(study.getUid()));
            } catch (CatalogIOException e) {
                try {
                    studyDBAdaptor.delete(study.getUid());
                } catch (Exception e1) {
                    logger.error("Can't delete study after failure creating study", e1);
                }
                throw e;
            }
        }

        study = studyDBAdaptor.update(study.getUid(), new ObjectMap("uri", uri), QueryOptions.empty()).first();
        auditManager.recordCreation(AuditRecord.Resource.study, study.getUid(), userId, study, null, null);

        long rootFileId = fileDBAdaptor.getId(study.getUid(), "");    //Set studyUri to the root folder too
        rootFile = fileDBAdaptor.update(rootFileId, new ObjectMap("uri", uri), QueryOptions.empty()).first();
        auditManager.recordCreation(AuditRecord.Resource.file, rootFile.getUid(), userId, rootFile, null, null);

        userDBAdaptor.updateUserLastModified(userId);

        result.setResult(Arrays.asList(study));
        return result;
    }

    int getCurrentRelease(Study study, String userId) throws CatalogException {
        return catalogManager.getProjectManager().resolveId(StringUtils.split(study.getFqn(), ":")[0], userId).getCurrentRelease();
    }

    public MyResourceId getVariableSetId(String variableStr, @Nullable String studyStr, String sessionId) throws CatalogException {
        if (StringUtils.isEmpty(variableStr)) {
            throw new CatalogException("Missing variableSet parameter");
        }

        String userId;
        long studyId;
        long variableSetId;

        if (StringUtils.isNumeric(variableStr) && Long.parseLong(variableStr) > configuration.getCatalog().getOffset()) {
            variableSetId = Long.parseLong(variableStr);
            Query query = new Query(StudyDBAdaptor.QueryParams.VARIABLE_SET_UID.key(), variableSetId);
            QueryResult<Study> studyQueryResult = studyDBAdaptor.get(query, new QueryOptions(QueryOptions.INCLUDE,
                    StudyDBAdaptor.QueryParams.UID.key()));
            if (studyQueryResult.getNumResults() == 0) {
                throw new CatalogException("Variable set " + variableStr + " not found");
            }
            studyId = studyQueryResult.first().getUid();
            userId = catalogManager.getUserManager().getUserId(sessionId);
        } else {
            if (variableStr.contains(",")) {
                throw new CatalogException("More than one variable set found. Please, choose just one variable set");
            }

            userId = catalogManager.getUserManager().getUserId(sessionId);
            Study study = catalogManager.getStudyManager().resolveId(studyStr, userId);
            studyId = study.getUid();

            Query query = new Query()
                    .append(StudyDBAdaptor.VariableSetParams.STUDY_ID.key(), study.getUid())
                    .append(StudyDBAdaptor.VariableSetParams.ID.key(), variableStr);
            QueryOptions queryOptions = new QueryOptions();
            QueryResult<VariableSet> variableSetQueryResult = studyDBAdaptor.getVariableSets(query, queryOptions);
            if (variableSetQueryResult.getNumResults() == 0) {
                throw new CatalogException("Variable set " + variableStr + " not found in study " + studyStr);
            } else if (variableSetQueryResult.getNumResults() > 1) {
                throw new CatalogException("More than one variable set found under " + variableStr + " in study " + studyStr);
            }
            variableSetId = variableSetQueryResult.first().getUid();
        }

        return new MyResourceId(userId, studyId, variableSetId);
    }

    /**
     * Fetch a study from Catalog given a study id or alias.
     *
     * @param studyStr  Study id or alias.
     * @param options   Read options
     * @param sessionId sessionId
     * @return The specified object
     * @throws CatalogException CatalogException
     */
    public QueryResult<Study> get(String studyStr, QueryOptions options, String sessionId) throws CatalogException {
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        String userId = catalogManager.getUserManager().getUserId(sessionId);
        Study study = catalogManager.getStudyManager().resolveId(studyStr, userId);

        Query query = new Query(StudyDBAdaptor.QueryParams.UID.key(), study.getUid());
        QueryResult<Study> studyQueryResult = studyDBAdaptor.get(query, options, userId);
        if (studyQueryResult.getNumResults() <= 0) {
            throw CatalogAuthorizationException.deny(userId, "view", "study", study.getFqn(), "");
        }
        return studyQueryResult;
    }

    public List<QueryResult<Study>> get(List<String> studyList, QueryOptions queryOptions, boolean silent, String sessionId)
            throws CatalogException {
        List<QueryResult<Study>> results = new ArrayList<>(studyList.size());
        for (String study : studyList) {
            try {
                QueryResult<Study> studyObj = get(study, queryOptions, sessionId);
                results.add(studyObj);
            } catch (CatalogException e) {
                if (silent) {
                    results.add(new QueryResult<>(study, 0, 0, 0, "", e.toString(), new ArrayList<>(0)));
                } else {
                    throw e;
                }
            }
        }
        return results;
    }

    /**
     * Fetch all the study objects matching the query.
     *
     * @param projectStr Project id or alias.
     * @param query      Query to catalog.
     * @param options    Query options, like "include", "exclude", "limit" and "skip"
     * @param sessionId  sessionId
     * @return All matching elements.
     * @throws CatalogException CatalogException
     */
    public QueryResult<Study> get(String projectStr, Query query, QueryOptions options, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(projectStr, "project");
        ParamUtils.defaultObject(query, Query::new);
        ParamUtils.defaultObject(options, QueryOptions::new);

        String auxProject = null;
        String auxOwner = null;
        if (StringUtils.isNotEmpty(projectStr)) {
            String[] split = projectStr.split("@");
            if (split.length == 1) {
                auxProject = projectStr;
            } else if (split.length == 2) {
                auxOwner = split[0];
                auxProject = split[1];
            } else {
                throw new CatalogException(projectStr + " does not follow the expected pattern [ownerId@projectId]");
            }
        }

        query.putIfNotNull(StudyDBAdaptor.QueryParams.PROJECT_ID.key(), auxProject);
        query.putIfNotNull(StudyDBAdaptor.QueryParams.OWNER.key(), auxOwner);

        return get(query, options, sessionId);
    }

    public List<QueryResult<Study>> get(List<String> projectList, Query query, QueryOptions options, boolean silent, String sessionId)
            throws CatalogException {
        List<QueryResult<Study>> results = new ArrayList<>(projectList.size());
        for (String project : projectList) {
            try {
                QueryResult<Study> studyObj = get(project, query, options, sessionId);
                results.add(studyObj);
            } catch (CatalogException e) {
                if (silent) {
                    results.add(new QueryResult<>(project, 0, 0, 0, "", e.toString(), new ArrayList<>(0)));
                } else {
                    throw e;
                }
            }
        }
        return results;
    }


    /**
     * Fetch all the study objects matching the query.
     *
     * @param query     Query to catalog.
     * @param options   Query options, like "include", "exclude", "limit" and "skip"
     * @param sessionId sessionId
     * @return All matching elements.
     * @throws CatalogException CatalogException
     */
    public QueryResult<Study> get(Query query, QueryOptions options, String sessionId) throws CatalogException {
        query = ParamUtils.defaultObject(query, Query::new);
        QueryOptions qOptions = options != null ? new QueryOptions(options) : new QueryOptions();

        String userId = catalogManager.getUserManager().getUserId(sessionId);

        if (!qOptions.containsKey("include") || qOptions.get("include") == null || qOptions.getAsStringList("include").isEmpty()) {
            qOptions.addToListOption("exclude", "projects.studies.attributes.studyConfiguration");
        }

        return studyDBAdaptor.get(query, qOptions, userId);
    }

    /**
     * Update an existing catalog study.
     *
     * @param studyStr   Study id or alias.
     * @param parameters Parameters to change.
     * @param options    options
     * @param sessionId  sessionId
     * @return The modified entry.
     * @throws CatalogException CatalogException
     */
    public QueryResult<Study> update(String studyStr, ObjectMap parameters, QueryOptions options, String sessionId)
            throws CatalogException {
        ParamUtils.checkObj(parameters, "Parameters");
        String userId = catalogManager.getUserManager().getUserId(sessionId);
        Study study = resolveId(studyStr, userId);

        authorizationManager.checkCanEditStudy(study.getUid(), userId);

        if (parameters.containsKey("alias")) {
            rename(study.getUid(), parameters.getString("alias"), sessionId);

            //Clone and remove alias from parameters. Do not modify the original parameter
            parameters = new ObjectMap(parameters);
            parameters.remove("alias");
        }
        for (String s : parameters.keySet()) {
            if (!s.matches("name|type|description|attributes|stats")) {
                throw new CatalogDBException("Parameter '" + s + "' can't be changed");
            }
        }

        String ownerId = getOwner(study);
        userDBAdaptor.updateUserLastModified(ownerId);
        QueryResult<Study> result = studyDBAdaptor.update(study.getUid(), parameters, options);
        auditManager.recordUpdate(AuditRecord.Resource.study, study.getUid(), userId, parameters, null, null);
        return result;
    }

    public QueryResult<PermissionRule> createPermissionRule(String studyStr, Study.Entity entry, PermissionRule permissionRule,
                                                            String sessionId) throws CatalogException {
        ParamUtils.checkObj(entry, "entry");
        ParamUtils.checkObj(permissionRule, "permission rule");

        String userId = catalogManager.getUserManager().getUserId(sessionId);
        Study study = resolveId(studyStr, userId);

        authorizationManager.checkCanUpdatePermissionRules(study.getUid(), userId);
        validatePermissionRules(study.getUid(), entry, permissionRule);

        studyDBAdaptor.createPermissionRule(study.getUid(), entry, permissionRule);

        return new QueryResult<>(study.getFqn(), -1, 1, 1, "", "", Collections.singletonList(permissionRule));
    }

    public void markDeletedPermissionRule(String studyStr, Study.Entity entry, String permissionRuleId,
                                          PermissionRule.DeleteAction deleteAction, String sessionId) throws CatalogException {
        ParamUtils.checkObj(entry, "entry");
        ParamUtils.checkObj(deleteAction, "Delete action");
        ParamUtils.checkObj(permissionRuleId, "permission rule id");

        String userId = catalogManager.getUserManager().getUserId(sessionId);
        Study study = resolveId(studyStr, userId);

        authorizationManager.checkCanUpdatePermissionRules(study.getUid(), userId);

        studyDBAdaptor.markDeletedPermissionRule(study.getUid(), entry, permissionRuleId, deleteAction);
    }

    public QueryResult<PermissionRule> getPermissionRules(String studyStr, Study.Entity entry, String sessionId) throws CatalogException {
        String userId = catalogManager.getUserManager().getUserId(sessionId);
        Study study = resolveId(studyStr, userId);

        authorizationManager.checkCanViewStudy(study.getUid(), userId);
        return studyDBAdaptor.getPermissionRules(study.getUid(), entry);
    }

    public QueryResult rank(long projectId, Query query, String field, int numResults, boolean asc, String sessionId)
            throws CatalogException {
        query = ParamUtils.defaultObject(query, Query::new);
        ParamUtils.checkObj(field, "field");
        ParamUtils.checkObj(projectId, "projectId");

        String userId = catalogManager.getUserManager().getUserId(sessionId);
        authorizationManager.checkCanViewProject(projectId, userId);

        // TODO: In next release, we will have to check the count parameter from the queryOptions object.
        boolean count = true;
//        query.append(CatalogFileDBAdaptor.QueryParams.STUDY_UID.key(), studyId);
        QueryResult queryResult = null;
        if (count) {
            // We do not need to check for permissions when we show the count of files
            queryResult = studyDBAdaptor.rank(query, field, numResults, asc);
        }

        return ParamUtils.defaultObject(queryResult, QueryResult::new);
    }

    public QueryResult groupBy(long projectId, Query query, String field, QueryOptions options, String sessionId) throws CatalogException {
        return groupBy(projectId, query, Collections.singletonList(field), options, sessionId);
    }

    public QueryResult groupBy(long projectId, Query query, List<String> fields, QueryOptions options, String sessionId)
            throws CatalogException {
        query = ParamUtils.defaultObject(query, Query::new);
        options = ParamUtils.defaultObject(options, QueryOptions::new);
        ParamUtils.checkObj(fields, "fields");
        ParamUtils.checkObj(projectId, "projectId");

        String userId = catalogManager.getUserManager().getUserId(sessionId);
        authorizationManager.checkCanViewProject(projectId, userId);

        // TODO: In next release, we will have to check the count parameter from the queryOptions object.
        boolean count = true;
        QueryResult queryResult = null;
        if (count) {
            // We do not need to check for permissions when we show the count of files
            queryResult = studyDBAdaptor.groupBy(query, fields, options);
        }

        return ParamUtils.defaultObject(queryResult, QueryResult::new);
    }

    public QueryResult<StudySummary> getSummary(String studyStr, QueryOptions queryOptions, String sessionId) throws CatalogException {
        long startTime = System.currentTimeMillis();

        Study study = get(studyStr, queryOptions, sessionId).first();

        StudySummary studySummary = new StudySummary()
                .setAlias(study.getId())
                .setAttributes(study.getAttributes())
                .setCipher(study.getCipher())
                .setCreationDate(study.getCreationDate())
                .setDatasets(study.getDatasets().size())
                .setDescription(study.getDescription())
                .setDiskUsage(study.getSize())
                .setExperiments(study.getExperiments())
                .setGroups(study.getGroups())
                .setName(study.getName())
                .setStats(study.getStats())
                .setStatus(study.getStatus())
                .setType(study.getType())
                .setVariableSets(study.getVariableSets());

        Long nFiles = fileDBAdaptor.count(
                new Query(FileDBAdaptor.QueryParams.STUDY_UID.key(), study.getUid())
                        .append(FileDBAdaptor.QueryParams.TYPE.key(), File.Type.FILE)
                        .append(FileDBAdaptor.QueryParams.STATUS_NAME.key(), "!=" + File.FileStatus.TRASHED + ";!="
                                + File.FileStatus.DELETED))
                .first();
        studySummary.setFiles(nFiles);

        Long nSamples = sampleDBAdaptor.count(
                new Query(SampleDBAdaptor.QueryParams.STUDY_UID.key(), study.getUid())
                        .append(SampleDBAdaptor.QueryParams.STATUS_NAME.key(), "!=" + File.FileStatus.TRASHED + ";!="
                                + File.FileStatus.DELETED))
                .first();
        studySummary.setSamples(nSamples);

        Long nJobs = jobDBAdaptor.count(
                new Query(JobDBAdaptor.QueryParams.STUDY_UID.key(), study.getUid())
                        .append(JobDBAdaptor.QueryParams.STATUS_NAME.key(), "!=" + File.FileStatus.TRASHED + ";!="
                                + File.FileStatus.DELETED))
                .first();
        studySummary.setJobs(nJobs);

        Long nCohorts = cohortDBAdaptor.count(
                new Query(CohortDBAdaptor.QueryParams.STUDY_UID.key(), study.getUid())
                        .append(CohortDBAdaptor.QueryParams.STATUS_NAME.key(), "!=" + File.FileStatus.TRASHED + ";!="
                                + File.FileStatus.DELETED))
                .first();
        studySummary.setCohorts(nCohorts);

        Long nIndividuals = individualDBAdaptor.count(
                new Query(IndividualDBAdaptor.QueryParams.STUDY_UID.key(), study.getUid())
                        .append(IndividualDBAdaptor.QueryParams.STATUS_NAME.key(), "!=" + File.FileStatus.TRASHED + ";!="
                                + File.FileStatus.DELETED))
                .first();
        studySummary.setIndividuals(nIndividuals);

        return new QueryResult<>("Study summary", (int) (System.currentTimeMillis() - startTime), 1, 1, "", "",
                Collections.singletonList(studySummary));
    }

    public List<QueryResult<StudySummary>> getSummary(List<String> studyList, QueryOptions queryOptions, boolean silent, String sessionId)
            throws CatalogException {
        List<QueryResult<StudySummary>> results = new ArrayList<>(studyList.size());
        for (String aStudyList : studyList) {
            try {
                QueryResult<StudySummary> summaryObj = getSummary(aStudyList, queryOptions, sessionId);
                results.add(summaryObj);
            } catch (CatalogException e) {
                if (silent) {
                    results.add(new QueryResult<>(aStudyList, 0, 0, 0, "", e.toString(), new ArrayList<>(0)));
                } else {
                    throw e;
                }
            }
        }
        return results;
    }

    public QueryResult<Group> createGroup(String studyStr, String groupId, String users, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(groupId, "group name");

        String userId = catalogManager.getUserManager().getUserId(sessionId);
        Study study = resolveId(studyStr, userId);

        // Fix the groupId
        if (!groupId.startsWith("@")) {
            groupId = "@" + groupId;
        }

        authorizationManager.checkCreateDeleteGroupPermissions(study.getUid(), userId, groupId);

        // Create the list of users
        List<String> userList;
        if (StringUtils.isNotEmpty(users)) {
            userList = Arrays.asList(users.split(","));
        } else {
            userList = Collections.emptyList();
        }

        // Check group exists
        if (existsGroup(study.getUid(), groupId)) {
            throw new CatalogException("The group " + groupId + " already exists.");
        }

        // Check the list of users is ok
        if (userList.size() > 0) {
            userDBAdaptor.checkIds(userList);
        }

        // Add those users to the members group
        studyDBAdaptor.addUsersToGroup(study.getUid(), MEMBERS, userList);
        // Create the group
        return studyDBAdaptor.createGroup(study.getUid(), new Group(groupId, userList));
    }

    public QueryResult<Group> getGroup(String studyStr, String groupId, String sessionId) throws CatalogException {
        String userId = catalogManager.getUserManager().getUserId(sessionId);
        Study study = resolveId(studyStr, userId);
        authorizationManager.checkCanViewStudy(study.getUid(), userId);

        // Fix the groupId
        if (groupId != null && !groupId.startsWith("@")) {
            groupId = "@" + groupId;
        }

        return studyDBAdaptor.getGroup(study.getUid(), groupId, Collections.emptyList());
    }

    public List<QueryResult<Group>> getGroup(List<String> studyList, String groupId, boolean silent, String sessionId)
            throws CatalogException {
        List<QueryResult<Group>> results = new ArrayList<>(studyList.size());
        for (String aStudyList : studyList) {
            try {
                QueryResult<Group> groupObj = getGroup(aStudyList, groupId, sessionId);
                results.add(groupObj);
            } catch (CatalogException e) {
                if (silent) {
                    results.add(new QueryResult<>(aStudyList, 0, 0, 0, "", e.toString(), new ArrayList<>(0)));
                } else {
                    throw e;
                }
            }
        }
        return results;
    }

    public QueryResult<Group> updateGroup(String studyStr, String groupId, GroupParams groupParams, String sessionId)
            throws CatalogException {
        ParamUtils.checkObj(groupParams, "Group parameters");
        ParamUtils.checkParameter(groupId, "Group name");
        ParamUtils.checkObj(groupParams.getAction(), "Action");

        String userId = catalogManager.getUserManager().getUserId(sessionId);
        Study study = resolveId(studyStr, userId);

        // Fix the group name
        if (!groupId.startsWith("@")) {
            groupId = "@" + groupId;
        }

        authorizationManager.checkUpdateGroupPermissions(study.getUid(), userId, groupId, groupParams);

        List<String> users;
        if (StringUtils.isNotEmpty(groupParams.getUsers())) {
            users = Arrays.asList(groupParams.getUsers().split(","));
            List<String> tmpUsers = users;
            if (groupId.equals(MEMBERS) || groupId.equals(ADMINS)) {
                // Remove anonymous user if present for the checks.
                // Anonymous user is only allowed in MEMBERS group, otherwise we keep it as if it is present it should fail.
                tmpUsers = users.stream().filter(user -> !user.equals(ANONYMOUS)).collect(Collectors.toList());
            }
            if (tmpUsers.size() > 0) {
                userDBAdaptor.checkIds(tmpUsers);
            }
        } else {
            users = Collections.emptyList();
        }

        // Fix the group name
        if (!groupId.startsWith("@")) {
            groupId = "@" + groupId;
        }

        switch (groupParams.getAction()) {
            case SET:
                studyDBAdaptor.setUsersToGroup(study.getUid(), groupId, users);
                studyDBAdaptor.addUsersToGroup(study.getUid(), MEMBERS, users);
                break;
            case ADD:
                studyDBAdaptor.addUsersToGroup(study.getUid(), groupId, users);
                if (!groupId.equals(MEMBERS)) {
                    studyDBAdaptor.addUsersToGroup(study.getUid(), MEMBERS, users);
                }
                break;
            case REMOVE:
                if (groupId.equals(MEMBERS)) {
                    // We remove the users from all the groups and acls
                    authorizationManager.resetPermissionsFromAllEntities(study.getUid(), users);
                    studyDBAdaptor.removeUsersFromAllGroups(study.getUid(), users);
                } else {
                    studyDBAdaptor.removeUsersFromGroup(study.getUid(), groupId, users);
                }
                break;
            default:
                throw new CatalogException("Unknown action " + groupParams.getAction() + " found.");
        }

        return studyDBAdaptor.getGroup(study.getUid(), groupId, Collections.emptyList());
    }

    public QueryResult<Group> syncGroupWith(String studyStr, String externalGroup, String catalogGroup, String authenticationOriginId,
                                            boolean force, String token) throws CatalogException {
        if (!ROOT.equals(catalogManager.getUserManager().getUserId(token))) {
            throw new CatalogAuthorizationException("Only the root of OpenCGA can synchronise groups");
        }

        ParamUtils.checkObj(studyStr, "study");
        ParamUtils.checkObj(externalGroup, "external group");
        ParamUtils.checkObj(catalogGroup, "catalog group");
        ParamUtils.checkObj(authenticationOriginId, "authentication origin");

        AuthenticationOrigin authenticationOrigin = getAuthenticationOrigin(authenticationOriginId);
        if (authenticationOrigin == null) {
            throw new CatalogException("Authentication origin " + authenticationOriginId + " not found");
        }

        try {
            String base = ((String) authenticationOrigin.getOptions().get(AuthenticationOrigin.GROUPS_SEARCH));
            if (!LDAPUtils.existsLDAPGroup(authenticationOrigin.getHost(), externalGroup, base)) {
                throw new CatalogException("Group " + externalGroup + " not found in origin " + authenticationOriginId);
            }
        } catch (NamingException e) {
            logger.error("{}", e.getMessage(), e);
            throw new CatalogException("Unexpected LDAP error: " + e.getMessage());
        }

        Study study = resolveId(studyStr, ROOT);

        // Fix the groupId
        if (!catalogGroup.startsWith("@")) {
            catalogGroup = "@" + catalogGroup;
        }

        QueryResult<Group> group = studyDBAdaptor.getGroup(study.getUid(), catalogGroup, Collections.emptyList());
        if (group.getNumResults() == 1) {
            if (group.first().getSyncedFrom() != null && StringUtils.isNotEmpty(group.first().getSyncedFrom().getAuthOrigin())
                    && StringUtils.isNotEmpty(group.first().getSyncedFrom().getRemoteGroup())) {
                if (authenticationOriginId.equals(group.first().getSyncedFrom().getAuthOrigin())
                        && externalGroup.equals(group.first().getSyncedFrom().getRemoteGroup())) {
                    // It is already synced with that group from that authentication origin
                    return group;
                } else {
                    throw new CatalogException("The group " + catalogGroup + " is already synced with the group " + externalGroup + " "
                            + "from " + authenticationOriginId + ". If you still want to sync the group with the new external group, "
                            + "please use the force parameter.");
                }
            }

            if (!force) {
                throw new CatalogException("Cannot sync the group " + catalogGroup + " because it already exist in Catalog. Please, use "
                        + "force parameter if you still want sync it.");
            }

            // We remove all the users belonging to that group and resync it with the new external group
            studyDBAdaptor.removeUsersFromGroup(study.getUid(), catalogGroup, group.first().getUserIds());
            studyDBAdaptor.syncGroup(study.getUid(), catalogGroup, new Group.Sync(authenticationOriginId, externalGroup));
        } else {
            // We need to create a new group
            Group newGroup = new Group(catalogGroup, Collections.emptyList(), new Group.Sync(authenticationOriginId, externalGroup));
            studyDBAdaptor.createGroup(study.getUid(), newGroup);
        }

        return studyDBAdaptor.getGroup(study.getUid(), catalogGroup, Collections.emptyList());
    }


    public QueryResult<Group> syncGroupWith(String studyStr, String groupId, Group.Sync syncedFrom, String sessionId)
            throws CatalogException {
        ParamUtils.checkObj(syncedFrom, "sync");

        String userId = catalogManager.getUserManager().getUserId(sessionId);
        Study study = resolveId(studyStr, userId);

        if (StringUtils.isEmpty(groupId)) {
            throw new CatalogException("Missing group name parameter");
        }

        // Fix the groupId
        if (!groupId.startsWith("@")) {
            groupId = "@" + groupId;
        }

        authorizationManager.checkSyncGroupPermissions(study.getUid(), userId, groupId);

        QueryResult<Group> group = studyDBAdaptor.getGroup(study.getUid(), groupId, Collections.emptyList());
        if (group.first().getSyncedFrom() != null && StringUtils.isNotEmpty(group.first().getSyncedFrom().getAuthOrigin())
                && StringUtils.isNotEmpty(group.first().getSyncedFrom().getRemoteGroup())) {
            throw new CatalogException("Cannot modify already existing sync information.");
        }

        // Check the group exists
        Query query = new Query()
                .append(StudyDBAdaptor.QueryParams.UID.key(), study.getUid())
                .append(StudyDBAdaptor.QueryParams.GROUP_NAME.key(), groupId);
        if (studyDBAdaptor.count(query).first() == 0) {
            throw new CatalogException("The group " + groupId + " does not exist.");
        }

        studyDBAdaptor.syncGroup(study.getUid(), groupId, syncedFrom);

        return studyDBAdaptor.getGroup(study.getUid(), groupId, Collections.emptyList());
    }

    public QueryResult<Group> deleteGroup(String studyStr, String groupId, String sessionId) throws CatalogException {
        String userId = catalogManager.getUserManager().getUserId(sessionId);
        Study study = resolveId(studyStr, userId);

        // Fix the groupId
        if (!groupId.startsWith("@")) {
            groupId = "@" + groupId;
        }

        authorizationManager.checkCreateDeleteGroupPermissions(study.getUid(), userId, groupId);

        QueryResult<Group> group = studyDBAdaptor.getGroup(study.getUid(), groupId, Collections.emptyList());
        group.setId("Delete group");

        // Remove the permissions the group might have had
        Study.StudyAclParams aclParams = new Study.StudyAclParams(null, AclParams.Action.RESET, null);
        updateAcl(Collections.singletonList(studyStr), groupId, aclParams, sessionId);

        studyDBAdaptor.deleteGroup(study.getUid(), groupId);

        return group;
    }

    public QueryResult<VariableSetSummary> getVariableSetSummary(String studyStr, String variableSetStr, String sessionId)
            throws CatalogException {
        MyResourceId resource = getVariableSetId(variableSetStr, studyStr, sessionId);

        String userId = resource.getUser();

        QueryResult<VariableSet> variableSet = studyDBAdaptor.getVariableSet(resource.getResourceId(), new QueryOptions(), userId);
        if (variableSet.getNumResults() == 0) {
            logger.error("getVariableSetSummary: Could not find variable set id {}. {} results returned", variableSetStr,
                    variableSet.getNumResults());
            throw new CatalogDBException("Variable set " + variableSetStr + " not found.");
        }

        int dbTime = 0;

        VariableSetSummary variableSetSummary = new VariableSetSummary(resource.getResourceId(), variableSet.first().getId());

        QueryResult<VariableSummary> annotationSummary = sampleDBAdaptor.getAnnotationSummary(resource.getStudyId(),
                resource.getResourceId());
        dbTime += annotationSummary.getDbTime();
        variableSetSummary.setSamples(annotationSummary.getResult());

        annotationSummary = cohortDBAdaptor.getAnnotationSummary(resource.getStudyId(), resource.getResourceId());
        dbTime += annotationSummary.getDbTime();
        variableSetSummary.setCohorts(annotationSummary.getResult());

        annotationSummary = individualDBAdaptor.getAnnotationSummary(resource.getStudyId(), resource.getResourceId());
        dbTime += annotationSummary.getDbTime();
        variableSetSummary.setIndividuals(annotationSummary.getResult());

        annotationSummary = familyDBAdaptor.getAnnotationSummary(resource.getStudyId(), resource.getResourceId());
        dbTime += annotationSummary.getDbTime();
        variableSetSummary.setFamilies(annotationSummary.getResult());

        return new QueryResult<>("Variable set summary", dbTime, 1, 1, "", "", Arrays.asList(variableSetSummary));
    }


    /*
     * Variables Methods
     */
    QueryResult<VariableSet> createVariableSet(Study study, String id, String name, Boolean unique, Boolean confidential,
                                               String description, Map<String, Object> attributes, List<Variable> variables,
                                               String sessionId) throws CatalogException {
        ParamUtils.checkParameter(id, "id");
        ParamUtils.checkObj(variables, "Variables from VariableSet");
        String userId = catalogManager.getUserManager().getUserId(sessionId);
        authorizationManager.checkCanCreateUpdateDeleteVariableSets(study.getUid(), userId);

        unique = ParamUtils.defaultObject(unique, true);
        confidential = ParamUtils.defaultObject(confidential, false);
        description = ParamUtils.defaultString(description, "");
        attributes = ParamUtils.defaultObject(attributes, new HashMap<>());

        for (Variable variable : variables) {
            ParamUtils.checkParameter(variable.getId(), "variable ID");
            ParamUtils.checkObj(variable.getType(), "variable Type");
            variable.setAllowedValues(ParamUtils.defaultObject(variable.getAllowedValues(), Collections.emptyList()));
            variable.setAttributes(ParamUtils.defaultObject(variable.getAttributes(), Collections.emptyMap()));
            variable.setCategory(ParamUtils.defaultString(variable.getCategory(), ""));
            variable.setDependsOn(ParamUtils.defaultString(variable.getDependsOn(), ""));
            variable.setDescription(ParamUtils.defaultString(variable.getDescription(), ""));
            variable.setName(ParamUtils.defaultString(variable.getName(), variable.getId()));
//            variable.setRank(defaultString(variable.getDescription(), ""));
        }

        Set<Variable> variablesSet = new HashSet<>(variables);
        if (variablesSet.size() < variables.size()) {
            throw new CatalogException("Error. Repeated variables");
        }

        VariableSet variableSet = new VariableSet(id, name, unique, confidential, description, variablesSet,
                getCurrentRelease(study, userId), attributes);
        CatalogAnnotationsValidator.checkVariableSet(variableSet);

        QueryResult<VariableSet> queryResult = studyDBAdaptor.createVariableSet(study.getUid(), variableSet);
        auditManager.recordCreation(AuditRecord.Resource.variableSet, queryResult.first().getUid(), userId, queryResult.first(), null,
                null);

        return queryResult;
    }

    public QueryResult<VariableSet> createVariableSet(String studyId, String id, String name, Boolean unique, Boolean confidential,
                                                      String description, Map<String, Object> attributes, List<Variable> variables,
                                                      String sessionId) throws CatalogException {
        String userId = catalogManager.getUserManager().getUserId(sessionId);
        Study study = resolveId(studyId, userId);
        return createVariableSet(study, id, name, unique, confidential, description, attributes, variables, sessionId);
    }

    public QueryResult<VariableSet> getVariableSet(String studyStr, String variableSet, QueryOptions options, String sessionId)
            throws CatalogException {
        options = ParamUtils.defaultObject(options, QueryOptions::new);
        MyResourceId resourceId = getVariableSetId(variableSet, studyStr, sessionId);
        return studyDBAdaptor.getVariableSet(resourceId.getResourceId(), options, resourceId.getUser());
    }

    public QueryResult<VariableSet> searchVariableSets(String studyStr, Query query, QueryOptions options, String sessionId)
            throws CatalogException {
        String userId = catalogManager.getUserManager().getUserId(sessionId);
        Study study = resolveId(studyStr, userId);
//        authorizationManager.checkStudyPermission(studyId, userId, StudyAclEntry.StudyPermissions.VIEW_VARIABLE_SET);
        options = ParamUtils.defaultObject(options, QueryOptions::new);
        query = ParamUtils.defaultObject(query, Query::new);
        if (query.containsKey(StudyDBAdaptor.VariableSetParams.UID.key())) {
            // Id could be either the id or the name
            MyResourceId resource = getVariableSetId(query.getString(StudyDBAdaptor.VariableSetParams.UID.key()), studyStr, sessionId);
            query.put(StudyDBAdaptor.VariableSetParams.UID.key(), resource.getResourceId());
        }
        query.put(StudyDBAdaptor.VariableSetParams.STUDY_ID.key(), study.getUid());
        return studyDBAdaptor.getVariableSets(query, options, userId);
    }

    public QueryResult<VariableSet> deleteVariableSet(String studyStr, String variableSetStr, String sessionId) throws CatalogException {
        MyResourceId resource = getVariableSetId(variableSetStr, studyStr, sessionId);
        String userId = resource.getUser();

        authorizationManager.checkCanCreateUpdateDeleteVariableSets(resource.getStudyId(), userId);
        QueryResult<VariableSet> queryResult = studyDBAdaptor.deleteVariableSet(resource.getResourceId(), QueryOptions.empty(), userId);
        auditManager.recordDeletion(AuditRecord.Resource.variableSet, resource.getResourceId(), userId, queryResult.first(), null, null);
        return queryResult;
    }

    public QueryResult<VariableSet> addFieldToVariableSet(String studyStr, String variableSetStr, Variable variable, String sessionId)
            throws CatalogException {
        MyResourceId resource = getVariableSetId(variableSetStr, studyStr, sessionId);
        String userId = resource.getUser();

        authorizationManager.checkCanCreateUpdateDeleteVariableSets(resource.getStudyId(), userId);
        QueryResult<VariableSet> queryResult = studyDBAdaptor.addFieldToVariableSet(resource.getResourceId(), variable, userId);
        auditManager.recordDeletion(AuditRecord.Resource.variableSet, resource.getResourceId(), userId, queryResult.first(), null, null);
        return queryResult;
    }

    public QueryResult<VariableSet> removeFieldFromVariableSet(String studyStr, String variableSetStr, String name, String sessionId)
            throws CatalogException {
        MyResourceId resource = getVariableSetId(variableSetStr, studyStr, sessionId);
        String userId = resource.getUser();

        authorizationManager.checkCanCreateUpdateDeleteVariableSets(resource.getStudyId(), userId);
        QueryResult<VariableSet> queryResult = studyDBAdaptor.removeFieldFromVariableSet(resource.getResourceId(), name, userId);
        auditManager.recordDeletion(AuditRecord.Resource.variableSet, resource.getResourceId(), userId, queryResult.first(), null, null);
        return queryResult;
    }

    public QueryResult<VariableSet> renameFieldFromVariableSet(String studyStr, String variableSetStr, String oldName, String newName,
                                                               String sessionId) throws CatalogException {
        throw new UnsupportedOperationException("Operation not yet supported");

//        MyResourceId resource = getVariableSetId(variableSetStr, studyStr, sessionId);
//        String userId = resource.getUser();
//
//        authorizationManager.checkCanCreateUpdateDeleteVariableSets(resource.getStudyId(), userId);
//        QueryResult<VariableSet> queryResult = studyDBAdaptor.renameFieldVariableSet(resource.getResourceId(), oldName, newName, userId);
//        auditManager.recordDeletion(AuditRecord.Resource.variableSet, resource.getResourceId(), userId, queryResult.first(), null, null);
//        return queryResult;
    }


    // **************************   ACLs  ******************************** //
    public List<QueryResult<StudyAclEntry>> getAcls(List<String> studyStrList, String member, boolean silent, String sessionId)
            throws CatalogException {
        String userId = catalogManager.getUserManager().getUserId(sessionId);
        List<Study> studyList = resolveIds(studyStrList, userId);
        List<QueryResult<StudyAclEntry>> studyAclList = new ArrayList<>(studyList.size());

        for (int i = 0; i < studyList.size(); i++) {
            long studyId = studyList.get(i).getUid();
            try {
                QueryResult<StudyAclEntry> allStudyAcls;
                if (StringUtils.isNotEmpty(member)) {
                    allStudyAcls = authorizationManager.getStudyAcl(userId, studyId, member);
                } else {
                    allStudyAcls = authorizationManager.getAllStudyAcls(userId, studyId);
                }
                allStudyAcls.setId(studyList.get(i).getFqn());
                studyAclList.add(allStudyAcls);
            } catch (CatalogException e) {
                if (silent) {
                    studyAclList.add(new QueryResult<>(studyList.get(i).getFqn(), 0, 0, 0, "", e.toString(), new ArrayList<>(0)));
                } else {
                    throw e;
                }
            }
        }
        return studyAclList;
    }

    public List<QueryResult<StudyAclEntry>> updateAcl(List<String> studyList, String memberIds, Study.StudyAclParams aclParams,
                                                      String sessionId) throws CatalogException {
        if (studyList == null || studyList.isEmpty()) {
            throw new CatalogException("Missing study parameter");
        }

        if (aclParams.getAction() == null) {
            throw new CatalogException("Invalid action found. Please choose a valid action to be performed.");
        }

        List<String> permissions = Collections.emptyList();
        if (StringUtils.isNotEmpty(aclParams.getPermissions())) {
            permissions = Arrays.asList(aclParams.getPermissions().trim().replaceAll("\\s", "").split(","));
            checkPermissions(permissions, StudyAclEntry.StudyPermissions::valueOf);
        }

        if (StringUtils.isNotEmpty(aclParams.getTemplate())) {
            EnumSet<StudyAclEntry.StudyPermissions> studyPermissions;
            switch (aclParams.getTemplate()) {
                case AuthorizationManager.ROLE_ADMIN:
                    studyPermissions = AuthorizationManager.getAdminAcls();
                    break;
                case AuthorizationManager.ROLE_ANALYST:
                    studyPermissions = AuthorizationManager.getAnalystAcls();
                    break;
                case AuthorizationManager.ROLE_VIEW_ONLY:
                    studyPermissions = AuthorizationManager.getViewOnlyAcls();
                    break;
                default:
                    studyPermissions = null;
                    break;
            }

            if (studyPermissions != null) {
                // Merge permissions from the template with the ones written
                Set<String> uniquePermissions = new HashSet<>(permissions);

                for (StudyAclEntry.StudyPermissions studyPermission : studyPermissions) {
                    uniquePermissions.add(studyPermission.toString());
                }

                permissions = new ArrayList<>(uniquePermissions);
            }
        }

        String userId = catalogManager.getUserManager().getUserId(sessionId);
        List<Study> studies = resolveIds(studyList, userId);

        // Check the user has the permissions needed to change permissions
        for (Study study : studies) {
            authorizationManager.checkCanAssignOrSeePermissions(study.getUid(), userId);
        }

        // Validate that the members are actually valid members
        List<String> members;
        if (memberIds != null && !memberIds.isEmpty()) {
            members = Arrays.asList(memberIds.split(","));
        } else {
            members = Collections.emptyList();
        }
        authorizationManager.checkNotAssigningPermissionsToAdminsGroup(members);
        for (Study study : studies) {
            checkMembers(study.getUid(), members);
        }

        switch (aclParams.getAction()) {
            case SET:
                return authorizationManager.setStudyAcls(studies.
                                stream()
                                .map(Study::getUid)
                                .collect(Collectors.toList()),
                        members, permissions);
            case ADD:
                return authorizationManager.addStudyAcls(studies
                                .stream()
                                .map(Study::getUid)
                                .collect(Collectors.toList()),
                        members, permissions);
            case REMOVE:
                return authorizationManager.removeStudyAcls(studies
                                .stream()
                                .map(Study::getUid)
                                .collect(Collectors.toList()),
                        members, permissions);
            case RESET:
                List<QueryResult<StudyAclEntry>> aclResult = new ArrayList<>(studies.size());
                for (Study study : studies) {
                    authorizationManager.resetPermissionsFromAllEntities(study.getUid(), members);
                    aclResult.add(authorizationManager.getAllStudyAcls(userId, study.getUid()));
                }
                return aclResult;
            default:
                throw new CatalogException("Unexpected error occurred. No valid action found.");
        }
    }


    // **************************   Private methods  ******************************** //
    private int getProjectCurrentRelease(long projectId) throws CatalogException {
        QueryOptions options = new QueryOptions(QueryOptions.INCLUDE, ProjectDBAdaptor.QueryParams.CURRENT_RELEASE.key());
        QueryResult<Project> projectQueryResult = projectDBAdaptor.get(projectId, options);
        if (projectQueryResult.getNumResults() == 0) {
            throw new CatalogException("Internal error. Cannot retrieve current release from project");
        }
        return projectQueryResult.first().getCurrentRelease();
    }

    private QueryResult rename(long studyId, String newStudyAlias, String sessionId) throws CatalogException {
        ParamUtils.checkAlias(newStudyAlias, "newStudyAlias");
        String userId = catalogManager.getUserManager().getUserId(sessionId);
//        String studyOwnerId = studyDBAdaptor.getStudyOwnerId(studyId);

        //User can't write/modify the study
        authorizationManager.checkCanEditStudy(studyId, userId);

        // Both users must bu updated
        userDBAdaptor.updateUserLastModified(userId);
//        userDBAdaptor.updateUserLastModified(studyOwnerId);
        //TODO get all shared users to updateUserLastModified

        //QueryResult queryResult = studyDBAdaptor.renameStudy(studyId, newStudyAlias);
        auditManager.recordUpdate(AuditRecord.Resource.study, studyId, userId, new ObjectMap("alias", newStudyAlias), null, null);
        return new QueryResult();

    }

    private boolean existsGroup(long studyId, String groupId) throws CatalogDBException {
        Query query = new Query()
                .append(StudyDBAdaptor.QueryParams.UID.key(), studyId)
                .append(StudyDBAdaptor.QueryParams.GROUP_NAME.key(), groupId);
        return studyDBAdaptor.count(query).first() > 0;
    }

    private void validatePermissionRules(long studyId, Study.Entity entry, PermissionRule permissionRule) throws CatalogException {
        ParamUtils.checkIdentifier(permissionRule.getId(), "PermissionRules");

        if (permissionRule.getPermissions() == null || permissionRule.getPermissions().isEmpty()) {
            throw new CatalogException("Missing permissions for the Permissions Rule object");
        }

        switch (entry) {
            case SAMPLES:
                validatePermissions(permissionRule.getPermissions(), SampleAclEntry.SamplePermissions::valueOf);
                break;
            case FILES:
                validatePermissions(permissionRule.getPermissions(), FileAclEntry.FilePermissions::valueOf);
                break;
            case COHORTS:
                validatePermissions(permissionRule.getPermissions(), CohortAclEntry.CohortPermissions::valueOf);
                break;
            case INDIVIDUALS:
                validatePermissions(permissionRule.getPermissions(), IndividualAclEntry.IndividualPermissions::valueOf);
                break;
            case FAMILIES:
                validatePermissions(permissionRule.getPermissions(), FamilyAclEntry.FamilyPermissions::valueOf);
                break;
            case JOBS:
                validatePermissions(permissionRule.getPermissions(), JobAclEntry.JobPermissions::valueOf);
                break;
            case CLINICAL_ANALYSES:
                validatePermissions(permissionRule.getPermissions(), ClinicalAnalysisAclEntry.ClinicalAnalysisPermissions::valueOf);
                break;
            default:
                throw new CatalogException("Unexpected entry found");
        }

        checkMembers(studyId, permissionRule.getMembers());
    }

    private void validatePermissions(List<String> permissions, Function<String, Object> valueOf) throws CatalogException {
        for (String permission : permissions) {
            try {
                valueOf.apply(permission);
            } catch (IllegalArgumentException e) {
                logger.error("Detected unsupported " + permission + " permission: {}", e.getMessage(), e);
                throw new CatalogException("Detected unsupported " + permission + " permission.");
            }
        }
    }

    private String getOwner(Study study) throws CatalogDBException {
        if (!StringUtils.isEmpty(study.getFqn())) {
            return StringUtils.split(study.getFqn(), "@")[0];
        }
        return studyDBAdaptor.getOwnerId(study.getUid());
    }

    public boolean createStats(String studyId) throws CatalogDBException {
        StudyStats studyStats = new StudyStats();
        FileStats fileStats = catalogManager.getFileManager().createStats(studyId);
        //Chort
        // ....
        //combine
        return true;
    }

    public StudyStats getStats(String studyId) {

        return new StudyStats();
    }
}
