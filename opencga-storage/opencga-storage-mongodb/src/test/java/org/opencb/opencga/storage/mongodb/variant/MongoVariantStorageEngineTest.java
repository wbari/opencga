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

import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opencb.biodata.formats.io.FileFormatException;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.mongodb.MongoDBCollection;
import org.opencb.commons.datastore.mongodb.MongoDataStore;
import org.opencb.opencga.storage.core.StoragePipelineResult;
import org.opencb.opencga.storage.core.exceptions.StorageEngineException;
import org.opencb.opencga.storage.core.exceptions.StoragePipelineException;
import org.opencb.opencga.storage.core.metadata.BatchFileOperation;
import org.opencb.opencga.storage.core.metadata.StudyConfiguration;
import org.opencb.opencga.storage.core.metadata.StudyConfigurationManager;
import org.opencb.opencga.storage.core.variant.VariantStorageBaseTest;
import org.opencb.opencga.storage.core.variant.VariantStorageEngine;
import org.opencb.opencga.storage.core.variant.VariantStorageManagerTest;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor;
import org.opencb.opencga.storage.mongodb.variant.MongoDBVariantStorageEngine.MongoDBVariantOptions;
import org.opencb.opencga.storage.mongodb.variant.adaptors.VariantMongoDBAdaptor;
import org.opencb.opencga.storage.mongodb.variant.converters.DocumentToSamplesConverter;
import org.opencb.opencga.storage.mongodb.variant.converters.DocumentToVariantConverter;
import org.opencb.opencga.storage.mongodb.variant.exceptions.MongoVariantStorageEngineException;
import org.opencb.opencga.storage.mongodb.variant.load.stage.MongoDBVariantStageLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.junit.internal.matchers.ThrowableCauseMatcher.hasCause;
import static org.junit.internal.matchers.ThrowableMessageMatcher.hasMessage;
import static org.opencb.opencga.storage.mongodb.variant.converters.DocumentToStudyVariantEntryConverter.*;


/**
 * @author Jacobo Coll <jacobo167@gmail.com>
 */
public class MongoVariantStorageEngineTest extends VariantStorageManagerTest implements MongoDBVariantStorageTest {

    @Before
    public void setUp() throws Exception {
        System.out.println("VariantMongoDBAdaptor.NUMBER_INSTANCES on setUp() " + VariantMongoDBAdaptor.NUMBER_INSTANCES.get());
    }

    @After
    public void tearDown() throws Exception {
        closeConnections();
        System.out.println("VariantMongoDBAdaptor.NUMBER_INSTANCES on tearDown() " + VariantMongoDBAdaptor.NUMBER_INSTANCES.get());
    }

    @Test
    public void stageResumeFromErrorTest() throws Exception {
        StudyConfiguration studyConfiguration = createStudyConfiguration();
        BatchFileOperation operation = new BatchFileOperation(MongoDBVariantOptions.STAGE.key(),
                Collections.singletonList(FILE_ID), System.currentTimeMillis(), BatchFileOperation.Type.OTHER);
        operation.addStatus(new Date(System.currentTimeMillis() - 100), BatchFileOperation.Status.RUNNING);
        operation.addStatus(new Date(System.currentTimeMillis() - 50), BatchFileOperation.Status.ERROR);
        // Last status is ERROR

        studyConfiguration.getBatches().add(operation);
        MongoDBVariantStorageEngine variantStorageManager = getVariantStorageEngine();
        variantStorageManager.getDBAdaptor().getStudyConfigurationManager().updateStudyConfiguration(studyConfiguration, null);

        System.out.println("----------------");
        System.out.println("|   RESUME     |");
        System.out.println("----------------");

        runDefaultETL(smallInputUri, variantStorageManager, studyConfiguration, new ObjectMap()
                .append(MongoDBVariantOptions.STAGE_RESUME.key(), false)
                .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
        );

    }

    @Test
    public void stageForceResumeTest() throws Exception {
        StudyConfiguration studyConfiguration = createStudyConfiguration();
        BatchFileOperation operation = new BatchFileOperation(MongoDBVariantOptions.STAGE.key(),
                Collections.singletonList(FILE_ID), System.currentTimeMillis(), BatchFileOperation.Type.OTHER);
        operation.addStatus(new Date(System.currentTimeMillis() - 100), BatchFileOperation.Status.RUNNING);
        operation.addStatus(new Date(System.currentTimeMillis() - 50), BatchFileOperation.Status.ERROR);
        operation.addStatus(new Date(System.currentTimeMillis()), BatchFileOperation.Status.RUNNING);
        // Last status is RUNNING
        studyConfiguration.getBatches().add(operation);
        MongoDBVariantStorageEngine variantStorageManager = getVariantStorageEngine();
        variantStorageManager.getDBAdaptor().getStudyConfigurationManager().updateStudyConfiguration(studyConfiguration, null);

        try {
            runDefaultETL(smallInputUri, variantStorageManager, studyConfiguration);
            fail();
        } catch (StorageEngineException e) {
            e.printStackTrace();
            MongoVariantStorageEngineException expected = MongoVariantStorageEngineException.fileBeingStagedException(FILE_ID, "variant-test-file.vcf.gz");
            assertThat(e, instanceOf(StoragePipelineException.class));
            assertThat(e, hasCause(instanceOf(expected.getClass())));
            assertThat(e, hasCause(hasMessage(is(expected.getMessage()))));
        }

        System.out.println("----------------");
        System.out.println("|   RESUME     |");
        System.out.println("----------------");

        runDefaultETL(smallInputUri, variantStorageManager, studyConfiguration, new ObjectMap()
                .append(MongoDBVariantOptions.STAGE_RESUME.key(), true)
                .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
        );
    }


