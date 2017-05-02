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

package org.opencb.opencga.catalog.db.mongodb;

import org.junit.Test;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.catalog.db.api.UserDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.models.File;
import org.opencb.opencga.catalog.models.Session;
import org.opencb.opencga.catalog.models.Status;
import org.opencb.opencga.catalog.models.User;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Created by pfurio on 19/01/16.
 */
public class UserMongoDBAdaptorTest extends MongoDBAdaptorTest {

    @Test
    public void nativeGet() throws Exception {
        Query query = new Query("id", "imedina");
        QueryResult queryResult = catalogUserDBAdaptor.nativeGet(query, null);
    }

    @Test
    public void createUserTest() throws CatalogException {

        User user = new User("NewUser", "", "", "", "", User.UserStatus.READY);
        QueryResult createUser = catalogUserDBAdaptor.insert(user, null);
        assertNotSame(0, createUser.getResult().size());

        thrown.expect(CatalogDBException.class);
        catalogUserDBAdaptor.insert(user, null);
    }

    @Test
    public void deleteUserTest() throws CatalogException {
        User deletable1 = new User("deletable1", "deletable 1", "d1@ebi", "1234", "", User.UserStatus.READY);
        QueryResult<User> createUser = catalogUserDBAdaptor.insert(deletable1, null);
        assertFalse(createUser.getResult().isEmpty());
        assertNotNull(createUser.first());

        assertEquals(Status.READY, createUser.first().getStatus().getName());

        QueryResult<User> deleteUser = catalogUserDBAdaptor.delete(deletable1.getId(), new QueryOptions());
        assertFalse(deleteUser.getResult().isEmpty());
        assertNotNull(deleteUser.first());
        assertEquals(Status.TRASHED, deleteUser.first().getStatus().getName());


        /*
        thrown.expect(CatalogDBException.class);
        catalogUserDBAdaptor.delete(deletable1.getId());
        */
    }

    @Test
    public void getUserTest() throws CatalogDBException {
        QueryResult<User> user = catalogUserDBAdaptor.get(user1.getId(), null, null);
        assertNotSame(0, user.getResult().size());

        user = catalogUserDBAdaptor.get(user3.getId(), null, null);
        assertFalse(user.getResult().isEmpty());
        assertFalse(user.first().getProjects().isEmpty());

        user = catalogUserDBAdaptor.get(user3.getId(), new QueryOptions("exclude", Arrays.asList("projects")), null);
        assertEquals(null, user.first().getProjects());

        user = catalogUserDBAdaptor.get(user3.getId(), null, user.first().getLastModified());
        assertTrue(user.getResult().isEmpty());

        thrown.expect(CatalogDBException.class);
        catalogUserDBAdaptor.get("NonExistingUser", null, null);
    }

    @Test
    public void loginTest() throws CatalogException, IOException {
        String userId = user1.getId();
        Session sessionJCOLL = new Session("127.0.0.1", 20);
        catalogUserDBAdaptor.addSession(userId, sessionJCOLL);

        // Check password is correct in the database
        String storedPassword = catalogUserDBAdaptor.get(userId, new QueryOptions(QueryOptions.INCLUDE, "password"), null).first()
                .getPassword();
        assertEquals("1234", storedPassword);
    }

    @Test
    public void logoutTest() throws CatalogDBException, IOException {
        String userId = user1.getId();

        Session sessionJCOLL = new Session("127.0.0.1", 20);
        catalogUserDBAdaptor.addSession(userId, sessionJCOLL);

        QueryResult<Session> logout = catalogUserDBAdaptor.logout(userId, sessionJCOLL.getId());
        assertEquals(1, logout.getResult().size());

        //thrown.expect(CatalogDBException.class);
        QueryResult<Session> falseSession = catalogUserDBAdaptor.logout(userId, "FalseSession");
        assertTrue(falseSession.getWarningMsg() != null && !falseSession.getWarningMsg().isEmpty());
        assertEquals("Session not found", falseSession.getWarningMsg());
    }

