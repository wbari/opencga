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

package org.opencb.opencga.storage.hadoop.variant;

import com.google.common.base.Throwables;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeysPublic;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.io.compress.Compression.Algorithm;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.VariantSource;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.core.results.VariantQueryResult;
import org.opencb.opencga.storage.core.StoragePipelineResult;
import org.opencb.opencga.storage.core.config.DatabaseCredentials;
import org.opencb.opencga.storage.core.config.StorageEngineConfiguration;
import org.opencb.opencga.storage.core.config.StorageEtlConfiguration;
import org.opencb.opencga.storage.core.exceptions.StorageEngineException;
import org.opencb.opencga.storage.core.exceptions.StoragePipelineException;
import org.opencb.opencga.storage.core.exceptions.VariantSearchException;
import org.opencb.opencga.storage.core.metadata.BatchFileOperation;
import org.opencb.opencga.storage.core.metadata.StudyConfiguration;
import org.opencb.opencga.storage.core.metadata.StudyConfigurationManager;
import org.opencb.opencga.storage.core.search.VariantSearchManager;
import org.opencb.opencga.storage.core.utils.CellBaseUtils;
import org.opencb.opencga.storage.core.variant.VariantStorageEngine;
import org.opencb.opencga.storage.core.variant.VariantStoragePipeline;
import org.opencb.opencga.storage.core.variant.adaptors.*;
import org.opencb.opencga.storage.core.variant.annotation.VariantAnnotationManager;
import org.opencb.opencga.storage.core.variant.annotation.annotators.VariantAnnotator;
import org.opencb.opencga.storage.core.variant.io.VariantReaderUtils;
import org.opencb.opencga.storage.core.variant.stats.VariantStatisticsManager;
import org.opencb.opencga.storage.hadoop.auth.HBaseCredentials;
import org.opencb.opencga.storage.hadoop.utils.HBaseManager;
import org.opencb.opencga.storage.hadoop.variant.adaptors.VariantHadoopDBAdaptor;
import org.opencb.opencga.storage.hadoop.variant.annotation.HadoopDefaultVariantAnnotationManager;
import org.opencb.opencga.storage.hadoop.variant.archive.ArchiveDriver;
import org.opencb.opencga.storage.hadoop.variant.executors.ExternalMRExecutor;
import org.opencb.opencga.storage.hadoop.variant.executors.MRExecutor;
import org.opencb.opencga.storage.hadoop.variant.index.VariantTableDeletionDriver;
import org.opencb.opencga.storage.hadoop.variant.metadata.HBaseStudyConfigurationDBAdaptor;
import org.opencb.opencga.storage.hadoop.variant.stats.HadoopDefaultVariantStatisticsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils.checkOperator;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils.isValidParam;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils.splitValue;

/**
 * Created by mh719 on 16/06/15.
 */
public class HadoopVariantStorageEngine extends VariantStorageEngine {
    public static final String STORAGE_ENGINE_ID = "hadoop";

    public static final String HADOOP_BIN = "hadoop.bin";
    public static final String HADOOP_ENV = "hadoop.env";
    public static final String OPENCGA_STORAGE_HADOOP_JAR_WITH_DEPENDENCIES = "opencga.storage.hadoop.jar-with-dependencies";
    public static final String HADOOP_LOAD_ARCHIVE = "hadoop.load.archive";
    public static final String HADOOP_LOAD_VARIANT = "hadoop.load.variant";
    // Resume merge variants if the current status is RUNNING or DONE
    /**
     * @deprecated use {@link Options#RESUME}
     */
    @Deprecated
    public static final String HADOOP_LOAD_VARIANT_RESUME = "hadoop.load.variant.resume";
    // Merge variants operation status. Skip merge and run post-load/post-merge step if status is DONE
    public static final String HADOOP_LOAD_VARIANT_STATUS = "hadoop.load.variant.status";
    //Other files to be loaded from Archive to Variant
    public static final String HADOOP_LOAD_VARIANT_PENDING_FILES = "opencga.storage.hadoop.load.pending.files";
    public static final String OPENCGA_STORAGE_HADOOP_INTERMEDIATE_HDFS_DIRECTORY = "opencga.storage.hadoop.intermediate.hdfs.directory";
    public static final String OPENCGA_STORAGE_HADOOP_VARIANT_HBASE_NAMESPACE = "opencga.storage.hadoop.variant.hbase.namespace";
    public static final String OPENCGA_STORAGE_HADOOP_VARIANT_ARCHIVE_TABLE_PREFIX = "opencga.storage.hadoop.variant.archive.table.prefix";
    public static final String OPENCGA_STORAGE_HADOOP_MAPREDUCE_SCANNER_TIMEOUT = "opencga.storage.hadoop.mapreduce.scanner.timeout";

