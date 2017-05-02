/*
 * Copyright 2015-2016 OpenCB
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

package org.opencb.opencga.app.cli.main.executors.catalog;


import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResponse;
import org.opencb.opencga.app.cli.main.executors.OpencgaCommandExecutor;
import org.opencb.opencga.app.cli.main.executors.catalog.commons.AclCommandExecutor;
import org.opencb.opencga.app.cli.main.executors.catalog.commons.AnnotationCommandExecutor;
import org.opencb.opencga.app.cli.main.options.CohortCommandOptions;
import org.opencb.opencga.app.cli.main.options.commons.AclCommandOptions;
import org.opencb.opencga.catalog.db.api.CohortDBAdaptor;
import org.opencb.opencga.catalog.db.api.JobDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.models.Cohort;
import org.opencb.opencga.catalog.models.Sample;
import org.opencb.opencga.catalog.models.acls.permissions.CohortAclEntry;

import java.io.IOException;

/**
 * Created by imedina on 03/06/16.
 */
public class CohortCommandExecutor extends OpencgaCommandExecutor {

    private CohortCommandOptions cohortsCommandOptions;
    private AclCommandExecutor<Cohort, CohortAclEntry> aclCommandExecutor;
    private AnnotationCommandExecutor<Cohort, CohortAclEntry> annotationCommandExecutor;

    public CohortCommandExecutor(CohortCommandOptions cohortsCommandOptions) {
        super(cohortsCommandOptions.commonCommandOptions);
        this.cohortsCommandOptions = cohortsCommandOptions;
        this.aclCommandExecutor = new AclCommandExecutor<>();
        this.annotationCommandExecutor = new AnnotationCommandExecutor<>();
    }


    @Override
    public void execute() throws Exception {
        logger.debug("Executing cohorts command line");

        String subCommandString = getParsedSubCommand(cohortsCommandOptions.jCommander);
        QueryResponse queryResponse = null;
        switch (subCommandString) {
            case "create":
                queryResponse = create();
                break;
            case "info":
                queryResponse = info();
                break;
            case "samples":
                queryResponse = samples();
                break;
            case "update":
                queryResponse = update();
                break;
            case "delete":
                queryResponse = delete();
                break;
            case "stats":
                queryResponse = stats();
                break;
            case "search":
                queryResponse = search();
                break;
            case "group-by":
                queryResponse = groupBy();
                break;
            case "acl":
                queryResponse = aclCommandExecutor.acls(cohortsCommandOptions.aclsCommandOptions, openCGAClient.getCohortClient());
                break;
            case "acl-update":
                queryResponse = updateAcl();
                break;
            case "annotation-sets-create":
                queryResponse = annotationCommandExecutor.createAnnotationSet(cohortsCommandOptions.annotationCreateCommandOptions,
                        openCGAClient.getCohortClient());
                break;
            case "annotation-sets-all-info":
                queryResponse = annotationCommandExecutor.getAllAnnotationSets(cohortsCommandOptions.annotationAllInfoCommandOptions,
                        openCGAClient.getCohortClient());
                break;
            case "annotation-sets-search":
                queryResponse = annotationCommandExecutor.searchAnnotationSets(cohortsCommandOptions.annotationSearchCommandOptions,
                        openCGAClient.getCohortClient());
                break;
            case "annotation-sets-delete":
                queryResponse = annotationCommandExecutor.deleteAnnotationSet(cohortsCommandOptions.annotationDeleteCommandOptions,
                        openCGAClient.getCohortClient());
                break;
            case "annotation-sets-info":
                queryResponse = annotationCommandExecutor.getAnnotationSet(cohortsCommandOptions.annotationInfoCommandOptions,
                        openCGAClient.getCohortClient());
                break;
            case "annotation-sets-update":
                queryResponse = annotationCommandExecutor.updateAnnotationSet(cohortsCommandOptions.annotationUpdateCommandOptions,
                        openCGAClient.getCohortClient());
                break;
            default:
                logger.error("Subcommand not valid");
                break;
        }

        createOutput(queryResponse);
    }