    @Test
    public void getUserIdBySessionId() throws CatalogDBException {
        String userId = user1.getId();

        catalogUserDBAdaptor.addSession(userId, new Session("127.0.0.1", 20)); //Having multiple conections
        catalogUserDBAdaptor.addSession(userId, new Session("127.0.0.1", 20));
        catalogUserDBAdaptor.addSession(userId, new Session("127.0.0.1", 20));

        Session sessionJCOLL = new Session("127.0.0.1", 20);
        catalogUserDBAdaptor.addSession(userId, sessionJCOLL);

        assertEquals(user1.getId(), catalogUserDBAdaptor.getUserIdBySessionId(sessionJCOLL.getId()));
        QueryResult logout = catalogUserDBAdaptor.logout(userId, sessionJCOLL.getId());
        assertEquals(1, logout.getResult().size());

        assertEquals("", catalogUserDBAdaptor.getUserIdBySessionId(sessionJCOLL.getId()));
    }

    @Test
    public void changePasswordTest() throws CatalogDBException {
//        System.out.println(catalogUserDBAdaptor.changePassword("jmmut", "1111", "1234"));
//        System.out.println(catalogUserDBAdaptor.changePassword("jmmut", "1234", "1111"));
//        try {
//            System.out.println(catalogUserDBAdaptor.changePassword("jmmut", "BAD_PASSWORD", "asdf"));
//            fail("Expected \"bad password\" exception");
//        } catch (CatalogDBException e) {
//            System.out.println(e);
//        }
        QueryResult queryResult = catalogUserDBAdaptor.changePassword(user2.getId(), user2.getPassword(), "1234");
        assertNotSame(0, queryResult.getResult().size());

        thrown.expect(CatalogDBException.class);
        catalogUserDBAdaptor.changePassword(user2.getId(), "BAD_PASSWORD", "asdf");
    }

    @Test
    public void modifyUserTest() throws CatalogDBException {
        ObjectMap genomeMapsConfig = new ObjectMap("lastPosition", "4:1222222:1333333");
        genomeMapsConfig.put("otherConf", Arrays.asList(1, 2, 3, 4, 5));
        catalogUserDBAdaptor.setConfig(user1.getId(), "genomemaps", genomeMapsConfig);

        User user = catalogUserDBAdaptor.get(user1.getId(), null, null).first();
        assertNotNull(user.getConfigs().get("genomemaps"));
        Map<String, Object> genomemaps = (Map<String, Object>) user.getConfigs().get("genomemaps");
        assertNotNull(genomemaps.get("otherConf"));
        assertNotNull(genomemaps.get("lastPosition"));
    }

    @Test
    public void addFilterTest() throws CatalogDBException, IOException {
        Query query = new Query("key1", "value1").append("key2", "value2");
        QueryOptions options = new QueryOptions(QueryOptions.INCLUDE, Arrays.asList("key1", "key2"));
        User.Filter filter = new User.Filter("filter1", "Description of filter 1", File.Bioformat.ALIGNMENT, query, options);

        catalogUserDBAdaptor.addFilter(user4.getId(), filter);
        QueryResult<User> userQueryResult = catalogUserDBAdaptor.get(user4.getId(), new QueryOptions(), null);

        User.Filter filterResult = userQueryResult.first().getConfigs().getFilters().get(0);

        assertEquals(filter.getName(), filterResult.getName());
        assertEquals(filter.getDescription(), filterResult.getDescription());
        assertEquals(filter.getBioformat(), filterResult.getBioformat());
        assertEquals(filter.getQuery().safeToString(), filterResult.getQuery().safeToString());
        assertEquals(filter.getOptions().safeToString(), filterResult.getOptions().safeToString());
    }