    public static final String HADOOP_LOAD_ARCHIVE_BATCH_SIZE = "hadoop.load.archive.batch.size";
    public static final String HADOOP_LOAD_VARIANT_BATCH_SIZE = "hadoop.load.variant.batch.size";
    public static final String HADOOP_LOAD_DIRECT = "hadoop.load.direct";
    public static final boolean HADOOP_LOAD_DIRECT_DEFAULT = true;

    public static final String EXTERNAL_MR_EXECUTOR = "opencga.external.mr.executor";
    public static final String ARCHIVE_TABLE_PREFIX = "opencga_study_";


    protected Configuration conf = null;
    protected MRExecutor mrExecutor;
    private HdfsVariantReaderUtils variantReaderUtils;
    private HBaseManager hBaseManager;
    private final AtomicReference<VariantHadoopDBAdaptor> dbAdaptor = new AtomicReference<>();
    private Logger logger = LoggerFactory.getLogger(HadoopVariantStorageEngine.class);

    public HadoopVariantStorageEngine() {
//        variantReaderUtils = new HdfsVariantReaderUtils(conf);
    }

    @Override
    public List<StoragePipelineResult> index(List<URI> inputFiles, URI outdirUri, boolean doExtract, boolean doTransform, boolean doLoad)
            throws StorageEngineException {

        if (inputFiles.size() == 1 || !doLoad) {
            return super.index(inputFiles, outdirUri, doExtract, doTransform, doLoad);
        }

        final boolean doArchive;
        final boolean doMerge;


        if (!getOptions().containsKey(HADOOP_LOAD_ARCHIVE) && !getOptions().containsKey(HADOOP_LOAD_VARIANT)) {
            doArchive = true;
            doMerge = true;
        } else {
            doArchive = getOptions().getBoolean(HADOOP_LOAD_ARCHIVE, false);
            doMerge = getOptions().getBoolean(HADOOP_LOAD_VARIANT, false);
        }

        if (!doArchive && !doMerge) {
            return Collections.emptyList();
        }

        final int nThreadArchive = getOptions().getInt(HADOOP_LOAD_ARCHIVE_BATCH_SIZE, 2);
        ObjectMap extraOptions = new ObjectMap()
                .append(HADOOP_LOAD_ARCHIVE, true)
                .append(HADOOP_LOAD_VARIANT, false);

        final List<StoragePipelineResult> concurrResult = new CopyOnWriteArrayList<>();
        List<VariantStoragePipeline> etlList = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(
                nThreadArchive,
                r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    return t;
                }); // Set Daemon for quick shutdown !!!
        LinkedList<Future<StoragePipelineResult>> futures = new LinkedList<>();
        List<Integer> indexedFiles = new CopyOnWriteArrayList<>();
        for (URI inputFile : inputFiles) {
            //Provide a connected storageETL if load is required.

            VariantStoragePipeline storageETL = newStorageETL(doLoad, new ObjectMap(extraOptions));
            futures.add(executorService.submit(() -> {
                try {
                    Thread.currentThread().setName(Paths.get(inputFile).getFileName().toString());
                    StoragePipelineResult storagePipelineResult = new StoragePipelineResult(inputFile);
                    URI nextUri = inputFile;
                    boolean error = false;
                    if (doTransform) {
                        try {
                            nextUri = transformFile(storageETL, storagePipelineResult, concurrResult, nextUri, outdirUri);

                        } catch (StoragePipelineException ignore) {
                            //Ignore here. Errors are stored in the ETLResult
                            error = true;
                        }
                    }

                    if (doLoad && doArchive && !error) {
                        try {
                            loadFile(storageETL, storagePipelineResult, concurrResult, nextUri, outdirUri);
                        } catch (StoragePipelineException ignore) {
                            //Ignore here. Errors are stored in the ETLResult
                            error = true;
                        }
                    }
                    if (doLoad && !error) {
                        // Read the VariantSource to get the original fileName (it may be different from the
                        // nextUri.getFileName if this is the transformed file)
                        String fileName = storageETL.readVariantSource(nextUri, null).getFileName();
                        // Get latest study configuration from DB, might have been changed since
                        StudyConfiguration studyConfiguration = storageETL.getStudyConfiguration();
                        // Get file ID for the provided file name
                        Integer fileId = studyConfiguration.getFileIds().get(fileName);
                        indexedFiles.add(fileId);
                    }
                    return storagePipelineResult;
                } finally {
                    try {
                        storageETL.close();
                    } catch (StorageEngineException e) {
                        logger.error("Issue closing DB connection ", e);
                    }
                }
            }));
        }