    private QueryResponse search() throws IOException {
        CohortCommandOptions.SearchCommandOptions commandOptions = cohortsCommandOptions.searchCommandOptions;

        logger.debug("Searching cohorts");

        Query query = new Query();
        query.putIfNotEmpty(CohortDBAdaptor.QueryParams.STUDY.key(), resolveStudy(commandOptions.study));
        query.putIfNotEmpty(CohortDBAdaptor.QueryParams.NAME.key(), commandOptions.name);
        query.putIfNotNull(CohortDBAdaptor.QueryParams.TYPE.key(), commandOptions.type);
        query.putIfNotNull(CohortDBAdaptor.QueryParams.STATUS.key(), commandOptions.status);
        query.putIfNotEmpty(CohortDBAdaptor.QueryParams.SAMPLES.key(), commandOptions.samples);

        QueryOptions queryOptions = new QueryOptions();
        queryOptions.putIfNotEmpty(QueryOptions.INCLUDE, commandOptions.dataModelOptions.include);
        queryOptions.putIfNotEmpty(QueryOptions.EXCLUDE, commandOptions.dataModelOptions.exclude);
        queryOptions.put(QueryOptions.LIMIT, commandOptions.numericOptions.limit);
        queryOptions.put(QueryOptions.SKIP, commandOptions.numericOptions.skip);
        queryOptions.put("count", commandOptions.numericOptions.count);

        return openCGAClient.getCohortClient().search(query,queryOptions);

    }