    @Test
    public void stageResumeFromError2Test() throws Exception {
        StudyConfiguration studyConfiguration = createStudyConfiguration();

        StoragePipelineResult storagePipelineResult = runDefaultETL(smallInputUri, variantStorageEngine, studyConfiguration, new ObjectMap()
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), false));

        MongoDBVariantStorageEngine variantStorageManager = getVariantStorageEngine();
        VariantMongoDBAdaptor dbAdaptor = variantStorageManager.getDBAdaptor();

        long stageCount = simulateStageError(studyConfiguration, dbAdaptor);

        // Resume stage and merge
        runDefaultETL(storagePipelineResult.getTransformResult(), variantStorageManager, studyConfiguration, new ObjectMap()
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), true)
                .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                .append(VariantStorageEngine.Options.CALCULATE_STATS.key(), false), false, true);

        long count = dbAdaptor.count(null).first();
        assertEquals(stageCount, count);
    }

    private long simulateStageError(StudyConfiguration studyConfiguration, VariantMongoDBAdaptor dbAdaptor) throws Exception {
        // Simulate stage error
        // 1) Set ERROR status on the StudyConfiguration
        StudyConfigurationManager scm = dbAdaptor.getStudyConfigurationManager();
        studyConfiguration.copy(scm.getStudyConfiguration(studyConfiguration.getStudyId(), new QueryOptions()).first());
        assertEquals(1, studyConfiguration.getBatches().size());
        assertEquals(BatchFileOperation.Status.READY, studyConfiguration.getBatches().get(0).currentStatus());
        TreeMap<Date, BatchFileOperation.Status> status = studyConfiguration.getBatches().get(0).getStatus();
        status.remove(status.lastKey(), BatchFileOperation.Status.READY);
        studyConfiguration.getBatches().get(0).addStatus(BatchFileOperation.Status.ERROR);
        scm.updateStudyConfiguration(studyConfiguration, null);

        // 2) Remove from files collection
        MongoDataStore dataStore = getMongoDataStoreManager(DB_NAME).get(DB_NAME);
        MongoDBCollection files = dataStore.getCollection(MongoDBVariantOptions.COLLECTION_FILES.defaultValue());
        System.out.println("Files delete count " + files.remove(new Document(), new QueryOptions()).first().getDeletedCount());

        // 3) Clean some variants from the Stage collection.
        MongoDBCollection stage = dataStore.getCollection(MongoDBVariantOptions.COLLECTION_STAGE.defaultValue());

        long stageCount = stage.count().first();
        System.out.println("stage count : " + stageCount);
        int i = 0;
        for (Document document : stage.find(new Document(), Projections.include("_id"), null).getResult()) {
            stage.remove(document, null).first().getDeletedCount();
            i++;
            if (i >= stageCount / 2) {
                break;
            }
        }
        System.out.println("stage count : " + stage.count().first());
        return stageCount;
    }

    @Test
    public void mergeAlreadyStagedFileTest() throws Exception {
        StudyConfiguration studyConfiguration = createStudyConfiguration();

        StoragePipelineResult storagePipelineResult = runDefaultETL(smallInputUri, variantStorageEngine, studyConfiguration, new ObjectMap()
                .append(VariantStorageEngine.Options.FILE_ID.key(), 3)
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), false));

        runETL(variantStorageEngine, storagePipelineResult.getTransformResult(), outputUri, new ObjectMap()
                .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                .append(VariantStorageEngine.Options.FILE_ID.key(), -1)
                .append(MongoDBVariantOptions.STAGE.key(), false)
                .append(MongoDBVariantOptions.MERGE.key(), true), false, false, true);

        Long count = variantStorageEngine.getDBAdaptor().count(null).first();
        assertTrue(count > 0);
    }

    @Test
    public void loadStageConcurrent() throws Exception {
        StudyConfiguration studyConfiguration = createStudyConfiguration();
        StoragePipelineResult storagePipelineResult = runDefaultETL(smallInputUri, variantStorageEngine, studyConfiguration, new ObjectMap(), true, false);

        StoragePipelineException exception = loadConcurrentAndCheck(studyConfiguration, storagePipelineResult);
        exception.printStackTrace();
    }

    @Test
    public void loadMergeSameConcurrent() throws Exception {
        StudyConfiguration studyConfiguration = createStudyConfiguration();
        StoragePipelineResult storagePipelineResult = runDefaultETL(smallInputUri, variantStorageEngine, studyConfiguration,
                new ObjectMap()
                        .append(MongoDBVariantOptions.STAGE.key(), true)
                        .append(MongoDBVariantOptions.MERGE.key(), false), true, true);

        StoragePipelineException exception = loadConcurrentAndCheck(studyConfiguration, storagePipelineResult);
        exception.printStackTrace();
        assertEquals(1, exception.getResults().size());
        assertTrue(exception.getResults().get(0).isLoadExecuted());
        assertNotNull(exception.getResults().get(0).getLoadError());
        MongoVariantStorageEngineException expected = MongoVariantStorageEngineException.filesBeingMergedException(Collections.singletonList(FILE_ID));
        assertEquals(expected.getClass(), exception.getResults().get(0).getLoadError().getClass());
        assertEquals(expected.getMessage(), exception.getResults().get(0).getLoadError().getMessage());
    }

    public StoragePipelineException loadConcurrentAndCheck(StudyConfiguration studyConfiguration, StoragePipelineResult storagePipelineResult) throws InterruptedException, StorageEngineException, ExecutionException {

        AtomicReference<StoragePipelineException> exception = new AtomicReference<>(null);
        Callable<Integer> load = () -> {
            try {
                runDefaultETL(storagePipelineResult.getTransformResult(), getVariantStorageEngine(), studyConfiguration, new ObjectMap()
                        .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                        .append(VariantStorageEngine.Options.CALCULATE_STATS.key(), false), false, true);
            } catch (StoragePipelineException e) {
                assertEquals(null, exception.getAndSet(e));
                return 1;
            }
            return 0;
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Integer> loadOne = executor.submit(load);
        Future<Integer> loadTwo = executor.submit(load);
        executor.shutdown();

        executor.awaitTermination(1, TimeUnit.MINUTES);

        VariantDBAdaptor dbAdaptor = variantStorageEngine.getDBAdaptor();
        assertTrue(dbAdaptor.count(new Query()).first() > 0);
        assertEquals(1, studyConfiguration.getIndexedFiles().size());
        assertEquals(BatchFileOperation.Status.READY, studyConfiguration.getBatches().get(0).currentStatus());
        assertEquals(MongoDBVariantOptions.STAGE.key(), studyConfiguration.getBatches().get(0).getOperationName());
        assertEquals(BatchFileOperation.Status.READY, studyConfiguration.getBatches().get(1).currentStatus());
        assertEquals(MongoDBVariantOptions.MERGE.key(), studyConfiguration.getBatches().get(1).getOperationName());

        assertEquals(1, loadOne.get() + loadTwo.get());
        return exception.get();
    }

    @Test
    public void stageWhileMerging() throws Exception {
        StudyConfiguration studyConfiguration = newStudyConfiguration();
        StoragePipelineResult storagePipelineResult = runDefaultETL(inputUri, getVariantStorageEngine(), studyConfiguration, new ObjectMap()
                .append(MongoDBVariantOptions.STAGE.key(), true));
        Thread thread = new Thread(() -> {
            try {
                runDefaultETL(storagePipelineResult.getTransformResult(), getVariantStorageEngine(), studyConfiguration, new ObjectMap(),
                        false, true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        StudyConfigurationManager studyConfigurationManager = getVariantStorageEngine().getDBAdaptor().getStudyConfigurationManager();
        int secondFileId = 8;
        try {
            thread.start();
            Thread.sleep(200);

            BatchFileOperation opInProgress = new BatchFileOperation(MongoDBVariantOptions.MERGE.key(), Collections.singletonList(FILE_ID), 0, BatchFileOperation.Type.OTHER);
            opInProgress.addStatus(BatchFileOperation.Status.RUNNING);
            MongoVariantStorageEngineException expected = MongoVariantStorageEngineException.operationInProgressException(opInProgress);
            thrown.expect(StoragePipelineException.class);
            thrown.expectCause(instanceOf(expected.getClass()));
            thrown.expectCause(hasMessage(is(expected.getMessage())));

            runDefaultETL(smallInputUri, getVariantStorageEngine(), studyConfiguration,
                    new ObjectMap(VariantStorageEngine.Options.FILE_ID.key(), secondFileId));
        } finally {
            System.out.println("Interrupt!");
            thread.interrupt();
            System.out.println("Join!");
            thread.join();
            System.out.println("EXIT");

            StudyConfiguration sc = studyConfigurationManager.getStudyConfiguration(studyConfiguration.getStudyId(), null).first();
            // Second file is not staged or merged
            List<BatchFileOperation> ops = sc.getBatches().stream().filter(op -> op.getFileIds().contains(secondFileId)).collect(Collectors.toList());
            assertEquals(0, ops.size());
        }
    }

    /**
     * Try to merge two different files in the same study at the same time.
     */
    @Test
    public void mergeWhileMerging() throws Exception {
        StudyConfiguration studyConfiguration = newStudyConfiguration();
        StoragePipelineResult storagePipelineResult = runDefaultETL(inputUri, getVariantStorageEngine(), studyConfiguration, new ObjectMap()
                .append(MongoDBVariantOptions.STAGE.key(), true));

        int secondFileId = 8;
        StoragePipelineResult storagePipelineResult2 = runDefaultETL(smallInputUri, getVariantStorageEngine(), studyConfiguration,
                new ObjectMap()
                        .append(MongoDBVariantOptions.STAGE.key(), true)
                        .append(VariantStorageEngine.Options.FILE_ID.key(), secondFileId));
        Thread thread = new Thread(() -> {
            try {
                runDefaultETL(storagePipelineResult.getTransformResult(), getVariantStorageEngine(), studyConfiguration, new ObjectMap(),
                        false, true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        StudyConfigurationManager studyConfigurationManager = getVariantStorageEngine().getDBAdaptor().getStudyConfigurationManager();
        try {
            thread.start();
            Thread.sleep(200);

            BatchFileOperation opInProgress = new BatchFileOperation(MongoDBVariantOptions.MERGE.key(), Collections.singletonList(FILE_ID), 0, BatchFileOperation.Type.OTHER);
            opInProgress.addStatus(BatchFileOperation.Status.RUNNING);
            MongoVariantStorageEngineException expected = MongoVariantStorageEngineException.operationInProgressException(opInProgress);
            thrown.expect(StoragePipelineException.class);
            thrown.expectCause(instanceOf(expected.getClass()));
            thrown.expectCause(hasMessage(is(expected.getMessage())));

            runDefaultETL(storagePipelineResult2.getTransformResult(), getVariantStorageEngine(), studyConfiguration,
                    new ObjectMap(MongoDBVariantOptions.STAGE.key(), false).append(VariantStorageEngine.Options.FILE_ID.key(), secondFileId), false, true);
        } finally {
            System.out.println("Interrupt!");
            thread.interrupt();
            System.out.println("Join!");
            thread.join();
            System.out.println("EXIT");

            StudyConfiguration sc = studyConfigurationManager.getStudyConfiguration(studyConfiguration.getStudyId(), null).first();
            // Second file is not staged or merged
            List<BatchFileOperation> ops = sc.getBatches().stream().filter(op -> op.getFileIds().contains(secondFileId)).collect(Collectors.toList());
            assertEquals(1, ops.size());
            assertEquals(MongoDBVariantOptions.STAGE.key(), ops.get(0).getOperationName());
            System.out.println("DONE");
        }
    }

    @Test
    public void mergeResumeFirstFileTest() throws Exception {
        mergeResume(VariantStorageBaseTest.inputUri, createStudyConfiguration(), o -> {});
    }

    @Test
    public void mergeResumeOtherFilesTest2() throws Exception {
        StudyConfiguration studyConfiguration = createStudyConfiguration();
        // Load in study 1
        URI f1 = getResourceUri("1000g_batches/1-500.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz");
        URI f2 = getResourceUri("1000g_batches/501-1000.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz");
        // Load in study 2
        URI f3 = getResourceUri("1000g_batches/1001-1500.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz");
        URI f4 = getResourceUri("1000g_batches/1501-2000.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz");

        // Load in study 1 (with interruptions)
        URI f5 = getResourceUri("1000g_batches/2001-2504.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz");

        int studyId1 = studyConfiguration.getStudyId();
        int studyId2 = studyConfiguration.getStudyId() + 1;
        mergeResume(f5, studyConfiguration, variantStorageManager -> {
            try {
                ObjectMap objectMap = new ObjectMap()
                        .append(VariantStorageEngine.Options.CALCULATE_STATS.key(), false)
                        .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                        .append(VariantStorageEngine.Options.FILE_ID.key(), null);

                runETL(variantStorageManager, f1, outputUri, objectMap
                                .append(VariantStorageEngine.Options.STUDY_ID.key(), studyId1)
                                .append(VariantStorageEngine.Options.STUDY_NAME.key(), studyConfiguration.getStudyName())
                        , true, true, true);
                runETL(variantStorageManager, f2, outputUri, objectMap
                                .append(VariantStorageEngine.Options.STUDY_ID.key(), studyId1)
                                .append(VariantStorageEngine.Options.STUDY_NAME.key(), studyConfiguration.getStudyName())
                        , true, true, true);
                runETL(variantStorageManager, f3, outputUri, objectMap
                                .append(VariantStorageEngine.Options.STUDY_ID.key(), studyId2)
                                .append(VariantStorageEngine.Options.STUDY_NAME.key(), studyConfiguration.getStudyName() + "_2")
                        , true, true, true);
                runETL(variantStorageManager, f4, outputUri, objectMap
                                .append(VariantStorageEngine.Options.STUDY_ID.key(), studyId2)
                                .append(VariantStorageEngine.Options.STUDY_NAME.key(), studyConfiguration.getStudyName() + "_2")
                        , true, true, true);
            } catch (Exception e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
        });
    }

    public void mergeResume(URI file, StudyConfiguration studyConfiguration, Consumer<VariantStorageEngine> setUp) throws Exception {

        setUp.accept(variantStorageEngine);

        StoragePipelineResult storagePipelineResult = runDefaultETL(file, variantStorageEngine, studyConfiguration, new ObjectMap()
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), false));


        int sleep = 0;
        int i = 0;
        Logger logger = LoggerFactory.getLogger("Test");
        AtomicBoolean success = new AtomicBoolean(false);
        final int MAX_EXECUTIONS = 15;
        while (true) {
            final int execution = ++i;
            Thread thread = new Thread(() -> {
                try {
                    runETL(variantStorageEngine, storagePipelineResult.getTransformResult(), outputUri, new ObjectMap()
                            .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                            .append(VariantStorageEngine.Options.CALCULATE_STATS.key(), false)
                            .append(MongoDBVariantOptions.STAGE.key(), true)
                            .append(MongoDBVariantOptions.MERGE_RESUME.key(), execution > 1)
                            .append(MongoDBVariantOptions.MERGE.key(), true), false, false, true);
                    success.set(true);
                } catch (IOException | FileFormatException | StorageEngineException e) {
                    logger.error("Error loading in execution " + execution, e);
                }
            });
            logger.warn("+-----------------------+");
            logger.warn("+   Execution : " + execution);
            if (execution == MAX_EXECUTIONS) {
                logger.warn("+   Last Execution!");
                sleep += TimeUnit.MINUTES.toMillis(5);
            }
            logger.warn("+-----------------------+");
            thread.start();
            sleep += 1000;
            logger.warn("join sleep = " + sleep);
            thread.join(sleep);
            if (thread.isAlive()) {
                thread.interrupt();
                thread.join();
            } else {
                logger.info("Exit. Success = " + success.get());
                break;
            }
            // Finish in less than MAX_EXECUTIONS executions
            assertTrue(execution < MAX_EXECUTIONS);
        }
        // Do at least one interruption
        assertTrue(i > 1);
        assertTrue(success.get());

        VariantMongoDBAdaptor dbAdaptor = (VariantMongoDBAdaptor) variantStorageEngine.getDBAdaptor();
        long count = dbAdaptor.count(null).first();
        System.out.println("count = " + count);
        assertTrue(count > 0);

        long cleanedDocuments = MongoDBVariantStageLoader.cleanStageCollection(dbAdaptor.getStageCollection(), studyConfiguration.getStudyId(), Collections.singletonList(FILE_ID), null, null);
        assertEquals(0, cleanedDocuments);

        studyConfiguration = dbAdaptor.getStudyConfigurationManager().getStudyConfiguration(studyConfiguration.getStudyId(), null).first();
        studyConfiguration.getHeaders().clear();
        System.out.println(studyConfiguration.toString());
        assertTrue(studyConfiguration.getIndexedFiles().contains(FILE_ID));
        assertEquals(BatchFileOperation.Status.READY, studyConfiguration.getBatches().get(1).currentStatus());

        // Insert in a different set of collections the same file
        MongoDBVariantStorageEngine variantStorageManager = getVariantStorageEngine("2");
        setUp.accept(variantStorageManager);

        runDefaultETL(file, variantStorageManager, studyConfiguration, new ObjectMap()
                .append(VariantStorageEngine.Options.CALCULATE_STATS.key(), false)
                .append(VariantStorageEngine.Options.ANNOTATE.key(), false));


        // Iterate over both collections to check that contain the same variants
        MongoDataStore mongoDataStore = getMongoDataStoreManager(DB_NAME).get(DB_NAME);
        MongoDBCollection variantsCollection = mongoDataStore.getCollection(MongoDBVariantOptions.COLLECTION_VARIANTS.defaultValue());
        MongoDBCollection variants2Collection = mongoDataStore.getCollection(MongoDBVariantOptions.COLLECTION_VARIANTS.defaultValue() + "2");
        MongoDBCollection stageCollection = mongoDataStore.getCollection(MongoDBVariantOptions.COLLECTION_STAGE.defaultValue());
        MongoDBCollection stage2Collection = mongoDataStore.getCollection(MongoDBVariantOptions.COLLECTION_STAGE.defaultValue() + "2");

        assertEquals(count, compareCollections(variants2Collection, variantsCollection));
        compareCollections(stage2Collection, stageCollection);
    }

    public MongoDBVariantStorageEngine getVariantStorageEngine(String collectionSufix) throws Exception {
        MongoDBVariantStorageEngine variantStorageEngine = newVariantStorageEngine();
        ObjectMap renameCollections = new ObjectMap()
                .append(MongoDBVariantOptions.COLLECTION_STUDIES.key(), MongoDBVariantOptions.COLLECTION_STUDIES.defaultValue() + collectionSufix)
                .append(MongoDBVariantOptions.COLLECTION_FILES.key(), MongoDBVariantOptions.COLLECTION_FILES.defaultValue() + collectionSufix)
                .append(MongoDBVariantOptions.COLLECTION_STAGE.key(), MongoDBVariantOptions.COLLECTION_STAGE.defaultValue() + collectionSufix)
                .append(MongoDBVariantOptions.COLLECTION_VARIANTS.key(), MongoDBVariantOptions.COLLECTION_VARIANTS.defaultValue() + collectionSufix);

        variantStorageEngine.getOptions().putAll(renameCollections);
        return variantStorageEngine;
    }

    public long compareCollections(MongoDBCollection expectedCollection, MongoDBCollection actualCollection) {
        return compareCollections(expectedCollection, actualCollection, d -> d);
    }

    public long compareCollections(MongoDBCollection expectedCollection, MongoDBCollection actualCollection, Function<Document, Document> map) {
        QueryOptions sort = new QueryOptions(QueryOptions.SORT, Sorts.ascending("_id"));

        System.out.println("Comparing " + expectedCollection + " vs " + actualCollection);
        assertNotEquals(expectedCollection.toString(), actualCollection.toString());
        assertEquals(expectedCollection.count().first(), actualCollection.count().first());

        Iterator<Document> actualIterator = actualCollection.nativeQuery().find(new Document(), sort).iterator();
        Iterator<Document> expectedIterator = expectedCollection.nativeQuery().find(new Document(), sort).iterator();

        long c = 0;
        while (actualIterator.hasNext() && expectedIterator.hasNext()) {
            c++;
            Document actual = map.apply(actualIterator.next());
            Document expected = map.apply(expectedIterator.next());
            assertEquals(expected, actual);
        }
        assertFalse(actualIterator.hasNext());
        assertFalse(expectedIterator.hasNext());
        return c;
    }


    @Test
    public void stageAlreadyStagedFileTest() throws Exception {
        StudyConfiguration studyConfiguration = createStudyConfiguration();

        StoragePipelineResult storagePipelineResult = runDefaultETL(smallInputUri, variantStorageEngine, studyConfiguration, new ObjectMap()
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), false));

        long count = variantStorageEngine.getDBAdaptor().count(null).first();
        assertEquals(0L, count);

        runETL(variantStorageEngine, storagePipelineResult.getTransformResult(), outputUri, new ObjectMap()
                .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), false), false, false, true);
    }


    @Test
    public void stageAlreadyMergedFileTest() throws Exception {
        StudyConfiguration studyConfiguration = createStudyConfiguration();

        StoragePipelineResult storagePipelineResult = runDefaultETL(smallInputUri, variantStorageEngine, studyConfiguration, new ObjectMap()
                .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), true));

        Long count = variantStorageEngine.getDBAdaptor().count(null).first();
        assertTrue(count > 0);

        thrown.expect(StoragePipelineException.class);
        thrown.expectCause(instanceOf(StorageEngineException.class));
        thrown.expectCause(hasMessage(containsString(StorageEngineException.alreadyLoaded(FILE_ID, studyConfiguration).getMessage())));
        runETL(variantStorageEngine, storagePipelineResult.getTransformResult(), outputUri, new ObjectMap()
                .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), false), false, false, true);

    }

    @Test
    public void mergeAlreadyMergedFileTest() throws Exception {
        StudyConfiguration studyConfiguration = createStudyConfiguration();

        StoragePipelineResult storagePipelineResult = runDefaultETL(smallInputUri, variantStorageEngine, studyConfiguration, new ObjectMap()
                .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), true));

        Long count = variantStorageEngine.getDBAdaptor().count(null).first();
        assertTrue(count > 0);

        StorageEngineException expectCause = StorageEngineException.alreadyLoaded(FILE_ID, studyConfiguration);

        thrown.expect(StoragePipelineException.class);
        thrown.expectCause(instanceOf(expectCause.getClass()));
        thrown.expectCause(hasMessage(containsString(expectCause.getMessage())));
        runETL(variantStorageEngine, storagePipelineResult.getTransformResult(), outputUri, new ObjectMap()
                .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), true), false, false, true);

    }

    /**
     * Test merge with other staged files.
     *
     * Study 1:
     *    Staged : file2
     *    Staged+Merged: file1
     * Study 2:
     *    Staged : file3, file4
     *    Staged+Merged: file5
     */
    @Test
    public void mergeWithOtherStages() throws Exception {

        StudyConfiguration studyConfiguration1 = new StudyConfiguration(1, "s1");
        StudyConfiguration studyConfiguration2 = new StudyConfiguration(2, "s2");
        URI file1 = getResourceUri("1000g_batches/1-500.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz");
        URI file2 = getResourceUri("1000g_batches/501-1000.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz");
        URI file3 = getResourceUri("1000g_batches/1001-1500.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz");
        URI file4 = getResourceUri("1000g_batches/1501-2000.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz");
        URI file5 = getResourceUri("1000g_batches/2001-2504.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz");

        // Stage and merge file1
        runDefaultETL(file1, getVariantStorageEngine(), studyConfiguration1, new ObjectMap()
                .append(VariantStorageEngine.Options.FILE_ID.key(), 1)
                .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                .append(VariantStorageEngine.Options.CALCULATE_STATS.key(), false)
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), true));
        runDefaultETL(file2, getVariantStorageEngine(), studyConfiguration1, new ObjectMap()
                .append(VariantStorageEngine.Options.FILE_ID.key(), 2)
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), false));

        runDefaultETL(file3, getVariantStorageEngine(), studyConfiguration2, new ObjectMap()
                .append(VariantStorageEngine.Options.FILE_ID.key(), 3)
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), false));
        runDefaultETL(file4, getVariantStorageEngine(), studyConfiguration2, new ObjectMap()
                .append(VariantStorageEngine.Options.FILE_ID.key(), 4)
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), false));
        // Stage and merge file5
        runDefaultETL(file5, getVariantStorageEngine(), studyConfiguration2, new ObjectMap()
                .append(VariantStorageEngine.Options.FILE_ID.key(), 5)
                .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                .append(VariantStorageEngine.Options.CALCULATE_STATS.key(), false)
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), true));


        StudyConfigurationManager scm = getVariantStorageEngine("2").getDBAdaptor().getStudyConfigurationManager();

        StudyConfiguration newStudyConfiguration1 = new StudyConfiguration(1, "s1");
        newStudyConfiguration1.setSampleIds(studyConfiguration1.getSampleIds());    // Copy the sampleIds from the first load
        scm.updateStudyConfiguration(newStudyConfiguration1, null);

        StudyConfiguration newStudyConfiguration2 = new StudyConfiguration(2, "s2");
        newStudyConfiguration2.setSampleIds(studyConfiguration2.getSampleIds());    // Copy the sampleIds from the first load
        scm.updateStudyConfiguration(newStudyConfiguration2, null);

        runDefaultETL(file1, getVariantStorageEngine("2"), newStudyConfiguration1, new ObjectMap()
                .append(VariantStorageEngine.Options.FILE_ID.key(), 1)
                .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                .append(VariantStorageEngine.Options.CALCULATE_STATS.key(), false)
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), true));
        runDefaultETL(file5, getVariantStorageEngine("2"), newStudyConfiguration2, new ObjectMap()
                .append(VariantStorageEngine.Options.FILE_ID.key(), 5)
                .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                .append(VariantStorageEngine.Options.CALCULATE_STATS.key(), false)
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), true));

        compareCollections(getVariantStorageEngine("2").getDBAdaptor().getVariantsCollection(),
                getVariantStorageEngine().getDBAdaptor().getVariantsCollection());
    }

    @Test
    public void concurrentMerge() throws Exception {
        StudyConfiguration studyConfiguration1 = new StudyConfiguration(1, "s1");
        StudyConfiguration studyConfiguration2 = new StudyConfiguration(2, "s2");


        URI file1 = getResourceUri("1000g_batches/1-500.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz");
        URI file2 = getResourceUri("1000g_batches/501-1000.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz");
        URI file3 = getResourceUri("1000g_batches/1001-1500.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz");
        URI file4 = getResourceUri("1000g_batches/1501-2000.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz");
        URI file5 = getResourceUri("1000g_batches/2001-2504.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz");

        MongoDBVariantStorageEngine variantStorageManager1 = getVariantStorageEngine();
        runDefaultETL(file1, variantStorageManager1, studyConfiguration1, new ObjectMap()
                        .append(VariantStorageEngine.Options.FILE_ID.key(), 1)
                        .append(MongoDBVariantOptions.STAGE.key(), true)
                        .append(MongoDBVariantOptions.MERGE.key(), false)
                , true, true);
        runDefaultETL(file2, variantStorageManager1, studyConfiguration1, new ObjectMap()
                        .append(VariantStorageEngine.Options.FILE_ID.key(), 2)
                        .append(MongoDBVariantOptions.STAGE.key(), true)
                        .append(MongoDBVariantOptions.MERGE.key(), false)
                , true, true);
        StoragePipelineResult storagePipelineResult3 = runDefaultETL(file3, variantStorageManager1, studyConfiguration1, new ObjectMap()
                        .append(VariantStorageEngine.Options.FILE_ID.key(), 3)
                        .append(MongoDBVariantOptions.STAGE.key(), true)
                        .append(MongoDBVariantOptions.MERGE.key(), false)
                , true, true);

        runDefaultETL(file4, variantStorageManager1, studyConfiguration2, new ObjectMap()
                        .append(VariantStorageEngine.Options.FILE_ID.key(), 4)
                        .append(MongoDBVariantOptions.STAGE.key(), true)
                        .append(MongoDBVariantOptions.MERGE.key(), false)
                , true, true);
        StoragePipelineResult storagePipelineResult5 = runDefaultETL(file5, variantStorageManager1, studyConfiguration2, new ObjectMap()
                        .append(VariantStorageEngine.Options.FILE_ID.key(), 5)
                        .append(MongoDBVariantOptions.STAGE.key(), true)
                        .append(MongoDBVariantOptions.MERGE.key(), false)
                , true, true);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future mergeFile3 = executor.submit((Callable) () -> {
            runDefaultETL(storagePipelineResult3.getTransformResult(), newVariantStorageEngine(), studyConfiguration1, new ObjectMap()
                    .append(VariantStorageEngine.Options.FILE_ID.key(), 3)
                    .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                    .append(VariantStorageEngine.Options.CALCULATE_STATS.key(), false)
                    .append(MongoDBVariantOptions.STAGE.key(), true)
                    .append(MongoDBVariantOptions.MERGE.key(), true), false, true);
            return 0;
        });
        Future mergeFile5 = executor.submit((Callable) () -> {
            runDefaultETL(storagePipelineResult5.getTransformResult(), newVariantStorageEngine(), studyConfiguration2, new ObjectMap()
                    .append(VariantStorageEngine.Options.FILE_ID.key(), 5)
                    .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                    .append(VariantStorageEngine.Options.CALCULATE_STATS.key(), false)
                    .append(MongoDBVariantOptions.STAGE.key(), true)
                    .append(MongoDBVariantOptions.MERGE.key(), true), false, true);
            return 0;
        });
        executor.shutdown();
        executor.awaitTermination(4, TimeUnit.MINUTES);
        assertEquals(0, mergeFile3.get());
        assertEquals(0, mergeFile5.get());

        StudyConfigurationManager scm = getVariantStorageEngine("2").getDBAdaptor().getStudyConfigurationManager();

        StudyConfiguration newStudyConfiguration1 = new StudyConfiguration(1, "s1");
        newStudyConfiguration1.setSampleIds(studyConfiguration1.getSampleIds());    // Copy the sampleIds from the first load
        scm.updateStudyConfiguration(newStudyConfiguration1, null);

        StudyConfiguration newStudyConfiguration2 = new StudyConfiguration(2, "s2");
        newStudyConfiguration2.setSampleIds(studyConfiguration2.getSampleIds());    // Copy the sampleIds from the first load
        scm.updateStudyConfiguration(newStudyConfiguration2, null);

        runDefaultETL(file3, getVariantStorageEngine("2"), newStudyConfiguration1, new ObjectMap()
                .append(VariantStorageEngine.Options.FILE_ID.key(), 3)
                .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                .append(VariantStorageEngine.Options.CALCULATE_STATS.key(), false)
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), true));
        runDefaultETL(file5, getVariantStorageEngine("2"), newStudyConfiguration2, new ObjectMap()
                .append(VariantStorageEngine.Options.FILE_ID.key(), 5)
                .append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                .append(VariantStorageEngine.Options.CALCULATE_STATS.key(), false)
                .append(MongoDBVariantOptions.STAGE.key(), true)
                .append(MongoDBVariantOptions.MERGE.key(), true));

        compareCollections(getVariantStorageEngine("2").getDBAdaptor().getVariantsCollection(),
                getVariantStorageEngine().getDBAdaptor().getVariantsCollection(), document -> {
                    // Sort studies, because they can be inserted in a different order
                    if (document.containsKey(DocumentToVariantConverter.STUDIES_FIELD)) {
                        ((List<Document>) document.get(DocumentToVariantConverter.STUDIES_FIELD, List.class))
                                .sort((o1, o2) -> o1.getInteger(STUDYID_FIELD).compareTo(o2.getInteger(STUDYID_FIELD)));
                    }
                    return document;
                });
    }

    @Test
    public void checkCanLoadSampleBatchTest() throws StorageEngineException {
        StudyConfiguration studyConfiguration = createStudyConfiguration();
        MongoDBVariantStoragePipeline.checkCanLoadSampleBatch(studyConfiguration, 1);
        studyConfiguration.getIndexedFiles().add(1);
        MongoDBVariantStoragePipeline.checkCanLoadSampleBatch(studyConfiguration, 2);
        studyConfiguration.getIndexedFiles().add(2);
        MongoDBVariantStoragePipeline.checkCanLoadSampleBatch(studyConfiguration, 3);
        studyConfiguration.getIndexedFiles().add(3);
        MongoDBVariantStoragePipeline.checkCanLoadSampleBatch(studyConfiguration, 4);
        studyConfiguration.getIndexedFiles().add(4);
    }

    @Test
    public void checkCanLoadSampleBatch2Test() throws StorageEngineException {
        StudyConfiguration studyConfiguration = createStudyConfiguration();
        MongoDBVariantStoragePipeline.checkCanLoadSampleBatch(studyConfiguration, 4);
        studyConfiguration.getIndexedFiles().add(4);
        MongoDBVariantStoragePipeline.checkCanLoadSampleBatch(studyConfiguration, 3);
        studyConfiguration.getIndexedFiles().add(3);
        MongoDBVariantStoragePipeline.checkCanLoadSampleBatch(studyConfiguration, 2);
        studyConfiguration.getIndexedFiles().add(2);
        MongoDBVariantStoragePipeline.checkCanLoadSampleBatch(studyConfiguration, 1);
        studyConfiguration.getIndexedFiles().add(1);
    }

    @Test
    public void checkCanLoadSampleBatchFailTest() throws StorageEngineException {
        StudyConfiguration studyConfiguration = createStudyConfiguration();
        studyConfiguration.getIndexedFiles().addAll(Arrays.asList(1, 3, 4));
        thrown.expect(StorageEngineException.class);
        thrown.expectMessage("Another sample batch has been loaded");
        MongoDBVariantStoragePipeline.checkCanLoadSampleBatch(studyConfiguration, 2);
    }

    @Test
    public void checkCanLoadSampleBatchFail2Test() throws StorageEngineException {
        StudyConfiguration studyConfiguration = createStudyConfiguration();
        studyConfiguration.getIndexedFiles().addAll(Arrays.asList(1, 2));
        thrown.expect(StorageEngineException.class);
        thrown.expectMessage("There was some already indexed samples, but not all of them");
        MongoDBVariantStoragePipeline.checkCanLoadSampleBatch(studyConfiguration, 5);
    }

    @SuppressWarnings("unchecked")
    public StudyConfiguration createStudyConfiguration() {
        StudyConfiguration studyConfiguration = new StudyConfiguration(5, "study");
        LinkedHashSet<Integer> batch1 = new LinkedHashSet<>(Arrays.asList(1, 2, 3, 4));
        LinkedHashSet<Integer> batch2 = new LinkedHashSet<>(Arrays.asList(5, 6, 7, 8));
        LinkedHashSet<Integer> batch3 = new LinkedHashSet<>(Arrays.asList(1, 3, 5, 7)); //Mixed batch
        studyConfiguration.getSamplesInFiles().put(1, batch1);
        studyConfiguration.getSamplesInFiles().put(2, batch1);
        studyConfiguration.getSamplesInFiles().put(3, batch2);
        studyConfiguration.getSamplesInFiles().put(4, batch2);
        studyConfiguration.getSamplesInFiles().put(5, batch3);
        studyConfiguration.getSampleIds().putAll(((Map) new ObjectMap()
                .append("s1", 1)
                .append("s2", 2)
                .append("s3", 3)
                .append("s4", 4)
                .append("s5", 5)
                .append("s6", 6)
                .append("s7", 7)
                .append("s8", 8)
        ));
        return studyConfiguration;
    }

    @Test
    @Override
    public void multiIndexPlatinum() throws Exception {
        super.multiIndexPlatinum();
        checkPlatinumDatabase(d -> 17, Collections.singleton("0/0"));
    }

    @Test
    public void multiIndexPlatinumNoUnknownGenotypes() throws Exception {
        super.multiIndexPlatinum(new ObjectMap(MongoDBVariantOptions.DEFAULT_GENOTYPE.key(), DocumentToSamplesConverter.UNKNOWN_GENOTYPE));
        checkPlatinumDatabase(d -> ((List) d.get(FILES_FIELD)).size(), Collections.singleton(DocumentToSamplesConverter.UNKNOWN_GENOTYPE));
    }

    private void checkPlatinumDatabase(Function<Document, Integer> getExpectedSamples, Set<String> defaultGenotypes) throws Exception {
        try (VariantMongoDBAdaptor dbAdaptor = getVariantStorageEngine().getDBAdaptor()) {
            MongoDBCollection variantsCollection = dbAdaptor.getVariantsCollection();

            for (Document document : variantsCollection.nativeQuery().find(new Document(), new QueryOptions())) {
                String id = document.getString("_id");
                List<Document> studies = document.get(DocumentToVariantConverter.STUDIES_FIELD, List.class);
//                List alternates = studies.get(0).get(ALTERNATES_FIELD, List.class);
                if (id.equals("M:     16185:C:A") || id.equals("M:     16184:C:") || id.equals("M:     16184:CC:")) {
                    continue;
                }
                assertEquals(id, 2, studies.size());
                for (Document study : studies) {
                    Document gts = study.get(GENOTYPES_FIELD, Document.class);
                    Set<Integer> samples = new HashSet<>();
                    for (String defaultGenotype : defaultGenotypes) {
                        assertThat(gts.keySet(), not(hasItem(defaultGenotype)));
                    }
                    for (Map.Entry<String, Object> entry : gts.entrySet()) {
                        List<Integer> sampleIds = (List<Integer>) entry.getValue();
                        for (Integer sampleId : sampleIds) {
                            assertFalse(id, samples.contains(sampleId));
                            assertTrue(id, samples.add(sampleId));
                        }
                    }
                    assertEquals("\"" + id + "\" study: " + study.get(STUDYID_FIELD), (int) getExpectedSamples.apply(study), samples.size());
                }

                Document gt1 = studies.get(0).get(GENOTYPES_FIELD, Document.class);
                Document gt2 = studies.get(1).get(GENOTYPES_FIELD, Document.class);
                assertEquals(id, gt1.keySet(), gt2.keySet());
                for (String gt : gt1.keySet()) {
                    // Order is not important. Compare using a set
                    assertEquals(id + ":" + gt, new HashSet<>(gt1.get(gt, List.class)), new HashSet<>(gt2.get(gt, List.class)));
                }

                //Order is very important!
                assertEquals(id, studies.get(0).get(ALTERNATES_FIELD), studies.get(1).get(ALTERNATES_FIELD));

                //Order is not important.
                Map<String, Document> files1 = ((List<Document>) studies.get(0).get(FILES_FIELD))
                        .stream()
                        .collect(Collectors.toMap(d -> d.get(FILEID_FIELD).toString(), Function.identity()));
                Map<String, Document> files2 = ((List<Document>) studies.get(1).get(FILES_FIELD))
                        .stream()
                        .collect(Collectors.toMap(d -> d.get(FILEID_FIELD).toString(), Function.identity()));
                assertEquals(id, studies.get(0).get(FILES_FIELD, List.class).size(), studies.get(1).get(FILES_FIELD, List.class).size());
                assertEquals(id, files1.size(), files2.size());
                for (Map.Entry<String, Document> entry : files1.entrySet()) {
                    assertEquals(id, entry.getValue(), files2.get(entry.getKey()));
                }

            }
        }
    }



    @Test
    @Override
    public void multiRegionBatchIndex() throws Exception {
        super.multiRegionBatchIndex();
        checkLoadedVariants();
    }

    @Test
    @Override
    public void multiRegionIndex() throws Exception {
        super.multiRegionIndex();

        checkLoadedVariants();
    }

    public void checkLoadedVariants() throws Exception {
        try (VariantMongoDBAdaptor dbAdaptor = getVariantStorageEngine().getDBAdaptor()) {
            MongoDBCollection variantsCollection = dbAdaptor.getVariantsCollection();

            for (Document document : variantsCollection.nativeQuery().find(new Document(), new QueryOptions())) {
                String id = document.getString("_id");
                List<Document> studies = document.get(DocumentToVariantConverter.STUDIES_FIELD, List.class);

//                assertEquals(id, 2, studies.size());
                for (Document study : studies) {
                    Document gts = study.get(GENOTYPES_FIELD, Document.class);
                    Set<Integer> samples = new HashSet<>();

                    for (Map.Entry<String, Object> entry : gts.entrySet()) {
                        List<Integer> sampleIds = (List<Integer>) entry.getValue();
                        for (Integer sampleId : sampleIds) {
                            String message = "var: " + id + " Duplicated sampleId " + sampleId + " in gt " + entry.getKey() + " : " + sampleIds;
                            assertFalse(message, samples.contains(sampleId));
                            assertTrue(message, samples.add(sampleId));
                        }
                    }
                }
            }
        }
    }

    @Test
    @Override
    public void indexWithOtherFieldsExcludeGT() throws Exception {
        super.indexWithOtherFieldsExcludeGT();

        try (VariantMongoDBAdaptor dbAdaptor = getVariantStorageEngine().getDBAdaptor()) {
            MongoDBCollection variantsCollection = dbAdaptor.getVariantsCollection();

            for (Document document : variantsCollection.nativeQuery().find(new Document(), new QueryOptions())) {
                assertFalse(((Document) document.get(DocumentToVariantConverter.STUDIES_FIELD, List.class).get(0))
                        .containsKey(GENOTYPES_FIELD));
                System.out.println("dbObject = " + document);
            }
        }

    }
}
