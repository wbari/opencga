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

package org.opencb.opencga.catalog.utils;

import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.catalog.config.Configuration;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.managers.CatalogManager;
import org.opencb.opencga.catalog.models.Study;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by pfurio on 08/06/16.
 */
public final class CatalogDemo {

    private CatalogDemo() {

    }

    /**
     * Populates the database with dummy data.
     *
     * @param configuration Catalog configuration file.
     * @param force Used in the case where a database already exists with the same name. When force = true, it will override it.
     * @throws CatalogException when there is already a database with the same name and force is false.
     */
    public static void createDemoDatabase(Configuration configuration, boolean force) throws CatalogException {
        CatalogManager catalogManager = new CatalogManager(configuration);
        if (catalogManager.existsCatalogDB()) {
            if (force) {
                catalogManager.deleteCatalogDB(force);
            } else {
//                throw new CatalogException("A database called " + catalogConfiguration.getDatabase().getDatabase() + " already exists");
                throw new CatalogException("A database called " + catalogManager.getCatalogDatabase() + " already exists");
            }
        }
        catalogManager.installCatalogDB();
        try {
            populateDatabase(catalogManager);
        } catch (IOException e) {
            throw new CatalogException(e.getMessage());
        }
    }

    private static void populateDatabase(CatalogManager catalogManager) throws CatalogException, IOException {
        // Create users
        Map<String, String> userSessions = new HashMap<>(5);
        for (int i = 1; i <= 5; i++) {
            String id = "user" + i;
            String name = "User" + i;
            String password = id + "_pass";
            String email = id + "@gmail.com";
            catalogManager.createUser(id, name, email, password, "organization", 2000L, null);
            userSessions.put(id, catalogManager.login(id, password, "localhost").first().getId());
        }

        // Create one project per user
        Map<String, Long> projects = new HashMap<>(5);
        for (Map.Entry<String, String> userSession : userSessions.entrySet()) {
            projects.put(userSession.getKey(), catalogManager.getProjectManager().create("DefaultProject", "default",
                    "Description", "Organization", "Homo sapiens", null, null, "GrCh38",
                    new QueryOptions(), userSession.getValue()).first().getId());
        }

        // Create two studies per user
        Map<String, List<Long>> studies = new HashMap<>(5);
        for (Map.Entry<String, String> userSession : userSessions.entrySet()) {
            long projectId = projects.get(userSession.getKey());
            List<Long> studiesTmp = new ArrayList<>(2);
            for (int i = 1; i <= 2; i++) {
                String name = "Name of study" + i;
                String alias = "study" + i;
                studiesTmp.add(catalogManager.createStudy(projectId, name, alias, Study.Type.FAMILY, "Description of " + alias,
                        userSession.getValue()).first().getId());
            }
            studies.put(userSession.getKey(), studiesTmp);
        }

        /*
        SHARE STUDY1 OF USER1
         */
        long studyId = studies.get("user1").get(0);
        String sessionId = userSessions.get("user5");

        // user5 will have the role "admin"
        catalogManager.createStudyAcls(Long.toString(studyId), "user5", "", "admin", userSessions.get("user1"));
        // user5 will add the rest of users. user2, user3 and user4 go to group "members"
        catalogManager.createGroup(Long.toString(studyId), "members", "user2,user3,user4", sessionId);
//        // @members will have the role "analyst"
        catalogManager.createStudyAcls(Long.toString(studyId), "@members", "", "analyst", sessionId);
//        // Add anonymous user to the role "denyAll". Later we will give it permissions to see some concrete samples.
        catalogManager.createStudyAcls(Long.toString(studyId), "anonymous", "", "locked", sessionId);
    }

}
