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

package org.opencb.opencga.catalog.managers;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.catalog.audit.AuditManager;
import org.opencb.opencga.catalog.audit.AuditRecord;
import org.opencb.opencga.catalog.auth.authentication.AuthenticationManager;
import org.opencb.opencga.catalog.auth.authentication.CatalogAuthenticationManager;
import org.opencb.opencga.catalog.auth.authentication.LDAPAuthenticationManager;
import org.opencb.opencga.catalog.auth.authorization.AuthorizationManager;
import org.opencb.opencga.catalog.config.AuthenticationOrigin;
import org.opencb.opencga.catalog.config.Configuration;
import org.opencb.opencga.catalog.db.DBAdaptorFactory;
import org.opencb.opencga.catalog.db.api.UserDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.exceptions.CatalogIOException;
import org.opencb.opencga.catalog.io.CatalogIOManagerFactory;
import org.opencb.opencga.catalog.managers.api.IUserManager;
import org.opencb.opencga.catalog.models.*;
import org.opencb.opencga.catalog.utils.LDAPUtils;
import org.opencb.opencga.catalog.utils.ParamUtils;
import org.opencb.opencga.core.results.LdapImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class UserManager extends AbstractManager implements IUserManager {

    private String INTERNAL_AUTHORIZATION = "internal";
    private Map<String, AuthenticationManager> authenticationManagerMap;

    protected static final String EMAIL_PATTERN = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
            + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
    //    private final SessionManager sessionManager;
    protected static final Pattern EMAILPATTERN = Pattern.compile(EMAIL_PATTERN);
    protected static Logger logger = LoggerFactory.getLogger(UserManager.class);
//    protected final Policies.UserCreation creationUserPolicy;

    public UserManager(AuthorizationManager authorizationManager, AuditManager auditManager, CatalogManager catalogManager,
                       DBAdaptorFactory catalogDBAdaptorFactory, CatalogIOManagerFactory ioManagerFactory,
                       Configuration configuration) {
        super(authorizationManager, auditManager, catalogManager, catalogDBAdaptorFactory, ioManagerFactory, configuration);

        authenticationManagerMap = new HashMap<>();
        if (configuration.getAuthenticationOrigins() != null) {
            for (AuthenticationOrigin authenticationOrigin : configuration.getAuthenticationOrigins()) {
                if (authenticationOrigin.getId() != null) {
                    switch (authenticationOrigin.getType()) {
                        case LDAP:
                            authenticationManagerMap.put(authenticationOrigin.getId(),
                                    new LDAPAuthenticationManager(authenticationOrigin.getHost()));
                            break;
//                    case OPENCGA:
                        default:
//                        authenticationManagerMap.put(authenticationOrigin.getId(),
//                                new CatalogAuthenticationManager(catalogDBAdaptorFactory, catalogConfiguration));
//                        INTERNAL_AUTHORIZATION = authenticationOrigin.getId();
                            break;
                    }
                }
            }
        }
        // Even if internal authentication is not present in the configuration file, create it
        authenticationManagerMap.putIfAbsent(INTERNAL_AUTHORIZATION,
                new CatalogAuthenticationManager(catalogDBAdaptorFactory, configuration));
        AuthenticationOrigin authenticationOrigin = new AuthenticationOrigin();
        if (configuration.getAuthenticationOrigins() == null) {
            configuration.setAuthenticationOrigins(Arrays.asList(authenticationOrigin));
        } else {
            // Check if OPENCGA authentication is already present in catalog configuration
            boolean catalogPresent = false;
            for (AuthenticationOrigin origin : configuration.getAuthenticationOrigins()) {
                if (AuthenticationOrigin.AuthenticationType.OPENCGA == origin.getType()) {
                    catalogPresent = true;
                    break;
                }
            }
            if (!catalogPresent) {
                List<AuthenticationOrigin> linkedList = new LinkedList<>();
                linkedList.addAll(configuration.getAuthenticationOrigins());
                linkedList.add(authenticationOrigin);
                configuration.setAuthenticationOrigins(linkedList);
            }
        }
    }

    static void checkEmail(String email) throws CatalogException {
        if (email == null || !EMAILPATTERN.matcher(email).matches()) {
            throw new CatalogException("email not valid");
        }
    }

    @Override
    public String getId(String sessionId) throws CatalogException {
        return authenticationManagerMap.get(INTERNAL_AUTHORIZATION).getUserId(sessionId);
//        if (sessionId == null || sessionId.isEmpty() || sessionId.equalsIgnoreCase("anonymous")) {
//            return "anonymous";
//        }
//        return userDBAdaptor.getUserIdBySessionId(sessionId);
    }

    @Override
    public void changePassword(String userId, String oldPassword, String newPassword) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
//        checkParameter(sessionId, "sessionId");
        ParamUtils.checkParameter(oldPassword, "oldPassword");
        ParamUtils.checkParameter(newPassword, "newPassword");
        if (oldPassword == newPassword) {
            throw new CatalogException("New password is the same as the old password.");
        }

        userDBAdaptor.checkId(userId);
        String authOrigin = getAuthenticationOriginId(userId);
        authenticationManagerMap.get(authOrigin).changePassword(userId, oldPassword, newPassword);
        userDBAdaptor.updateUserLastModified(userId);
    }

    private String getAuthenticationOriginId(String userId) throws CatalogException {
        QueryResult<User> user = userDBAdaptor.get(userId, new QueryOptions(), "");
        if (user == null || user.getNumResults() == 0) {
            throw new CatalogException(userId + " user not found");
        }
        return user.first().getAccount().getAuthOrigin();
    }

    private AuthenticationOrigin getAuthenticationOrigin(String authOrigin) {
        if (configuration.getAuthenticationOrigins() != null) {
            for (AuthenticationOrigin authenticationOrigin : configuration.getAuthenticationOrigins()) {
                if (authOrigin.equals(authenticationOrigin.getId())) {
                    return authenticationOrigin;
                }
            }
        }
        return null;
    }

    @Override
    public QueryResult<User> create(String id, String name, String email, String password, String organization, Long quota, String
            accountType, QueryOptions options) throws CatalogException {

        // Check if the users can be registered publicly or just the admin.
        if (!catalogDBAdaptorFactory.getCatalogMetaDBAdaptor().isRegisterOpen()) {
            String adminPassword = configuration.getAdmin().getPassword();
            if (adminPassword != null && !adminPassword.isEmpty()) {
                authenticationManagerMap.get(INTERNAL_AUTHORIZATION).authenticate("admin", adminPassword, true);
            } else {
                throw new CatalogException("The registration is closed to the public: Please talk to your administrator.");
            }
        }

        ParamUtils.checkParameter(id, "id");
        ParamUtils.checkParameter(password, "password");
        ParamUtils.checkParameter(name, "name");
        checkEmail(email);
        organization = organization != null ? organization : "";
        checkUserExists(id);

        User user = new User(id, name, email, "", organization, User.UserStatus.READY);
        user.getAccount().setAuthOrigin(INTERNAL_AUTHORIZATION);
        // Check account type
        if (accountType != null) {
            if (!Account.FULL.equalsIgnoreCase(accountType) && !Account.GUEST.equalsIgnoreCase(accountType)) {
                throw new CatalogException("The account type specified does not correspond with any of the valid ones. Valid account types:"
                        + Account.FULL + " and " + Account.GUEST);
            }
            user.getAccount().setType(accountType);
        }

        if (quota != null && quota > 0L) {
            user.setQuota(quota);
        }

        String userId = id;
        try {
            catalogIOManagerFactory.getDefault().createUser(user.getId());
            QueryResult<User> queryResult = userDBAdaptor.insert(user, options);
//            auditManager.recordCreation(AuditRecord.Resource.user, user.getId(), userId, queryResult.first(), null, null);
            auditManager.recordAction(AuditRecord.Resource.user, AuditRecord.Action.create, AuditRecord.Magnitude.low, user.getId(), userId,
                    null, queryResult.first(), null, null);
            authenticationManagerMap.get(INTERNAL_AUTHORIZATION).newPassword(user.getId(), password);
            return queryResult;
        } catch (CatalogIOException | CatalogDBException e) {
            if (!userDBAdaptor.exists(user.getId())) {
                logger.error("ERROR! DELETING USER! " + user.getId());
                catalogIOManagerFactory.getDefault().deleteUser(user.getId());
            }
            throw e;
        }
    }

    @Override
    public LdapImportResult importFromExternalAuthOrigin(String authOrigin, String accountType, ObjectMap params, String adminPassword)
            throws CatalogException {
        LdapImportResult retResult = new LdapImportResult();
        LdapImportResult.Input input = new LdapImportResult.Input(params.getAsStringList("users"), params.getString("group"),
                params.getString("study-group"), authOrigin, accountType, params.getString("study"));
        retResult.setInput(input);

        // Validate the admin password.
        QueryResult<Session> admin;
        try {
            admin = login("admin", adminPassword, "localhost");
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            retResult.setErrorMsg(e.getMessage());
            return retResult;
        }
        String sessionId = admin.first().getId();

        if (INTERNAL_AUTHORIZATION.equals(authOrigin)) {
            retResult.setErrorMsg("Cannot import users from catalog. Authentication origin should be external.");
            return retResult;
        }

        // Obtain the authentication origin parameters
        AuthenticationOrigin authenticationOrigin = getAuthenticationOrigin(authOrigin);
        if (authenticationOrigin == null) {
            retResult.setErrorMsg("The authentication origin id " + authOrigin + " does not correspond with any id in our database.");
            return retResult;
        }

        // Check account type
        if (accountType != null) {
            if (!Account.FULL.equalsIgnoreCase(accountType) && !Account.GUEST.equalsIgnoreCase(accountType)) {
                retResult.setErrorMsg("The account type specified does not correspond with any of the valid ones. Valid account types:"
                        + Account.FULL + " and " + Account.GUEST);
                return retResult;
            }
        }

        DirContext dirContext;
        try {
            dirContext = LDAPUtils.getDirContext(authenticationOrigin.getHost());
        } catch (NamingException e) {
            logger.error(e.getMessage(), e);
            retResult.setErrorMsg(e.getMessage());
            return retResult;
        }

        String base = ((String) authenticationOrigin.getOptions().get(AuthenticationOrigin.GROUPS_SEARCH));
        Set<String> usersFromLDAP = new HashSet<>();
        usersFromLDAP.addAll(retResult.getInput().getUsers());
        try {
            usersFromLDAP.addAll(LDAPUtils.getUsersFromLDAPGroup(dirContext, retResult.getInput().getGroup(), base));
        } catch (NamingException e) {
            logger.error(e.getMessage(), e);
            retResult.setErrorMsg(e.getMessage());
            return retResult;
        }
        if (usersFromLDAP.size() == 0) {
            retResult.setWarningMsg("No users were found. Nothing to do.");
            return retResult;
        }

        base = ((String) authenticationOrigin.getOptions().get(AuthenticationOrigin.USERS_SEARCH));
        List<Attributes> userAttrList;
        try {
            List<String> userList = new ArrayList<>(usersFromLDAP.size());
            userList.addAll(usersFromLDAP);
            userAttrList = LDAPUtils.getUserInfoFromLDAP(dirContext, userList, base);
        } catch (NamingException e) {
            logger.error(e.getMessage(), e);
            retResult.setErrorMsg(e.getMessage());
            return retResult;
        }

        if (userAttrList.size() == 0) {
            retResult.setWarningMsg("No users were found. Nothing to do.");
            return retResult;
        }

        String type;
        if (Account.GUEST.equalsIgnoreCase(accountType)) {
            type = Account.GUEST;
        } else {
            type = Account.FULL;
        }

        Set<String> userList = new HashSet<>(userAttrList.size());
        LdapImportResult.SummaryResult summaryResult = new LdapImportResult.SummaryResult();
        summaryResult.setTotal(usersFromLDAP.size());

        // Register users in catalog
        for (Attributes attrs : userAttrList) {
            String displayname;
            String mail;
            String uid;
            String rdn;
            try {
                displayname = (String) attrs.get("displayname").get(0);
                mail = (String) attrs.get("mail").get(0);
                uid = (String) attrs.get("uid").get(0);
                rdn = (String) attrs.get("gecos").get(0);
            } catch (NamingException e) {
                logger.error(e.getMessage(), e);
                retResult.setErrorMsg(e.getMessage());
                return retResult;
            }

            // Check if the user already exists in catalog
            if (userDBAdaptor.exists(uid)) {
                summaryResult.getExistingUsers().add(uid);
                userList.add(uid);
                continue;
            }

            logger.debug("Registering {} in Catalog", uid);

            // Create the user in catalog
            Account account = new Account().setType(type).setAuthOrigin(authOrigin);

            // TODO: Parse expiration date
//            if (params.get("expirationDate") != null) {
//                account.setExpirationDate(...);
//            }

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("LDAP_RDN", rdn);
            User user = new User(uid, displayname, mail, "", base, account, User.UserStatus.READY, "", -1, -1, new ArrayList<>(),
                    new ArrayList<>(), new ArrayList<>(), new HashMap<>(), attributes);

            userDBAdaptor.insert(user, QueryOptions.empty());

            summaryResult.getNewUsers().add(uid);
            userList.add(uid);
        }

        // Check users not found in LDAP
        for (String uid : retResult.getInput().getUsers()) {
            if (!userList.contains(uid)) {
                summaryResult.getNonExistingUsers().add(uid);
            }
        }

        LdapImportResult.Result result = new LdapImportResult.Result();
        retResult.setResult(result);
        result.setUserSummary(summaryResult);

        if (StringUtils.isEmpty(retResult.getInput().getStudy()) || StringUtils.isEmpty(retResult.getInput().getStudyGroup())) {
            return retResult;
        }
        long studyId = catalogManager.getStudyManager().getId("admin", retResult.getInput().getStudy());

        if (studyId <= 0) {
            retResult.setErrorMsg("Study not " + retResult.getInput().getStudy() + " found.");
            return retResult;
        }

        try {
            catalogManager.getStudyManager().createGroup(Long.toString(studyId), retResult.getInput().getStudyGroup(),
                    StringUtils.join(userList, ","), sessionId);
        } catch (CatalogException e) {
            if (e.getMessage().contains("users already belong to")) {
                // Cannot create a group with those users because they already belong to other group
                retResult.setErrorMsg(e.getMessage());
                return retResult;
            }
            try {
                catalogManager.getStudyManager().updateGroup(Long.toString(studyId), retResult.getInput().getStudyGroup(),
                        StringUtils.join(userList, ","), null, null, sessionId);
            } catch (CatalogException e1) {
                retResult.setErrorMsg(e1.getMessage());
                return retResult;
            }
        }

        try {
            QueryResult<Group> group = catalogManager.getStudyManager().getGroup(Long.toString(studyId),
                    retResult.getInput().getStudyGroup(), sessionId);

            retResult.getResult().setUsersInGroup(group.first().getUserIds());
        } catch (CatalogException e) {
//            logger.error(e.getMessage(), e);
            retResult.setErrorMsg(e.getMessage());
        }
        return retResult;
    }

    private void checkGroupsFromExternalUser(String userId, List<String> groupIds, List<String> studyIds) throws CatalogException {
        for (String studyStr : studyIds) {
            long studyId = catalogManager.getStudyManager().getId(userId, studyStr);
            if (studyId < 1) {
                throw new CatalogException("Study " + studyStr + " not found.");
            }

            // TODO: What do we do with admin session id when logging in? We will need to update the groups.
            //catalogManager.getSessionManager().createToken("admin", "private")
//            for (String groupId : groupIds) {
//                try {
//                    catalogManager.getStudyManager().updateGroup(Long.toString(studyId), groupId, userId, null, null, )
//                } catch (CatalogDBException e) {
//                    // The user already belonged to the group.
//                } catch (CatalogException e) {
//                    // The group does not exist.
//                }
//            }

        }
    }

    @Override
    public QueryResult<User> get(String userId, QueryOptions options, String sessionId) throws CatalogException {
        return get(userId, null, options, sessionId);
    }

    @Override
    public QueryResult<User> get(String userId, String lastModified, QueryOptions options, String sessionId)
            throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(sessionId, "sessionId");
        checkSessionId(userId, sessionId);
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        if (!options.containsKey(QueryOptions.INCLUDE) || options.get(QueryOptions.INCLUDE) == null) {
            List<String> excludeList;
            if (options.containsKey(QueryOptions.EXCLUDE)) {
                List<String> asStringList = options.getAsStringList(QueryOptions.EXCLUDE, ",");
                excludeList = new ArrayList<>(asStringList.size() + 3);
                excludeList.addAll(asStringList);
            } else {
                excludeList = new ArrayList<>(3);
            }
            excludeList.add(UserDBAdaptor.QueryParams.SESSIONS.key());
            excludeList.add(UserDBAdaptor.QueryParams.PASSWORD.key());
            if (!excludeList.contains(UserDBAdaptor.QueryParams.PROJECTS.key())) {
                excludeList.add("projects.studies.variableSets");
            }
            options.put(QueryOptions.EXCLUDE, excludeList);
        }

        QueryResult<User> user = userDBAdaptor.get(userId, options, lastModified);
        return user;
    }

    @Override
    public QueryResult<User> get(Query query, QueryOptions options, String sessionId) throws CatalogException {
        throw new UnsupportedOperationException();
    }

    /**
     * Modify some params from the user profile.
     * name
     * email
     * organization
     * attributes
     *
     * @throws CatalogException
     */
    @Override
    public QueryResult<User> update(String userId, ObjectMap parameters, QueryOptions options, String sessionId)
            throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkObj(parameters, "parameters");

        if (sessionId != null && !sessionId.isEmpty()) {
            ParamUtils.checkParameter(sessionId, "sessionId");
            checkSessionId(userId, sessionId);
            for (String s : parameters.keySet()) {
                if (!s.matches("name|email|organization|attributes")) {
                    throw new CatalogDBException("Parameter '" + s + "' can't be changed");
                }
            }
        } else {
            if (configuration.getAdmin().getPassword() == null || configuration.getAdmin().getPassword().isEmpty()) {
                throw new CatalogException("Nor the administrator password nor the session id could be found. The user could not be "
                        + "updated.");
            }
            authenticationManagerMap.get(INTERNAL_AUTHORIZATION).authenticate("admin", configuration.getAdmin().getPassword(), true);
        }

        if (parameters.containsKey("email")) {
            checkEmail(parameters.getString("email"));
        }
        userDBAdaptor.updateUserLastModified(userId);
        QueryResult<User> queryResult = userDBAdaptor.update(userId, parameters);
        auditManager.recordUpdate(AuditRecord.Resource.user, userId, userId, parameters, null, null);
        return queryResult;
    }

    @Override
    public List<QueryResult<User>> delete(String userIdList, QueryOptions options, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(userIdList, "userIdList");

        List<String> userIds = Arrays.asList(userIdList.split(","));
        List<QueryResult<User>> deletedUsers = new ArrayList<>(userIds.size());
        for (String userId : userIds) {
            if (sessionId != null && !sessionId.isEmpty()) {
                ParamUtils.checkParameter(sessionId, "sessionId");
                checkSessionId(userId, sessionId);
            } else {
                if (configuration.getAdmin().getPassword() == null || configuration.getAdmin().getPassword().isEmpty()) {
                    throw new CatalogException("Nor the administrator password nor the session id could be found. The user could not be "
                            + "deleted.");
                }
                authenticationManagerMap.get(INTERNAL_AUTHORIZATION)
                        .authenticate("admin", configuration.getAdmin().getPassword(), true);
            }

            QueryResult<User> deletedUser = userDBAdaptor.delete(userId, options);
            auditManager.recordDeletion(AuditRecord.Resource.user, userId, userId, deletedUser.first(), null, null);
            deletedUsers.add(deletedUser);
        }
        return deletedUsers;
    }

    @Override
    public List<QueryResult<User>> delete(Query query, QueryOptions options, String sessionId) throws CatalogException, IOException {
        QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, UserDBAdaptor.QueryParams.ID.key());
        QueryResult<User> userQueryResult = userDBAdaptor.get(query, queryOptions);
        List<String> userIds = userQueryResult.getResult().stream().map(User::getId).collect(Collectors.toList());
        String userIdStr = StringUtils.join(userIds, ",");
        return delete(userIdStr, options, sessionId);
    }

    @Override
    public List<QueryResult<User>> restore(String ids, QueryOptions options, String sessionId) throws CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<QueryResult<User>> restore(Query query, QueryOptions options, String sessionId) throws CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public QueryResult rank(Query query, String field, int numResults, boolean asc, String sessionId) throws CatalogException {
        throw new UnsupportedOperationException("User: Operation not supported.");
    }

    @Override
    public QueryResult groupBy(Query query, String field, QueryOptions options, String sessionId) throws CatalogException {
        throw new UnsupportedOperationException("User: Operation not supported.");
    }

    @Override
    public QueryResult groupBy(Query query, List<String> fields, QueryOptions options, String sessionId) throws CatalogException {
        throw new UnsupportedOperationException("User: Operation not supported.");
    }

    @Override
    public void setStatus(String id, String status, String message, String sessionId) throws CatalogException {
        throw new NotImplementedException("User: Operation not yet supported");
    }

    @Override
    public QueryResult resetPassword(String userId, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(sessionId, "sessionId");
        checkSessionId(userId, sessionId);

        String authOrigin = getAuthenticationOriginId(userId);
        return authenticationManagerMap.get(authOrigin).resetPassword(userId);
    }

    @Override
    public void validatePassword(String userId, String password, boolean throwException) throws CatalogException {
        if (userId.equalsIgnoreCase("admin")) {
            authenticationManagerMap.get(INTERNAL_AUTHORIZATION).authenticate("admin", password, throwException);
        } else {
            String authOrigin = getAuthenticationOriginId(userId);
            authenticationManagerMap.get(authOrigin).authenticate(userId, password, throwException);
        }
    }

    @Deprecated
    @Override
    public QueryResult<ObjectMap> loginAsAnonymous(String sessionIp)
            throws CatalogException, IOException {
        ParamUtils.checkParameter(sessionIp, "sessionIp");
        Session session = new Session(sessionIp, 20);

        String userId = "anonymous_" + session.getId();

        // TODO sessionID should be created here

        catalogIOManagerFactory.getDefault().createAnonymousUser(userId);

        try {
            return userDBAdaptor.loginAsAnonymous(session);
        } catch (CatalogDBException e) {
            catalogIOManagerFactory.getDefault().deleteUser(userId);
            throw e;
        }

    }

    @Override
    public QueryResult<Session> login(String userId, String password, String sessionIp) throws CatalogException, IOException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(password, "password");
        ParamUtils.checkParameter(sessionIp, "sessionIp");

        String authId;
        QueryResult<User> user = null;
        if (!userId.equals("admin")) {
            user = userDBAdaptor.get(userId, new QueryOptions(), null);
            if (user.getNumResults() == 0) {
                throw new CatalogException("The user id " + userId + " does not exist.");
            }

            // Check that the authentication id is valid
            authId = getAuthenticationOriginId(userId);
        } else {
            authId = INTERNAL_AUTHORIZATION;
        }
        AuthenticationOrigin authenticationOrigin = getAuthenticationOrigin(authId);

        if (authenticationOrigin == null) {
            throw new CatalogException("Could not find authentication origin " + authId + " for user " + userId);
        }

        if (AuthenticationOrigin.AuthenticationType.LDAP == authenticationOrigin.getType()) {
            if (user == null) {
                throw new CatalogException("Internal error: This error should never happen.");
            }
            authenticationManagerMap.get(authId).authenticate(((String) user.first().getAttributes().get("LDAP_RDN")), password, true);
        } else {
            authenticationManagerMap.get(authId).authenticate(userId, password, true);
        }

        QueryResult<Session> sessionTokenQueryResult;
        try {
            sessionTokenQueryResult = catalogManager.getSessionManager().createToken(userId, sessionIp, Session.Type.USER);
        } catch (CatalogException e) {
            auditManager.recordAction(AuditRecord.Resource.user, AuditRecord.Action.login, AuditRecord.Magnitude.high, userId, userId,
                    null, null, "Unsuccessfully login attempt", null);
            throw e;
        }

        auditManager.recordAction(AuditRecord.Resource.user, AuditRecord.Action.login, AuditRecord.Magnitude.low, userId, userId, null,
                sessionTokenQueryResult.first(), "User successfully logged in", null);

        return sessionTokenQueryResult;
    }

    @Override
    public QueryResult<Session> getNewUserSession(String sessionId, String userId) throws CatalogException {
        authenticationManagerMap.get(INTERNAL_AUTHORIZATION).authenticate("admin", sessionId, true);
        return catalogManager.getSessionManager().createToken(userId, "localhost", Session.Type.SYSTEM);
    }

    @Override
    public QueryResult logout(String userId, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(sessionId, "sessionId");

        QueryResult<Session> logout = null;
        try {
            if (userId.equals("admin")) {
                catalogDBAdaptorFactory.getCatalogMetaDBAdaptor().logout(sessionId);
                return logout;
            } else {
                logout = userDBAdaptor.logout(userId, sessionId);
            }
        } catch (CatalogDBException e) {
            auditManager.recordAction(AuditRecord.Resource.user, AuditRecord.Action.logout, AuditRecord.Magnitude.high, userId, userId,
                    null, null, "Unsuccessfully logout attempt", null);
            throw e;
        }
        auditManager.recordAction(AuditRecord.Resource.user, AuditRecord.Action.logout, AuditRecord.Magnitude.low, userId, userId,
                logout.first(), null, "User successfully logged out", null);

        return logout;
//        switch (authorizationManager.getUserRole(userId)) {
//            case ANONYMOUS:
//                return logoutAnonymous(sessionId);
//            default:
////                List<Session> sessions = Collections.singletonList(sessionManager.logout(userId, sessionId));
////                return new QueryResult<>("logout", 0, 1, 1, "", "", sessions);
//                return userDBAdaptor.logout(userId, sessionId);
//        }
    }

    @Deprecated
    @Override
    public QueryResult logoutAnonymous(String sessionId) throws CatalogException {
        ParamUtils.checkParameter(sessionId, "sessionId");
        String userId = getId(sessionId);
        ParamUtils.checkParameter(userId, "userId");
        checkSessionId(userId, sessionId);

        logger.info("logout anonymous user. userId: " + userId + " sesionId: " + sessionId);

        catalogIOManagerFactory.getDefault().deleteAnonymousUser(userId);
        return userDBAdaptor.logoutAnonymous(sessionId);
    }

    @Override
    public QueryResult<User.Filter> addFilter(String userId, String sessionId, String name, String description, File.Bioformat bioformat,
                                              Query query, QueryOptions queryOptions) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(sessionId, "sessionId");
        ParamUtils.checkParameter(name, "name");
        ParamUtils.checkObj(bioformat, "bioformat");
        ParamUtils.checkObj(query, "Query");
        ParamUtils.checkObj(queryOptions, "QueryOptions");
        if (description == null) {
            description = "";
        }

        String userIdAux = getId(sessionId);
        userDBAdaptor.checkId(userId);
        if (!userId.equals(userIdAux)) {
            throw new CatalogException("User " + userIdAux + " is not authorised to store filters for user " + userId);
        }

        Query queryExists = new Query()
                .append(UserDBAdaptor.QueryParams.ID.key(), userId)
                .append(UserDBAdaptor.QueryParams.CONFIGS_FILTERS_NAME.key(), name);
        if (userDBAdaptor.count(queryExists).first() > 0) {
            throw new CatalogException("There already exists a filter called " + name + " for user " + userId);
        }

        User.Filter filter = new User.Filter(name, description, bioformat, query, queryOptions);
        return userDBAdaptor.addFilter(userId, filter);
    }

    @Override
    public QueryResult<User.Filter> updateFilter(String userId, String sessionId, String name, ObjectMap params) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(sessionId, "sessionId");
        ParamUtils.checkParameter(name, "name");

        String userIdAux = getId(sessionId);
        userDBAdaptor.checkId(userId);
        if (!userId.equals(userIdAux)) {
            throw new CatalogException("User " + userIdAux + " is not authorised to update filters for user " + userId);
        }

        Query queryExists = new Query()
                .append(UserDBAdaptor.QueryParams.ID.key(), userId)
                .append(UserDBAdaptor.QueryParams.CONFIGS_FILTERS_NAME.key(), name);
        if (userDBAdaptor.count(queryExists).first() == 0) {
            throw new CatalogException("There is no filter called " + name + " for user " + userId);
        }

        QueryResult<Long> queryResult = userDBAdaptor.updateFilter(userId, name, params);
        User.Filter filter = getFilter(userId, name);
        if (filter == null) {
            throw new CatalogException("Internal error: The filter " + name + " could not be found.");
        }

        return new QueryResult<>("Update filter", queryResult.getDbTime(), 1, 1, queryResult.getWarningMsg(), queryResult.getErrorMsg(),
                Arrays.asList(filter));
    }

    @Override
    public QueryResult<User.Filter> deleteFilter(String userId, String sessionId, String name) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(sessionId, "sessionId");
        ParamUtils.checkParameter(name, "name");

        String userIdAux = getId(sessionId);
        userDBAdaptor.checkId(userId);
        if (!userId.equals(userIdAux)) {
            throw new CatalogException("User " + userIdAux + " is not authorised to delete filters for user " + userId);
        }

        User.Filter filter = getFilter(userId, name);
        if (filter == null) {
            throw new CatalogException("There is no filter called " + name + " for user " + userId);
        }

        QueryResult<Long> queryResult = userDBAdaptor.deleteFilter(userId, name);
        return new QueryResult<>("Delete filter", queryResult.getDbTime(), 1, 1, queryResult.getWarningMsg(), queryResult.getErrorMsg(),
                Arrays.asList(filter));
    }

    @Override
    public QueryResult<User.Filter> getFilter(String userId, String sessionId, String name) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(sessionId, "sessionId");
        ParamUtils.checkParameter(name, "name");

        String userIdAux = getId(sessionId);
        userDBAdaptor.checkId(userId);
        if (!userId.equals(userIdAux)) {
            throw new CatalogException("User " + userIdAux + " is not authorised to get filters from user " + userId);
        }

        User.Filter filter = getFilter(userId, name);
        if (filter == null) {
            return new QueryResult<>("Get filter", 0, 0, 0, "", "Filter not found", Arrays.asList());
        } else {
            return new QueryResult<>("Get filter", 0, 1, 1, "", "", Arrays.asList(filter));
        }
    }

    @Override
    public QueryResult<User.Filter> getAllFilters(String userId, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(sessionId, "sessionId");

        String userIdAux = getId(sessionId);
        userDBAdaptor.checkId(userId);
        if (!userId.equals(userIdAux)) {
            throw new CatalogException("User " + userIdAux + " is not authorised to get filters from user " + userId);
        }

        Query query = new Query()
                .append(UserDBAdaptor.QueryParams.ID.key(), userId);
        QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, UserDBAdaptor.QueryParams.CONFIGS.key());
        QueryResult<User> userQueryResult = userDBAdaptor.get(query, queryOptions);

        if (userQueryResult.getNumResults() != 1) {
            throw new CatalogException("Internal error: User " + userId + " not found.");
        }

        List<User.Filter> filters = userQueryResult.first().getConfigs().getFilters();

        return new QueryResult<>("Get filters", 0, filters.size(), filters.size(), "", "", filters);
    }

    @Override
    public QueryResult setConfig(String userId, String sessionId, String name, ObjectMap config) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(sessionId, "sessionId");
        ParamUtils.checkParameter(name, "name");
        ParamUtils.checkObj(config, "ObjectMap");

        String userIdAux = getId(sessionId);
        userDBAdaptor.checkId(userId);
        if (!userId.equals(userIdAux)) {
            throw new CatalogException("User " + userIdAux + " is not authorised to set configuration for user " + userId);
        }

        return userDBAdaptor.setConfig(userId, name, config);
    }

    @Override
    public QueryResult deleteConfig(String userId, String sessionId, String name) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(sessionId, "sessionId");
        ParamUtils.checkParameter(name, "name");

        String userIdAux = getId(sessionId);
        userDBAdaptor.checkId(userId);
        if (!userId.equals(userIdAux)) {
            throw new CatalogException("User " + userIdAux + " is not authorised to delete the configuration of user " + userId);
        }

        QueryOptions options = new QueryOptions(QueryOptions.INCLUDE, UserDBAdaptor.QueryParams.CONFIGS.key());
        QueryResult<User> userQueryResult = userDBAdaptor.get(userId, options, "");
        if (userQueryResult.getNumResults() == 0) {
            throw new CatalogException("Internal error: Could not get user " + userId);
        }

        User.UserConfiguration configs = userQueryResult.first().getConfigs();
        if (configs == null) {
            throw new CatalogException("Internal error: Configuration object is null.");
        }

        if (configs.get(name) == null) {
            throw new CatalogException("Error: Cannot delete configuration with name " + name + ". Configuration name not found.");
        }

        QueryResult<Long> queryResult = userDBAdaptor.deleteConfig(userId, name);
        return new QueryResult("Delete configuration", queryResult.getDbTime(), 1, 1, "", "", Arrays.asList(configs.get(name)));
    }

    @Override
    public QueryResult getConfig(String userId, String sessionId, String name) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(sessionId, "sessionId");
        ParamUtils.checkParameter(name, "name");

        String userIdAux = getId(sessionId);
        userDBAdaptor.checkId(userId);
        if (!userId.equals(userIdAux)) {
            throw new CatalogException("User " + userIdAux + " is not authorised to fetch the configuration of user " + userId);
        }

        QueryOptions options = new QueryOptions(QueryOptions.INCLUDE, UserDBAdaptor.QueryParams.CONFIGS.key());
        QueryResult<User> userQueryResult = userDBAdaptor.get(userId, options, "");
        if (userQueryResult.getNumResults() == 0) {
            throw new CatalogException("Internal error: Could not get user " + userId);
        }

        User.UserConfiguration configs = userQueryResult.first().getConfigs();
        if (configs == null) {
            throw new CatalogException("Internal error: Configuration object is null.");
        }

        if (configs.get(name) == null) {
            throw new CatalogException("Error: Cannot fetch configuration with name " + name + ". Configuration name not found.");
        }

        return new QueryResult("Get configuration", userQueryResult.getDbTime(), 1, 1, userQueryResult.getWarningMsg(),
                userQueryResult.getErrorMsg(), Arrays.asList(configs.get(name)));
    }


    private User.Filter getFilter(String userId, String name) throws CatalogException {
        Query query = new Query()
                .append(UserDBAdaptor.QueryParams.ID.key(), userId);
        QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, UserDBAdaptor.QueryParams.CONFIGS.key());
        QueryResult<User> userQueryResult = userDBAdaptor.get(query, queryOptions);

        if (userQueryResult.getNumResults() != 1) {
            throw new CatalogException("Internal error: User " + userId + " not found.");
        }

        for (User.Filter filter : userQueryResult.first().getConfigs().getFilters()) {
            if (name.equals(filter.getName())) {
                return filter;
            }
        }

        return null;
    }

    private void checkSessionId(String userId, String sessionId) throws CatalogException {
        String userIdBySessionId = userDBAdaptor.getUserIdBySessionId(sessionId);
        if (!userIdBySessionId.equals(userId)) {
            throw new CatalogException("Invalid sessionId for user: " + userId);
        }
    }

    private void checkUserExists(String userId) throws CatalogException {
        if (userId.toLowerCase().equals("admin")) {
            throw new CatalogException("Permission denied: It is not allowed the creation of another admin user.");
        } else if (userId.toLowerCase().equals("anonymous") || userId.toLowerCase().equals("daemon") || userId.equals("*")) {
            throw new CatalogException("Permission denied: Cannot create users with special treatments in catalog.");
        }

        if (userDBAdaptor.exists(userId)) {
            throw new CatalogException("The user already exists in our database. Please, choose a different one.");
        }
    }

}
