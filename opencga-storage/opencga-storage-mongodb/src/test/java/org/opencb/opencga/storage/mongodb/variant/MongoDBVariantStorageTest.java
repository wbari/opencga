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

package org.opencb.opencga.storage.mongodb.variant;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.opencb.biodata.models.variant.VariantSource;
import org.opencb.biodata.models.variant.avro.VariantType;
import org.opencb.commons.datastore.mongodb.MongoDataStoreManager;
import org.opencb.opencga.storage.core.config.StorageConfiguration;
import org.opencb.opencga.storage.core.variant.VariantStorageTest;
import org.opencb.opencga.storage.mongodb.auth.MongoCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.opencb.opencga.storage.core.variant.VariantStorageBaseTest.DB_NAME;

/**
 * Created by hpccoll1 on 01/06/15.
 */
public interface MongoDBVariantStorageTest extends VariantStorageTest {

    Logger logger = LoggerFactory.getLogger(MongoDBVariantStorageTest.class);
    AtomicReference<MongoDBVariantStorageEngine> manager = new AtomicReference<>(null);
    List<MongoDBVariantStorageEngine> managers = Collections.synchronizedList(new ArrayList<>());

    default MongoDBVariantStorageEngine getVariantStorageEngine() throws Exception {
        synchronized (manager) {
            MongoDBVariantStorageEngine storageManager = manager.get();
            if (storageManager == null) {
                storageManager = new MongoDBVariantStorageEngine();
                manager.set(storageManager);
            }
            InputStream is = MongoDBVariantStorageTest.class.getClassLoader().getResourceAsStream("storage-configuration.yml");
            StorageConfiguration storageConfiguration = StorageConfiguration.load(is);
            storageManager.setConfiguration(storageConfiguration, MongoDBVariantStorageEngine.STORAGE_ENGINE_ID, DB_NAME);
            return storageManager;
        }
    }

    default MongoDBVariantStorageEngine newVariantStorageEngine() throws Exception {
        synchronized (managers) {
            MongoDBVariantStorageEngine storageManager = new MongoDBVariantStorageEngine();
            InputStream is = MongoDBVariantStorageTest.class.getClassLoader().getResourceAsStream("storage-configuration.yml");
            StorageConfiguration storageConfiguration = StorageConfiguration.load(is);
            storageManager.setConfiguration(storageConfiguration, MongoDBVariantStorageEngine.STORAGE_ENGINE_ID, DB_NAME);
            managers.add(storageManager);
            return storageManager;
        }
    }

    default void closeConnections() throws IOException {
        System.out.println("Closing MongoDBVariantStorageEngine");
        for (MongoDBVariantStorageEngine manager : managers) {
            System.out.println("closing manager = " + manager);
            manager.close();
        }
        managers.clear();
        if (manager.get() != null) {
            manager.get().close();
        }
    }

    default void clearDB(String dbName) throws Exception {
        MongoCredentials credentials = getVariantStorageEngine().getMongoCredentials();
        logger.info("Cleaning MongoDB {}", credentials.getMongoDbName());
        try (MongoDataStoreManager mongoManager = new MongoDataStoreManager(credentials.getDataStoreServerAddresses())) {
            mongoManager.get(credentials.getMongoDbName(), credentials.getMongoDBConfiguration());
            mongoManager.drop(credentials.getMongoDbName());
        }
    }

    @Override
    default void close() throws Exception {
        closeConnections();
    }

    default int getExpectedNumLoadedVariants(VariantSource source) {
        int numRecords = source.getStats().getNumRecords();
        return numRecords
                - source.getStats().getVariantTypeCount(VariantType.SYMBOLIC)
                - source.getStats().getVariantTypeCount(VariantType.NO_VARIATION);
    }


    default MongoDataStoreManager getMongoDataStoreManager(String dbName) throws Exception {
        MongoCredentials credentials = getVariantStorageEngine().getMongoCredentials();
        return new MongoDataStoreManager(credentials.getDataStoreServerAddresses());
    }

    default void logLevel(String level) {
        ConsoleAppender stderr = (ConsoleAppender) LogManager.getRootLogger().getAppender("stderr");
        stderr.setThreshold(Level.toLevel(level));
        org.apache.log4j.Logger.getLogger("org.mongodb.driver.cluster").setLevel(Level.WARN);
        org.apache.log4j.Logger.getLogger("org.mongodb.driver.connection").setLevel(Level.WARN);
        org.apache.log4j.Logger.getLogger("org.mongodb.driver.protocol.update").setLevel(Level.WARN);
        org.apache.log4j.Logger.getLogger("org.mongodb.driver.protocol.command").setLevel(Level.WARN);
        org.apache.log4j.Logger.getLogger("org.mongodb.driver.protocol.query").setLevel(Level.WARN);
        org.apache.log4j.Logger.getLogger("org.mongodb.driver.protocol.getmore").setLevel(Level.WARN);
    }
}
