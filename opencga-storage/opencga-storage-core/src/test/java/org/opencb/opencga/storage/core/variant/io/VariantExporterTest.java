package org.opencb.opencga.storage.core.variant.io;

import org.junit.Before;
import org.junit.Test;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.storage.core.config.StorageConfiguration;
import org.opencb.opencga.storage.core.variant.VariantStorageBaseTest;
import org.opencb.opencga.storage.core.variant.VariantStorageEngine;
import org.opencb.opencga.storage.core.variant.dummy.DummyStudyConfigurationAdaptor;
import org.opencb.opencga.storage.core.variant.dummy.DummyVariantStorageEngine;
import org.opencb.opencga.storage.core.variant.io.VariantWriterFactory.VariantOutputFormat;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertTrue;

/**
 * Created on 06/12/16.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class VariantExporterTest extends VariantStorageBaseTest {

    @Before
    public void setUp() throws Exception {
        runDefaultETL(smallInputUri, variantStorageEngine, newStudyConfiguration());
    }

    @Override
    public VariantStorageEngine getVariantStorageEngine() throws Exception {
        try (InputStream is = DummyVariantStorageEngine.class.getClassLoader().getResourceAsStream("storage-configuration.yml")) {
            StorageConfiguration storageConfiguration = StorageConfiguration.load(is);
            DummyVariantStorageEngine storageManager = new DummyVariantStorageEngine();
            storageManager.setConfiguration(storageConfiguration, DummyVariantStorageEngine.STORAGE_ENGINE_ID);
            return storageManager;
        }
    }

    @Override
    public void clearDB(String dbName) throws Exception {
        DummyStudyConfigurationAdaptor.clear();
    }

    @Test
    public void exportStudyTest() throws Exception {
        variantStorageEngine.exportData(null, VariantOutputFormat.VCF, new Query(), new QueryOptions());
        // It may happen that the VcfExporter closes the StandardOutput.
        // Check System.out is not closed
        System.out.println(getClass().getSimpleName() + ": System out not closed!");
    }

    @Test
    public void exportStudyJsonTest() throws Exception {
        URI output = newOutputUri().resolve("variant.json.gz");
        variantStorageEngine.exportData(output, VariantOutputFormat.JSON_GZ, new Query(), new QueryOptions());

        System.out.println("output = " + output);
        assertTrue(Paths.get(output).toFile().exists());
        assertTrue(Paths.get(output.getPath() + VariantExporter.METADATA_FILE_EXTENSION).toFile().exists());

        // Check gzip format
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(output.getPath()))))) {
            int i = 0;
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    break;
                }
                System.out.println("[" + i++ + "]: " + line);
            }
        }
    }

}
