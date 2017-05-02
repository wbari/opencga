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

package org.opencb.opencga.catalog;

import org.apache.log4j.Level;
import org.junit.rules.ExternalResource;
import org.opencb.commons.datastore.core.DataStoreServerAddress;
import org.opencb.commons.datastore.mongodb.MongoDataStore;
import org.opencb.commons.datastore.mongodb.MongoDataStoreManager;
import org.opencb.opencga.catalog.config.Configuration;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.managers.CatalogManager;
import org.opencb.opencga.core.common.TimeUtils;
import org.opencb.opencga.core.common.UriUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

/**
 * Created on 05/05/16
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class CatalogManagerExternalResource extends ExternalResource {

    private static CatalogManager catalogManager;
    private Configuration configuration;
    private Path opencgaHome;


    public CatalogManagerExternalResource() {

        org.apache.log4j.Logger.getLogger("org.mongodb.driver.cluster").setLevel(Level.WARN);
        org.apache.log4j.Logger.getLogger("org.mongodb.driver.connection").setLevel(Level.WARN);

    }


    @Override
    public void before() throws Exception {
        int c = 0;
        do {
            opencgaHome = Paths.get("target/test-data").resolve("junit_opencga_home_" + TimeUtils.getTimeMillis() + (c > 0 ? "_" + c : ""));
            c++;
        } while (opencgaHome.toFile().exists());
        Files.createDirectories(opencgaHome);
        configuration = Configuration.load(getClass().getResource("/configuration-test.yml").openStream());
        configuration.setDataDir(opencgaHome.resolve("sessions").toUri().toString());
        configuration.setTempJobsDir(opencgaHome.resolve("jobs").toUri().toString());

        catalogManager = new CatalogManager(configuration);
        try {
            catalogManager.deleteCatalogDB(false);
        } catch (Exception ignore) {}
        clearCatalog(configuration);
        if (!opencgaHome.toFile().exists()) {
            deleteFolderTree(opencgaHome.toFile());
            Files.createDirectory(opencgaHome);
        }
        catalogManager.installCatalogDB();
    }

    @Override
    public void after() {
        super.after();
        try {
            catalogManager.close();
        } catch (CatalogException e) {
            throw new RuntimeException(e);
        }
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public CatalogManager getCatalogManager() {
        return catalogManager;
    }

    public Path getOpencgaHome() {
        return opencgaHome;
    }

    public static void clearCatalog(Configuration configuration) throws IOException, CatalogException, URISyntaxException {
        List<DataStoreServerAddress> dataStoreServerAddresses = new LinkedList<>();
        for (String hostPort : configuration.getCatalog().getDatabase().getHosts()) {
            if (hostPort.contains(":")) {
                String[] split = hostPort.split(":");
                Integer port = Integer.valueOf(split[1]);
                dataStoreServerAddresses.add(new DataStoreServerAddress(split[0], port));
            } else {
                dataStoreServerAddresses.add(new DataStoreServerAddress(hostPort, 27017));
            }
        }
        MongoDataStoreManager mongoManager = new MongoDataStoreManager(dataStoreServerAddresses);

        if (catalogManager == null) {
            catalogManager = new CatalogManager(configuration);
        }

//        MongoDataStore db = mongoManager.get(catalogConfiguration.getDatabase().getDatabase());
        MongoDataStore db = mongoManager.get(catalogManager.getCatalogDatabase());
        db.getDb().drop();
//        mongoManager.close(catalogConfiguration.getDatabase().getDatabase());
        mongoManager.close(catalogManager.getCatalogDatabase());

        Path rootdir = Paths.get(UriUtils.createDirectoryUri(configuration.getDataDir()));
        deleteFolderTree(rootdir.toFile());
        if (!configuration.getTempJobsDir().isEmpty()) {
            Path jobsDir = Paths.get(UriUtils.createDirectoryUri(configuration.getTempJobsDir()));
            if (jobsDir.toFile().exists()) {
                deleteFolderTree(jobsDir.toFile());
            }
        }
    }

    public static void deleteFolderTree(java.io.File folder) {
        java.io.File[] files = folder.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.isDirectory()) {
                    deleteFolderTree(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }
}