        executorService.shutdown();

        int errors = 0;
        try {
            while (!futures.isEmpty()) {
                executorService.awaitTermination(1, TimeUnit.MINUTES);
                // Check values
                if (futures.peek().isDone() || futures.peek().isCancelled()) {
                    Future<StoragePipelineResult> first = futures.pop();
                    StoragePipelineResult result = first.get(1, TimeUnit.MINUTES);
                    if (result.getTransformError() != null) {
                        //TODO: Handle errors. Retry?
                        errors++;
                        logger.error("Error transforming file " + result.getInput(), result.getTransformError());
                    } else if (result.getLoadError() != null) {
                        //TODO: Handle errors. Retry?
                        errors++;
                        logger.error("Error loading file " + result.getInput(), result.getLoadError());
                    }
                    concurrResult.add(result);
                }
            }
            if (errors > 0) {
                throw new StoragePipelineException("Errors found", concurrResult);
            }

            if (doLoad && doMerge) {
                int batchMergeSize = getOptions().getInt(HADOOP_LOAD_VARIANT_BATCH_SIZE, 10);
                // Overwrite default ID list with user provided IDs
                List<Integer> pendingFiles = indexedFiles;
                if (getOptions().containsKey(HADOOP_LOAD_VARIANT_PENDING_FILES)) {
                    List<Integer> idList = getOptions().getAsIntegerList(HADOOP_LOAD_VARIANT_PENDING_FILES);
                    if (!idList.isEmpty()) {
                        // only if the list is not empty
                        pendingFiles = idList;
                    }
                }

                List<Integer> filesToMerge = new ArrayList<>(batchMergeSize);
                int i = 0;
                for (Iterator<Integer> iterator = pendingFiles.iterator(); iterator.hasNext(); i++) {
                    Integer indexedFile = iterator.next();
                    filesToMerge.add(indexedFile);
                    if (filesToMerge.size() == batchMergeSize || !iterator.hasNext()) {
                        extraOptions = new ObjectMap()
                                .append(HADOOP_LOAD_ARCHIVE, false)
                                .append(HADOOP_LOAD_VARIANT, true)
                                .append(HADOOP_LOAD_VARIANT_PENDING_FILES, filesToMerge);

                        AbstractHadoopVariantStoragePipeline localEtl = newStorageETL(doLoad, extraOptions);

                        int studyId = getOptions().getInt(Options.STUDY_ID.key());
                        localEtl.preLoad(inputFiles.get(i), outdirUri);
                        localEtl.merge(studyId, filesToMerge);
                        localEtl.postLoad(inputFiles.get(i), outdirUri);
                        filesToMerge.clear();
                    }
                }

                annotateLoadedFiles(outdirUri, inputFiles, concurrResult, getOptions());
                calculateStatsForLoadedFiles(outdirUri, inputFiles, concurrResult, getOptions());

            }
        } catch (InterruptedException e) {
            Thread.interrupted();
            throw new StoragePipelineException("Interrupted!", e, concurrResult);
        } catch (ExecutionException e) {
            throw new StoragePipelineException("Execution exception!", e, concurrResult);
        } catch (TimeoutException e) {
            throw new StoragePipelineException("Timeout Exception", e, concurrResult);
        } finally {
            if (!executorService.isShutdown()) {
                try {
                    executorService.shutdownNow();
                } catch (Exception e) {
                    logger.error("Problems shutting executer service down", e);
                }
            }
        }
        return concurrResult;
    }

    @Override
    public AbstractHadoopVariantStoragePipeline newStoragePipeline(boolean connected) throws StorageEngineException {
        return newStorageETL(connected, null);
    }

    @Override
    protected VariantAnnotationManager newVariantAnnotationManager(VariantAnnotator annotator) throws StorageEngineException {
        return new HadoopDefaultVariantAnnotationManager(annotator, getDBAdaptor());
    }

    @Override
    public VariantStatisticsManager newVariantStatisticsManager() throws StorageEngineException {
        return new HadoopDefaultVariantStatisticsManager(getDBAdaptor());
    }

    public AbstractHadoopVariantStoragePipeline newStorageETL(boolean connected, Map<? extends String, ?> extraOptions)
            throws StorageEngineException {
        ObjectMap options = new ObjectMap(configuration.getStorageEngine(STORAGE_ENGINE_ID).getVariant().getOptions());
        if (extraOptions != null) {
            options.putAll(extraOptions);
        }
        boolean directLoad = options.getBoolean(HADOOP_LOAD_DIRECT, HADOOP_LOAD_DIRECT_DEFAULT);
        VariantHadoopDBAdaptor dbAdaptor = connected ? getDBAdaptor() : null;
        Configuration hadoopConfiguration = null == dbAdaptor ? null : dbAdaptor.getConfiguration();
        hadoopConfiguration = hadoopConfiguration == null ? getHadoopConfiguration(options) : hadoopConfiguration;
        hadoopConfiguration.setIfUnset(ArchiveDriver.CONFIG_ARCHIVE_TABLE_COMPRESSION, Algorithm.SNAPPY.getName());

        HBaseCredentials archiveCredentials = buildCredentials(getArchiveTableName(options.getInt(Options.STUDY_ID.key()), options));

        AbstractHadoopVariantStoragePipeline storageETL = null;
        if (directLoad) {
            storageETL = new HadoopDirectVariantStoragePipeline(configuration, storageEngineId, dbAdaptor, getMRExecutor(options),
                    hadoopConfiguration, archiveCredentials, getVariantReaderUtils(hadoopConfiguration), options);
        } else {
            storageETL = new HadoopVariantStoragePipeline(configuration, storageEngineId, dbAdaptor, getMRExecutor(options),
                    hadoopConfiguration, archiveCredentials, getVariantReaderUtils(hadoopConfiguration), options);
        }
        return storageETL;
    }

    public HdfsVariantReaderUtils getVariantReaderUtils() {
        return getVariantReaderUtils(conf);
    }

    private HdfsVariantReaderUtils getVariantReaderUtils(Configuration config) {
        if (null == variantReaderUtils) {
            variantReaderUtils = new HdfsVariantReaderUtils(config);
        } else if (this.variantReaderUtils.conf == null && config != null) {
            variantReaderUtils = new HdfsVariantReaderUtils(config);
        }
        return variantReaderUtils;
    }

    @Override
    public void dropFile(String study, int fileId) throws StorageEngineException {
        ObjectMap options = configuration.getStorageEngine(STORAGE_ENGINE_ID).getVariant().getOptions();
        // Use ETL as helper class
        AbstractHadoopVariantStoragePipeline etl = newStoragePipeline(true);
        VariantDBAdaptor dbAdaptor = etl.getDBAdaptor();
        StudyConfigurationManager scm = dbAdaptor.getStudyConfigurationManager();
        List<Integer> fileList = Collections.singletonList(fileId);
        final int studyId = scm.getStudyId(study, null);

        // Pre delete
        scm.lockAndUpdate(studyId, sc -> {
            if (!sc.getIndexedFiles().contains(fileId)) {
                throw StorageEngineException.unableToExecute("File not indexed.", fileId, sc);
            }
            boolean resume = options.getBoolean(Options.RESUME.key(), Options.RESUME.defaultValue())
                    || options.getBoolean(HadoopVariantStorageEngine.HADOOP_LOAD_VARIANT_RESUME, false);
            BatchFileOperation operation =
                    etl.addBatchOperation(sc, VariantTableDeletionDriver.JOB_OPERATION_NAME, fileList, resume,
                            BatchFileOperation.Type.REMOVE);
            options.put(AbstractAnalysisTableDriver.TIMESTAMP, operation.getTimestamp());
            return sc;
        });

        // Delete
        Thread hook = etl.newShutdownHook(VariantTableDeletionDriver.JOB_OPERATION_NAME, fileList);
        try {
            Runtime.getRuntime().addShutdownHook(hook);

            String archiveTable = getArchiveTableName(studyId, options);
            HBaseCredentials variantsTable = getDbCredentials();
            String hadoopRoute = options.getString(HADOOP_BIN, "hadoop");
            String jar = AbstractHadoopVariantStoragePipeline.getJarWithDependencies(options);

            Class execClass = VariantTableDeletionDriver.class;
            String args = VariantTableDeletionDriver.buildCommandLineArgs(variantsTable.toString(), archiveTable,
                    variantsTable.getTable(), studyId, fileList, options);
            String executable = hadoopRoute + " jar " + jar + ' ' + execClass.getName();

            long startTime = System.currentTimeMillis();
            logger.info("------------------------------------------------------");
            logger.info("Remove file ID {} in archive '{}' and analysis table '{}'", fileId, archiveTable, variantsTable.getTable());
            logger.debug(executable + " " + args);
            logger.info("------------------------------------------------------");
            int exitValue = getMRExecutor(options).run(executable, args);
            logger.info("------------------------------------------------------");
            logger.info("Exit value: {}", exitValue);
            logger.info("Total time: {}s", (System.currentTimeMillis() - startTime) / 1000.0);
            if (exitValue != 0) {
                throw new StorageEngineException("Error removing fileId " + fileId + " from tables ");
            }

            // Post Delete
            // If everything went fine, remove file column from Archive table and from studyconfig
            scm.lockAndUpdate(studyId, sc -> {
                scm.setStatus(sc, BatchFileOperation.Status.READY,
                        VariantTableDeletionDriver.JOB_OPERATION_NAME, fileList);
                sc.getIndexedFiles().remove(fileId);
                return sc;
            });

        } catch (Exception e) {
            scm.atomicSetStatus(studyId, BatchFileOperation.Status.ERROR,
                    VariantTableDeletionDriver.JOB_OPERATION_NAME, fileList);
            throw e;
        } finally {
            Runtime.getRuntime().removeShutdownHook(hook);
        }
    }

    @Override
    public void dropStudy(String studyName) throws StorageEngineException {
        throw new UnsupportedOperationException("Unimplemented");
    }

    private HBaseCredentials getDbCredentials() throws StorageEngineException {
        String table = getVariantTableName();
        return buildCredentials(table);
    }

    @Override
    public VariantHadoopDBAdaptor getDBAdaptor() throws StorageEngineException {
        if (dbAdaptor.get() == null) {
            synchronized (dbAdaptor) {
                if (dbAdaptor.get() == null) {
                    HBaseCredentials credentials = getDbCredentials();
                    try {
                        StorageEngineConfiguration storageEngine = this.configuration.getStorageEngine(STORAGE_ENGINE_ID);
                        Configuration configuration = getHadoopConfiguration(storageEngine.getVariant().getOptions());
                        configuration = VariantHadoopDBAdaptor.getHbaseConfiguration(configuration, credentials);
                        dbAdaptor.set(new VariantHadoopDBAdaptor(getHBaseManager(configuration), credentials,
                                this.configuration, configuration, getCellBaseUtils()));
                    } catch (IOException e) {
                        throw new StorageEngineException("Error creating DB Adapter", e);
                    }
                }
            }
        }
        return dbAdaptor.get();
    }

    private synchronized HBaseManager getHBaseManager(Configuration configuration) {
        if (hBaseManager == null) {
            hBaseManager = new HBaseManager(configuration);
        }
        return hBaseManager;
    }


    @Override
    public VariantQueryResult<Variant> get(Query query, QueryOptions options) throws StorageEngineException {
        if (options == null) {
            options = QueryOptions.empty();
        }
        Set<VariantField> returnedFields = VariantField.getReturnedFields(options);
        // TODO: Use CacheManager ?
        if (options.getBoolean(VariantSearchManager.SUMMARY)
                || !query.containsKey(VariantQueryParam.GENOTYPE.key())
                && !query.containsKey(VariantQueryParam.SAMPLES.key())
//                && !query.containsKey(VariantQueryParam.FILES.key())
//                && !query.containsKey(VariantQueryParam.FILTER.key())
                && !returnedFields.contains(VariantField.STUDIES_SAMPLES_DATA)
//                && !returnedFields.contains(VariantField.STUDIES_FILES)
                && searchActiveAndAlive()) {
            try {
                return getVariantSearchManager().query(dbName, query, options);
            } catch (IOException | VariantSearchException e) {
                throw Throwables.propagate(e);
            }
        } else {
            VariantDBAdaptor dbAdaptor = getDBAdaptor();
            StudyConfigurationManager studyConfigurationManager = dbAdaptor.getStudyConfigurationManager();
            query = parseQuery(query, studyConfigurationManager);
            setDefaultTimeout(options);
            return dbAdaptor.get(query, options);
        }
    }

    @Override
    public VariantDBIterator iterator(Query query, QueryOptions options) throws StorageEngineException {
        Set<VariantField> returnedFields = VariantField.getReturnedFields(options);
        if (options.getBoolean(VariantSearchManager.SUMMARY)
                || !query.containsKey(VariantQueryParam.GENOTYPE.key())
                && !query.containsKey(VariantQueryParam.SAMPLES.key())
//                && !query.containsKey(VariantQueryParam.FILES.key())
//                && !query.containsKey(VariantQueryParam.FILTER.key())
                && !returnedFields.contains(VariantField.STUDIES_SAMPLES_DATA)
//                && !returnedFields.contains(VariantField.STUDIES_FILES)
                && searchActiveAndAlive()) {
            try {
                return getVariantSearchManager().iterator(dbName, query, options);
            } catch (IOException | VariantSearchException e) {
                throw Throwables.propagate(e);
            }
        } else {
            VariantDBAdaptor dbAdaptor = getDBAdaptor();
            StudyConfigurationManager studyConfigurationManager = dbAdaptor.getStudyConfigurationManager();
            query = parseQuery(query, studyConfigurationManager);
            return dbAdaptor.iterator(query, options);
        }
    }

    @Override
    public QueryResult<Long> count(Query query) throws StorageEngineException {
        if (query.containsKey(VariantQueryParam.GENOTYPE.key())
                || query.containsKey(VariantQueryParam.SAMPLES.key())
                || !searchActiveAndAlive()) {
            VariantDBAdaptor dbAdaptor = getDBAdaptor();
            StudyConfigurationManager studyConfigurationManager = dbAdaptor.getStudyConfigurationManager();
            query = parseQuery(query, studyConfigurationManager);
            return dbAdaptor.count(query);
        } else {
            try {
                StopWatch watch = StopWatch.createStarted();
                long count = getVariantSearchManager().query(dbName, query, new QueryOptions(QueryOptions.LIMIT, 1)).getNumTotalResults();
                int time = (int) watch.getTime(TimeUnit.MILLISECONDS);
                return new QueryResult<>("count", time, 1, 1, "", "", Collections.singletonList(count));
            } catch (IOException | VariantSearchException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    public Query parseQuery(Query originalQuery, StudyConfigurationManager studyConfigurationManager) throws StorageEngineException {
        // Copy input query! Do not modify original query!
        Query query = originalQuery == null ? new Query() : new Query(originalQuery);
        List<String> studyNames = studyConfigurationManager.getStudyNames(QueryOptions.empty());
        CellBaseUtils cellBaseUtils = getCellBaseUtils();

        if (isValidParam(query, VariantQueryParam.STUDIES) && studyNames.size() == 1) {
            query.remove(VariantQueryParam.STUDIES.key());
        }

        if (isValidParam(query, VariantQueryParam.ANNOT_GO)) {
            String value = query.getString(VariantQueryParam.ANNOT_GO.key());
            // Check if comma separated of semi colon separated (AND or OR)
            VariantQueryUtils.QueryOperation queryOperation = checkOperator(value);
            // Split by comma or semi colon
            List<String> goValues = splitValue(value, queryOperation);

            if (queryOperation == VariantQueryUtils.QueryOperation.AND) {
                throw VariantQueryException.malformedParam(VariantQueryParam.ANNOT_GO, value, "Unimplemented AND operator");
            }
            query.remove(VariantQueryParam.ANNOT_GO.key());
            List<String> genes = new ArrayList<>(query.getAsStringList(VariantQueryParam.GENE.key()));
            Set<String> genesByGo = cellBaseUtils.getGenesByGo(goValues);
            if (genesByGo.isEmpty()) {
                genes.add("none");
            } else {
                genes.addAll(genesByGo);
            }
            query.put(VariantQueryParam.GENE.key(), genes);
        }

        if (isValidParam(query, VariantQueryParam.ANNOT_EXPRESSION)) {
            String value = query.getString(VariantQueryParam.ANNOT_EXPRESSION.key());
            // Check if comma separated of semi colon separated (AND or OR)
            VariantQueryUtils.QueryOperation queryOperation = checkOperator(value);
            // Split by comma or semi colon
            List<String> expressionValues = splitValue(value, queryOperation);

            if (queryOperation == VariantQueryUtils.QueryOperation.AND) {
                throw VariantQueryException.malformedParam(VariantQueryParam.ANNOT_EXPRESSION, value, "Unimplemented AND operator");
            }
            query.remove(VariantQueryParam.ANNOT_EXPRESSION.key());
            List<String> genes = new ArrayList<>(query.getAsStringList(VariantQueryParam.GENE.key()));
            Set<String> genesByExpression = cellBaseUtils.getGenesByExpression(expressionValues);
            if (genesByExpression.isEmpty()) {
                genes.add("none");
            } else {
                genes.addAll(genesByExpression);
            }
            query.put(VariantQueryParam.GENE.key(), genes);
        }
        return query;
    }

    @Override
    public void close() throws IOException {
        super.close();
        if (hBaseManager != null) {
            hBaseManager.close();
            hBaseManager = null;
        }
        if (dbAdaptor.get() != null) {
            dbAdaptor.get().close();
            dbAdaptor.set(null);
        }
    }

    private HBaseCredentials buildCredentials(String table) throws StorageEngineException {
        StorageEtlConfiguration vStore = configuration.getStorageEngine(STORAGE_ENGINE_ID).getVariant();

        DatabaseCredentials db = vStore.getDatabase();
        String user = db.getUser();
        String pass = db.getPassword();
        List<String> hostList = db.getHosts();
        if (hostList != null && hostList.size() > 1) {
            throw new IllegalStateException("Expect only one server name");
        }
        String target = hostList != null && !hostList.isEmpty() ? hostList.get(0) : null;
        try {
            String server;
            Integer port;
            String zookeeperPath;
            if (target == null || target.isEmpty()) {
                Configuration conf = getHadoopConfiguration(getOptions());
                server = conf.get(HConstants.ZOOKEEPER_QUORUM);
                port = 60000;
                zookeeperPath = conf.get(HConstants.ZOOKEEPER_ZNODE_PARENT);
            } else {
                URI uri;
                try {
                    uri = new URI(target);
                } catch (URISyntaxException e) {
                    try {
                        uri = new URI("hbase://" + target);
                    } catch (URISyntaxException e1) {
                        throw e;
                    }
                }
                server = uri.getHost();
                port = uri.getPort() > 0 ? uri.getPort() : 60000;
                // If just an IP or host name is provided, the URI parser will return empty host, and the content as "path". Avoid that
                if (server == null) {
                    server = uri.getPath();
                    zookeeperPath = null;
                } else {
                    zookeeperPath = uri.getPath();
                }
            }
            HBaseCredentials credentials;
            if (!StringUtils.isBlank(zookeeperPath)) {
                credentials = new HBaseCredentials(server, table, user, pass, port, zookeeperPath);
            } else {
                credentials = new HBaseCredentials(server, table, user, pass, port);
            }
            return credentials;
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }


    @Override
    public StudyConfigurationManager getStudyConfigurationManager() throws StorageEngineException {
        ObjectMap options = getOptions();
        HBaseCredentials dbCredentials = getDbCredentials();
        Configuration configuration = VariantHadoopDBAdaptor.getHbaseConfiguration(getHadoopConfiguration(options), dbCredentials);
        return new StudyConfigurationManager(new HBaseStudyConfigurationDBAdaptor(dbCredentials.getTable(), configuration, options));
    }

    private Configuration getHadoopConfiguration(ObjectMap options) throws StorageEngineException {
        Configuration conf = this.conf == null ? HBaseConfiguration.create() : this.conf;
        // This is the only key needed to connect to HDFS:
        //   CommonConfigurationKeysPublic.FS_DEFAULT_NAME_KEY = fs.defaultFS
        //

        if (conf.get(CommonConfigurationKeysPublic.FS_DEFAULT_NAME_KEY) == null) {
            throw new StorageEngineException("Missing configuration parameter \""
                    + CommonConfigurationKeysPublic.FS_DEFAULT_NAME_KEY + "\"");
        }

        options.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .forEach(entry -> conf.set(entry.getKey(), options.getString(entry.getKey())));
        return conf;
    }

    private MRExecutor getMRExecutor(ObjectMap options) {
        if (options.containsKey(EXTERNAL_MR_EXECUTOR)) {
            Class<? extends MRExecutor> aClass;
            if (options.get(EXTERNAL_MR_EXECUTOR) instanceof Class) {
                aClass = options.get(EXTERNAL_MR_EXECUTOR, Class.class).asSubclass(MRExecutor.class);
            } else {
                try {
                    aClass = Class.forName(options.getString(EXTERNAL_MR_EXECUTOR)).asSubclass(MRExecutor.class);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
            try {
                return aClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        } else if (mrExecutor == null) {
            return new ExternalMRExecutor(options);
        } else {
            return mrExecutor;
        }
    }

    /**
     * Get the archive table name given a StudyId.
     *
     * @param studyId Numerical study identifier
     * @return Table name
     */
    public String getArchiveTableName(int studyId) {
        String prefix = getOptions().getString(OPENCGA_STORAGE_HADOOP_VARIANT_ARCHIVE_TABLE_PREFIX);
        if (StringUtils.isEmpty(prefix)) {
            prefix = ARCHIVE_TABLE_PREFIX;
        }
        return buildTableName(getOptions().getString(OPENCGA_STORAGE_HADOOP_VARIANT_HBASE_NAMESPACE, ""),
                prefix, studyId);
    }

    /**
     * Get the archive table name given a StudyId.
     *
     * @param studyId Numerical study identifier
     * @param conf Hadoop configuration with the OpenCGA values.
     * @return Table name
     */
    public static String getArchiveTableName(int studyId, Configuration conf) {
        String prefix = conf.get(OPENCGA_STORAGE_HADOOP_VARIANT_ARCHIVE_TABLE_PREFIX);
        if (StringUtils.isEmpty(prefix)) {
            prefix = ARCHIVE_TABLE_PREFIX;
        }
        return buildTableName(conf.get(OPENCGA_STORAGE_HADOOP_VARIANT_HBASE_NAMESPACE, ""),
                prefix, studyId);
    }

    /**
     * Get the archive table name given a StudyId.
     *
     * @param studyId Numerical study identifier
     * @param options Options
     * @return Table name
     */
    public static String getArchiveTableName(int studyId, ObjectMap options) {
        String prefix = options.getString(OPENCGA_STORAGE_HADOOP_VARIANT_ARCHIVE_TABLE_PREFIX);
        if (StringUtils.isEmpty(prefix)) {
            prefix = ARCHIVE_TABLE_PREFIX;
        }
        return buildTableName(options.getString(OPENCGA_STORAGE_HADOOP_VARIANT_HBASE_NAMESPACE, ""),
                prefix, studyId);
    }

    public String getVariantTableName() {
        return getVariantTableName(dbName, getOptions());
    }

    public static String getVariantTableName(String table, ObjectMap options) {
        return buildTableName(options.getString(OPENCGA_STORAGE_HADOOP_VARIANT_HBASE_NAMESPACE, ""), "", table);
    }

    public static String getVariantTableName(String table, Configuration conf) {
        return buildTableName(conf.get(OPENCGA_STORAGE_HADOOP_VARIANT_HBASE_NAMESPACE, ""), "", table);
    }

    protected static String buildTableName(String namespace, String prefix, int studyId) {
        return buildTableName(namespace, prefix, String.valueOf(studyId));
    }

    protected static String buildTableName(String namespace, String prefix, String tableName) {
        StringBuilder sb = new StringBuilder();

        if (StringUtils.isNotEmpty(namespace)) {
            if (tableName.contains(":")) {
                if (!tableName.startsWith(namespace + ":")) {
                    throw new IllegalArgumentException("Wrong namespace : '" + tableName + "'."
                            + " Namespace mismatches with the read from configuration:" + namespace);
                } else {
                    tableName = tableName.substring(tableName.indexOf(':') + 1); // Remove '<namespace>:'
                }
            }
            sb.append(namespace).append(":");
        }
        if (StringUtils.isNotEmpty(prefix)) {
            sb.append(prefix);
            if (!prefix.endsWith("_")) {
                sb.append("_");
            }
        }
        sb.append(tableName);

        String fullyQualified = sb.toString();
        TableName.isLegalFullyQualifiedTableName(fullyQualified.getBytes());
        return fullyQualified;
    }

    public VariantSource readVariantSource(URI input) throws StorageEngineException {
        return getVariantReaderUtils(null).readVariantSource(input);
    }

    private static class HdfsVariantReaderUtils extends VariantReaderUtils {
        private final Configuration conf;

        HdfsVariantReaderUtils(Configuration conf) {
            this.conf = conf;
        }

        @Override
        public VariantSource readVariantSource(URI input) throws StorageEngineException {
            VariantSource source;

            if (input.getScheme() == null || input.getScheme().startsWith("file")) {
                if (input.getPath().contains("variants.proto")) {
                    return VariantReaderUtils.readVariantSource(Paths.get(input.getPath().replace("variants.proto", "file.json")), null);
                } else {
                    return VariantReaderUtils.readVariantSource(Paths.get(input.getPath()), null);
                }
            }

            Path metaPath = new Path(VariantReaderUtils.getMetaFromTransformedFile(input.toString()));
            FileSystem fs = null;
            try {
                fs = FileSystem.get(conf);
            } catch (IOException e) {
                throw new StorageEngineException("Unable to get FileSystem", e);
            }
            try (
                    InputStream inputStream = new GZIPInputStream(fs.open(metaPath))
            ) {
                source = VariantReaderUtils.readVariantSource(inputStream);
            } catch (IOException e) {
                throw new StorageEngineException("Unable to read VariantSource", e);
            }
            return source;
        }
    }
}
