package org.opencb.opencga.storage.core.variant;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.opencb.biodata.formats.io.FileFormatException;
import org.opencb.datastore.core.ObjectMap;
import org.opencb.opencga.storage.core.StorageManagerException;
import org.opencb.opencga.storage.core.StudyConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Created by jacobo on 31/05/15.
 */
@Ignore
public abstract class VariantStorageManagerTestUtils {


    public static final String VCF_TEST_FILE_NAME = "10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz";
    public static final int NUM_VARIANTS = 9792;
    public static final int STUDY_ID = 5;
    public static final String STUDY_NAME = "1000g";
    protected static URI inputUri;
    protected static URI outputUri;
    protected VariantStorageManager variantStorageManager;
    public static Logger logger;
    protected static Properties storageProperties;

    @BeforeClass
    public static void _beforeClass() throws Exception {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "debug");
        Path rootDir = getTmpRootDir();
        Path inputPath = rootDir.resolve(VCF_TEST_FILE_NAME);
        Files.copy(VariantStorageManagerTest.class.getClassLoader().getResourceAsStream(VCF_TEST_FILE_NAME), inputPath, StandardCopyOption.REPLACE_EXISTING);
        inputUri = inputPath.toUri();
        outputUri = rootDir.toUri();
        logger = LoggerFactory.getLogger(VariantStorageManagerTest.class);

        storageProperties = new Properties();
        storageProperties.load(VariantStorageManagerTestUtils.class.getClassLoader().getResourceAsStream("storage.properties"));
    }

    protected static Path getTmpRootDir() throws IOException {
        Path rootDir = Paths.get("tmp", "VariantStorageManagerTest");
        Files.createDirectories(rootDir);
        return rootDir;
    }

    @Before
    public void before() throws Exception {
        clearDB();
    }

    @Before
    public final void _before() throws Exception {
        variantStorageManager = getVariantStorageManager();
    }

    protected abstract VariantStorageManager getVariantStorageManager() throws Exception;
    protected abstract void clearDB() throws Exception;


    /* ---------------------------------------------------- */
    /* Static methods to run a simple ETL to index Variants */
    /* ---------------------------------------------------- */

    /**
     * Simple class to store the output URIs generated by the ETL
     */
    public static class ETLResult {

        public URI extractResult;
        public URI preTransformResult;
        public URI transformResult;
        public URI postTransformResult;
        public URI preLoadResult;
        public URI loadResult;
        //        public URI postLoadResult;
    }

    public static ETLResult runETL(VariantStorageManager variantStorageManager, ObjectMap params)
            throws IOException, FileFormatException, StorageManagerException {
        return runETL(variantStorageManager, params, true, true, true);
    }

    public static ETLResult runETL(VariantStorageManager variantStorageManager, ObjectMap params,
                                   boolean doExtract,
                                   boolean doTransform,
                                   boolean doLoad)
            throws IOException, FileFormatException, StorageManagerException {
        return runETL(variantStorageManager, inputUri, outputUri, params, params, params, params, params, params, params, doExtract, doTransform, doLoad);
    }

    public static ETLResult runDefaultETL(VariantStorageManager variantStorageManager, StudyConfiguration studyConfiguration)
            throws URISyntaxException, IOException, FileFormatException, StorageManagerException {
        return runDefaultETL(inputUri, variantStorageManager, studyConfiguration);
    }

    public static ETLResult runDefaultETL(URI inputUri, VariantStorageManager variantStorageManager, StudyConfiguration studyConfiguration)
            throws URISyntaxException, IOException, FileFormatException, StorageManagerException {

        ObjectMap extractParams = new ObjectMap();

        ObjectMap preTransformParams = new ObjectMap();
        preTransformParams.put(VariantStorageManager.STUDY_CONFIGURATION, studyConfiguration);
        ObjectMap transformParams = new ObjectMap();
        transformParams.put(VariantStorageManager.STUDY_CONFIGURATION, studyConfiguration);
        transformParams.put(VariantStorageManager.INCLUDE_SAMPLES, true);
        transformParams.put(VariantStorageManager.FILE_ID, 6);
        ObjectMap postTransformParams = new ObjectMap();

        ObjectMap preLoadParams = new ObjectMap();
        preLoadParams.put(VariantStorageManager.STUDY_CONFIGURATION, studyConfiguration);
        ObjectMap loadParams = new ObjectMap();
        loadParams.put(VariantStorageManager.STUDY_CONFIGURATION, studyConfiguration);
        loadParams.put(VariantStorageManager.INCLUDE_SAMPLES, true);
        loadParams.put(VariantStorageManager.FILE_ID, 6);

        ObjectMap postLoadParams = new ObjectMap();
        postLoadParams.put(VariantStorageManager.FILE_ID, 6);
        postLoadParams.put(VariantStorageManager.STUDY_CONFIGURATION, studyConfiguration);
        postLoadParams.put(VariantStorageManager.ANNOTATOR_PROPERTIES, storageProperties);
        postLoadParams.put(VariantStorageManager.ANNOTATE, true);
        postLoadParams.put(VariantStorageManager.CALCULATE_STATS, true);
        postLoadParams.put(VariantStorageManager.STUDY_CONFIGURATION, studyConfiguration);

        return runETL(variantStorageManager, inputUri, outputUri, extractParams, preTransformParams, transformParams, postTransformParams, preLoadParams, loadParams, postLoadParams, true, true, true);
    }

    public static ETLResult runETL(VariantStorageManager variantStorageManager, URI inputUri, URI outputUri,
                                   ObjectMap extractParams,
                                   ObjectMap preTransformParams, ObjectMap transformParams, ObjectMap postTransformParams,
                                   ObjectMap preLoadParams, ObjectMap loadParams, ObjectMap postLoadParams,
                                   boolean doExtract,
                                   boolean doTransform,
                                   boolean doLoad)
            throws IOException, FileFormatException, StorageManagerException {
        ETLResult etlResult = new ETLResult();

        if (doExtract) {
            inputUri = variantStorageManager.extract(inputUri, outputUri, extractParams);
            etlResult.extractResult = inputUri;
        }

        if (doTransform) {
            inputUri = variantStorageManager.preTransform(inputUri, preTransformParams);
            etlResult.preTransformResult = inputUri;
            Assert.assertTrue("Intermediary file " + inputUri + " does not exist", Paths.get(inputUri).toFile().exists());

            inputUri = variantStorageManager.transform(inputUri, null, outputUri, transformParams);
            etlResult.transformResult = inputUri;
            Assert.assertTrue("Intermediary file " + inputUri + " does not exist", Paths.get(inputUri).toFile().exists());

            inputUri = variantStorageManager.postTransform(inputUri, postTransformParams);
            etlResult.postTransformResult = inputUri;
            Assert.assertTrue("Intermediary file " + inputUri + " does not exist", Paths.get(inputUri).toFile().exists());
        }

        if (doLoad) {
            inputUri = variantStorageManager.preLoad(inputUri, outputUri, preLoadParams);
            etlResult.preLoadResult = inputUri;
            Assert.assertTrue("Intermediary file " + inputUri + " does not exist", Paths.get(inputUri).toFile().exists());

            inputUri = variantStorageManager.load(inputUri, loadParams);
            etlResult.loadResult = inputUri;
            Assert.assertTrue("Intermediary file " + inputUri + " does not exist", Paths.get(inputUri).toFile().exists());

            variantStorageManager.postLoad(inputUri, outputUri, postLoadParams);
        }
        return etlResult;
    }

    protected static StudyConfiguration newStudyConfiguration() {
        return new StudyConfiguration(STUDY_ID, STUDY_NAME);
    }


}