    @Test
    public void updateFilterTest() throws CatalogDBException, IOException {
        Query query = new Query("key1", "value1").append("key2", "value2");
        QueryOptions options = new QueryOptions(QueryOptions.INCLUDE, Arrays.asList("key1", "key2"));
        User.Filter filter = new User.Filter("filter1", "Description of filter 1", File.Bioformat.ALIGNMENT, query, options);
        catalogUserDBAdaptor.addFilter(user4.getId(), filter);

        ObjectMap params = new ObjectMap()
                .append(UserDBAdaptor.FilterParams.DESCRIPTION.key(), "The description has changed")
                .append(UserDBAdaptor.FilterParams.BIOFORMAT.key(), File.Bioformat.VARIANT)
                .append(UserDBAdaptor.FilterParams.QUERY.key(), new Query("key3", "whatever"))
                .append(UserDBAdaptor.FilterParams.OPTIONS.key(), new QueryOptions("options", "optionsValue"));
        catalogUserDBAdaptor.updateFilter(user4.getId(), filter.getName(), params);

        QueryResult<User> userQueryResult = catalogUserDBAdaptor.get(user4.getId(), new QueryOptions(), null);

        User.Filter filterResult = userQueryResult.first().getConfigs().getFilters().get(0);

        assertEquals(filter.getName(), filterResult.getName());
        assertEquals(params.get(UserDBAdaptor.FilterParams.DESCRIPTION.key()), filterResult.getDescription());
        assertEquals(params.get(UserDBAdaptor.FilterParams.BIOFORMAT.key()), filterResult.getBioformat());
        assertEquals(((Query) params.get(UserDBAdaptor.FilterParams.QUERY.key())).safeToString(), filterResult.getQuery().safeToString());
        assertEquals(((QueryOptions) params.get(UserDBAdaptor.FilterParams.OPTIONS.key())).safeToString(),
                filterResult.getOptions().safeToString());
    }

    @Test
    public void deleteFilterTest() throws CatalogDBException, IOException {
        Query query = new Query("key1", "value1").append("key2", "value2");
        QueryOptions options = new QueryOptions(QueryOptions.INCLUDE, Arrays.asList("key1", "key2"));
        User.Filter filter = new User.Filter("filter1", "Description of filter 1", File.Bioformat.ALIGNMENT, query, options);

        catalogUserDBAdaptor.addFilter(user4.getId(), filter);
        catalogUserDBAdaptor.deleteFilter(user4.getId(), filter.getName());
        QueryResult<User> userQueryResult = catalogUserDBAdaptor.get(user4.getId(), new QueryOptions(), null);

        User.UserConfiguration configs = userQueryResult.first().getConfigs();
        assertTrue(configs.getFilters().size() == 0);
    }


    @Test
    public void setConfigTest() throws CatalogDBException, IOException {
        ObjectMap objectMap = new ObjectMap()
                .append("key1", Arrays.asList(1,2,3,4,5))
                .append("key2", new ObjectMap("key21", 21).append("key22", 22));

        QueryResult queryResult = catalogUserDBAdaptor.setConfig(user4.getId(), "config1", objectMap);

        assertEquals(1, queryResult.getNumResults());

        LinkedHashMap result = (LinkedHashMap) queryResult.first();
        assertTrue(result.get("key1") instanceof List);
        assertTrue(result.get("key2") instanceof Map);

        // Update the config
        objectMap.put("key2", objectMap.get("key1"));
        queryResult = catalogUserDBAdaptor.setConfig(user4.getId(), "config1", objectMap);
        assertEquals(1, queryResult.getNumResults());

        result = (LinkedHashMap) queryResult.first();
        assertTrue(result.get("key1") instanceof List);
        assertTrue(result.get("key2") instanceof List);
    }

    @Test
    public void deleteConfigTest() throws CatalogDBException, IOException {
        ObjectMap objectMap = new ObjectMap()
                .append("key1", Arrays.asList(1,2,3,4,5))
                .append("key2", new ObjectMap("key21", 21).append("key22", 22));

        catalogUserDBAdaptor.setConfig(user4.getId(), "config1", objectMap);

        catalogUserDBAdaptor.deleteConfig(user4.getId(), "config1");

        thrown.expect(CatalogDBException.class);
        thrown.expectMessage("Could not delete config1 configuration");
        catalogUserDBAdaptor.deleteConfig(user4.getId(), "config1");
    }


}