    private QueryResponse<Cohort> create() throws CatalogException, IOException {
        logger.debug("Creating a new cohort");

        ObjectMap params = new ObjectMap();
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.NAME.key(), cohortsCommandOptions.createCommandOptions.name);
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.SAMPLES.key(), cohortsCommandOptions.createCommandOptions.sampleIds);
        params.putIfNotNull(CohortDBAdaptor.QueryParams.TYPE.key(), cohortsCommandOptions.createCommandOptions.type);
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.DESCRIPTION.key(), cohortsCommandOptions.createCommandOptions.description);

        return openCGAClient.getCohortClient().create(resolveStudy(cohortsCommandOptions.createCommandOptions.study),
                cohortsCommandOptions.createCommandOptions.variableSetId, cohortsCommandOptions.createCommandOptions.variable, params);
    }

    private QueryResponse<Cohort> info() throws CatalogException, IOException {
        logger.debug("Getting cohort information");

        ObjectMap params = new ObjectMap();
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.STUDY.key(), resolveStudy(cohortsCommandOptions.infoCommandOptions.study));
        params.putIfNotEmpty(QueryOptions.INCLUDE, cohortsCommandOptions.infoCommandOptions.dataModelOptions.include);
        params.putIfNotEmpty(QueryOptions.EXCLUDE, cohortsCommandOptions.infoCommandOptions.dataModelOptions.exclude);
        return openCGAClient.getCohortClient().get(cohortsCommandOptions.infoCommandOptions.cohort, params);
    }

    private QueryResponse<Sample> samples() throws CatalogException, IOException {
        logger.debug("Listing samples belonging to a cohort");

        Query query = new Query();
        query.putIfNotEmpty(CohortDBAdaptor.QueryParams.STUDY.key(), resolveStudy(cohortsCommandOptions.samplesCommandOptions.study));

        QueryOptions queryOptions = new QueryOptions();
        queryOptions.putIfNotEmpty(QueryOptions.INCLUDE, cohortsCommandOptions.samplesCommandOptions.dataModelOptions.include);
        queryOptions.putIfNotEmpty(QueryOptions.EXCLUDE, cohortsCommandOptions.samplesCommandOptions.dataModelOptions.exclude);
        queryOptions.put(QueryOptions.LIMIT,  cohortsCommandOptions.samplesCommandOptions.numericOptions.limit);
        queryOptions.put(QueryOptions.SKIP, cohortsCommandOptions.samplesCommandOptions.numericOptions.skip);
        queryOptions.put("count", cohortsCommandOptions.samplesCommandOptions.numericOptions.count);
        return openCGAClient.getCohortClient().getSamples(cohortsCommandOptions.samplesCommandOptions.cohort, query, queryOptions);
    }

    private QueryResponse<Cohort> update() throws CatalogException, IOException {
        logger.debug("Updating cohort");

        ObjectMap params = new ObjectMap();
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.NAME.key(), cohortsCommandOptions.updateCommandOptions.name);
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.CREATION_DATE.key(), cohortsCommandOptions.updateCommandOptions.creationDate);
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.DESCRIPTION.key(), cohortsCommandOptions.updateCommandOptions.description);
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.SAMPLES.key(), cohortsCommandOptions.updateCommandOptions.samples);
        return openCGAClient.getCohortClient().update(cohortsCommandOptions.updateCommandOptions.cohort,
                resolveStudy(cohortsCommandOptions.updateCommandOptions.study), params);
    }

    private QueryResponse<Cohort> delete() throws CatalogException, IOException {
        logger.debug("Deleting cohort");

        ObjectMap params = new ObjectMap();
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.STUDY.key(), resolveStudy(cohortsCommandOptions.deleteCommandOptions.study));

        return openCGAClient.getCohortClient().delete(cohortsCommandOptions.deleteCommandOptions.cohort, params);
    }

    private QueryResponse<Object> stats() throws CatalogException, IOException {
        logger.debug("Calculating variant stats for a set of cohorts");

        Query query = new Query();
        query.putIfNotEmpty(CohortDBAdaptor.QueryParams.STUDY.key(), resolveStudy(cohortsCommandOptions.statsCommandOptions.study));

        QueryOptions queryOptions = new QueryOptions();
        queryOptions.put("calculate", cohortsCommandOptions.statsCommandOptions.calculate);
        queryOptions.put("delete", cohortsCommandOptions.statsCommandOptions.delete);
        queryOptions.putIfNotEmpty("log", cohortsCommandOptions.statsCommandOptions.log);
        queryOptions.putIfNotEmpty(JobDBAdaptor.QueryParams.OUT_DIR_ID.key(), cohortsCommandOptions.statsCommandOptions.outdirId);
        return openCGAClient.getCohortClient().getStats(cohortsCommandOptions.statsCommandOptions.cohort, query, queryOptions);
    }

    private QueryResponse<ObjectMap> groupBy() throws CatalogException, IOException {
        logger.debug("Group by cohorts");

        ObjectMap params = new ObjectMap();
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.STUDY.key(), resolveStudy(cohortsCommandOptions.groupByCommandOptions.study));
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.ID.key(), cohortsCommandOptions.groupByCommandOptions.id);
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.NAME.key(), cohortsCommandOptions.groupByCommandOptions.name);
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.TYPE.key(), cohortsCommandOptions.groupByCommandOptions.type);
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.STATUS_NAME.key(), cohortsCommandOptions.groupByCommandOptions.status);
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.CREATION_DATE.key(), cohortsCommandOptions.groupByCommandOptions.creationDate);
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.SAMPLES.key(), cohortsCommandOptions.groupByCommandOptions.sampleIds);
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.ATTRIBUTES.key(), cohortsCommandOptions.groupByCommandOptions.attributes);
        params.putIfNotEmpty(CohortDBAdaptor.QueryParams.NATTRIBUTES.key(), cohortsCommandOptions.groupByCommandOptions.nattributes);
        return openCGAClient.getCohortClient().groupBy(cohortsCommandOptions.groupByCommandOptions.study,
                cohortsCommandOptions.groupByCommandOptions.fields,params);
    }

    private QueryResponse<CohortAclEntry> updateAcl() throws IOException, CatalogException {
        AclCommandOptions.AclsUpdateCommandOptions commandOptions = cohortsCommandOptions.aclsUpdateCommandOptions;

        ObjectMap queryParams = new ObjectMap();
        queryParams.putIfNotNull("study", commandOptions.study);

        ObjectMap bodyParams = new ObjectMap();
        bodyParams.putIfNotNull("permissions", commandOptions.permissions);
        bodyParams.putIfNotNull("action", commandOptions.action);
        bodyParams.putIfNotNull("cohort", commandOptions.id);

        return openCGAClient.getCohortClient().updateAcl(commandOptions.memberId, queryParams, bodyParams);
    }


}
