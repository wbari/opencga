package org.opencb.opencga.storage.mongodb.variant.load;

import org.junit.Before;
import org.junit.Test;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.storage.core.metadata.StudyConfiguration;
import org.opencb.opencga.storage.core.variant.VariantStorageBaseTest;
import org.opencb.opencga.storage.core.variant.adaptors.VariantField;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam;
import org.opencb.opencga.storage.core.variant.io.VariantWriterFactory.VariantOutputFormat;
import org.opencb.opencga.storage.mongodb.variant.MongoDBVariantStorageTest;

import java.net.URI;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Created on 08/12/16.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class MongoVariantImporterTest extends VariantStorageBaseTest implements MongoDBVariantStorageTest {


    private StudyConfiguration studyConfiguration;

    @Before
    public void setUp() throws Exception {
        studyConfiguration = newStudyConfiguration();
        runDefaultETL(smallInputUri, variantStorageEngine, studyConfiguration);
    }


    @Test
    public void testSimpleImport() throws Exception {
        URI outputFile = newOutputUri().resolve("export.avro");

        System.out.println("outputFile = " + outputFile);
        variantStorageEngine.exportData(outputFile, VariantOutputFormat.AVRO, new Query(), new QueryOptions());

        clearDB(DB_NAME);

        variantStorageEngine.importData(outputFile, new ObjectMap());

        for (Variant variant : variantStorageEngine.getDBAdaptor()) {
            assertEquals(4, variant.getStudies().get(0).getSamplesData().size());
        }
    }


    @Test
    public void testImportSomeSamples() throws Exception {
        URI outputFile = newOutputUri().resolve("export.avro");

        System.out.println("outputFile = " + outputFile);
        List<String> samples = new LinkedList<>(studyConfiguration.getSampleIds().keySet()).subList(1, 3);
        Set<String> samplesSet = new HashSet<>(samples);
        Query query = new Query(VariantQueryParam.RETURNED_SAMPLES.key(), samples);
        variantStorageEngine.exportData(outputFile, VariantOutputFormat.AVRO, query, new QueryOptions());

        clearDB(DB_NAME);

        variantStorageEngine.importData(outputFile, new ObjectMap());

        for (Variant variant : variantStorageEngine.getDBAdaptor()) {
            assertEquals(2, variant.getStudies().get(0).getSamplesData().size());
            assertEquals(samplesSet, variant.getStudies().get(0).getSamplesName());
        }
    }

    @Test
    public void testImportExcludeSamples() throws Exception {
        URI outputFile = newOutputUri().resolve("export.avro");

        System.out.println("outputFile = " + outputFile);
        Query query = new Query();
        QueryOptions queryOptions = new QueryOptions(QueryOptions.EXCLUDE, VariantField.STUDIES_SAMPLES_DATA.toString());
        variantStorageEngine.exportData(outputFile, VariantOutputFormat.AVRO, query, queryOptions);

        clearDB(DB_NAME);

        variantStorageEngine.importData(outputFile, new ObjectMap());

        for (Variant variant : variantStorageEngine.getDBAdaptor()) {
            assertEquals(0, variant.getStudies().get(0).getSamplesData().size());
        }
    }

    @Test
    public void testImportEmptySamples() throws Exception {
        URI outputFile = newOutputUri().resolve("export.avro");

        System.out.println("outputFile = " + outputFile);
        Query query = new Query(VariantQueryParam.RETURNED_SAMPLES.key(), ".");
        QueryOptions queryOptions = new QueryOptions();
        variantStorageEngine.exportData(outputFile, VariantOutputFormat.AVRO, query, queryOptions);

        clearDB(DB_NAME);

        variantStorageEngine.importData(outputFile, new ObjectMap());

        for (Variant variant : variantStorageEngine.getDBAdaptor()) {
            assertEquals(0, variant.getStudies().get(0).getSamplesData().size());
        }
    }

}
