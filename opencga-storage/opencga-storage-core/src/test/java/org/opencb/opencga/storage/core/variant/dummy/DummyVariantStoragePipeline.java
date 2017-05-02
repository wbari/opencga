package org.opencb.opencga.storage.core.variant.dummy;

import org.opencb.biodata.models.variant.VariantSource;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.opencga.storage.core.config.StorageConfiguration;
import org.opencb.opencga.storage.core.exceptions.StorageEngineException;
import org.opencb.opencga.storage.core.metadata.BatchFileOperation;
import org.opencb.opencga.storage.core.metadata.StudyConfiguration;
import org.opencb.opencga.storage.core.variant.VariantStoragePipeline;
import org.opencb.opencga.storage.core.variant.VariantStorageEngine;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor;
import org.opencb.opencga.storage.core.variant.io.VariantReaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Created on 28/11/16.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class DummyVariantStoragePipeline extends VariantStoragePipeline {

    public static final String VARIANTS_LOAD_FAIL = "dummy.variants.load.fail";
    private final Logger logger = LoggerFactory.getLogger(DummyVariantStoragePipeline.class);

    public DummyVariantStoragePipeline(StorageConfiguration configuration, String storageEngineId, VariantDBAdaptor dbAdaptor, VariantReaderUtils variantReaderUtils) {
        super(configuration, storageEngineId, dbAdaptor, variantReaderUtils);
    }

    public void init(ObjectMap options) {
        getOptions().clear();
        getOptions().putAll(options);
    }

    @Override
    protected void securePreLoad(StudyConfiguration studyConfiguration, VariantSource source) throws StorageEngineException {
        super.securePreLoad(studyConfiguration, source);

        List<Integer> fileIds = getOptions().getAsIntegerList(VariantStorageEngine.Options.FILE_ID.key());
        BatchFileOperation op = new BatchFileOperation("load", fileIds, 1, BatchFileOperation.Type.LOAD);
        op.addStatus(BatchFileOperation.Status.RUNNING);
        studyConfiguration.getBatches().add(op);
    }

    @Override
    public URI load(URI input) throws IOException, StorageEngineException {
        logger.info("Loading file " + input);
        List<Integer> fileIds = getOptions().getAsIntegerList(VariantStorageEngine.Options.FILE_ID.key());
        if (getOptions().getBoolean(VARIANTS_LOAD_FAIL)) {
            getStudyConfigurationManager().atomicSetStatus(getStudyId(), BatchFileOperation.Status.ERROR, "load", fileIds);
        } else {
            getStudyConfigurationManager().atomicSetStatus(getStudyId(), BatchFileOperation.Status.DONE, "load", fileIds);
        }
        return input;
    }

    @Override
    public URI postLoad(URI input, URI output) throws StorageEngineException {
        logger.info("Post load file " + input);
        return super.postLoad(input, output);
    }

    @Override
    public void securePostLoad(List<Integer> fileIds, StudyConfiguration studyConfiguration) throws StorageEngineException {
        super.securePostLoad(fileIds, studyConfiguration);
        BatchFileOperation.Status status = dbAdaptor.getStudyConfigurationManager()
                .setStatus(studyConfiguration, BatchFileOperation.Status.READY, "load", fileIds);
        if (status != BatchFileOperation.Status.DONE) {
            logger.warn("Unexpected status " + status);
        }
    }

    @Override
    protected void checkLoadedVariants(URI input, int fileId, StudyConfiguration studyConfiguration) throws StorageEngineException {

    }
}
