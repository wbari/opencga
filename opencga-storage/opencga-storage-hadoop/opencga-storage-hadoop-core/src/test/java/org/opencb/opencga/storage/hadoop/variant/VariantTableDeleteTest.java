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

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.VariantSource;
import org.opencb.biodata.models.variant.avro.VariantAnnotation;
import org.opencb.opencga.storage.core.metadata.StudyConfiguration;
import org.opencb.opencga.storage.core.variant.VariantStorageBaseTest;
import org.opencb.opencga.storage.hadoop.variant.adaptors.VariantHadoopDBAdaptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;

/**
 * @author Matthias Haimel mh719+git@cam.ac.uk
 *
 */
public class VariantTableDeleteTest extends VariantStorageBaseTest implements HadoopVariantStorageTest {

    @ClassRule
    public static ExternalResource externalResource = new HadoopExternalResource();

    @Before
    public void setUp() throws Exception {
        clearDB(DB_NAME);
        clearDB(getVariantStorageEngine().getArchiveTableName(STUDY_ID));
    }

    private VariantSource loadFile(String resource, StudyConfiguration studyConfiguration, Map<? extends String, ?> map) throws Exception {
        return VariantHbaseTestUtils.loadFile(getVariantStorageEngine(), DB_NAME, outputUri, resource, studyConfiguration, map);
    }

    private void removeFile(String file, StudyConfiguration studyConfiguration, Map<? extends String, ?> map) throws Exception {
        Integer fileId = studyConfiguration.getFileIds().get(file);
        System.out.printf("Remove File ID %s for %s", fileId, file);
        VariantHbaseTestUtils.removeFile(getVariantStorageEngine(), DB_NAME, fileId, studyConfiguration, map);
    }

    @Test
    public void dropFileTest() throws Exception {
        StudyConfiguration studyConfiguration = VariantStorageBaseTest.newStudyConfiguration();
        System.out.println("studyConfiguration = " + studyConfiguration);
        String studyName = studyConfiguration.getStudyName();

        loadFile("s1.genome.vcf", studyConfiguration, Collections.emptyMap());
        Map<String, Variant> variants = buildVariantsIdx();
        assertFalse(variants.containsKey("1:10014:A:G"));

        assertThat(variants.keySet(), hasItem("1:10013:T:C"));
        assertEquals("0/1", variants.get("1:10013:T:C").getStudy(studyName).getSampleData("s1", "GT"));
        assertEquals(null, variants.get("1:10013:T:C").getStudy(studyName).getSampleData("s2", "GT"));

        loadFile("s2.genome.vcf", studyConfiguration, Collections.emptyMap());
        variants = buildVariantsIdx();
        assertThat(variants.keySet(), hasItem("1:10014:A:G"));
        assertEquals("0/2", variants.get("1:10014:A:G").getStudy(studyName).getSampleData("s1", "GT"));
        assertEquals("0/1", variants.get("1:10014:A:G").getStudy(studyName).getSampleData("s2", "GT"));

        assertThat(variants.keySet(), hasItem("1:10013:T:C"));
        assertEquals("0/1", variants.get("1:10013:T:C").getStudy(studyName).getSampleData("s1", "GT"));
        assertEquals("0/0", variants.get("1:10013:T:C").getStudy(studyName).getSampleData("s2", "GT"));

        VariantHbaseTestUtils.printVariantsFromVariantsTable(getVariantStorageEngine().getDBAdaptor());
        // delete
        removeFile("s2.genome.vcf", studyConfiguration, Collections.emptyMap());
        variants = buildVariantsIdx();
        if (variants.containsKey("1:10014:A:G")) {
            System.out.println(variants.get("1:10014:A:G").getImpl());
        }
        assertThat(variants.keySet(), not(hasItem("1:10014:A:G")));

        assertThat(variants.keySet(), hasItem("1:10013:T:C"));
        assertEquals("0/1", variants.get("1:10013:T:C").getStudy(studyName).getSampleData("s1", "GT"));
        assertEquals(null, variants.get("1:10013:T:C").getStudy(studyName).getSampleData("s2", "GT"));

        System.out.println("studyConfiguration = " + studyConfiguration);
        System.out.println("studyConfiguration.getAttributes().toJson() = " + studyConfiguration.getAttributes().toJson());
    }

    @Test
    public void dropSingleFileTest() throws Exception {
        StudyConfiguration studyConfiguration = VariantStorageBaseTest.newStudyConfiguration();
        System.out.println("studyConfiguration = " + studyConfiguration);
        String studyName = studyConfiguration.getStudyName();

        loadFile("s1.genome.vcf", studyConfiguration, Collections.emptyMap());
        Map<String, Variant> variants = buildVariantsIdx();

        assertFalse(variants.containsKey("1:10014:A:G"));
        assertTrue(variants.containsKey("1:10013:T:C"));
        assertEquals("0/1", variants.get("1:10013:T:C").getStudy(studyName).getSampleData("s1", "GT"));

        VariantHbaseTestUtils.printVariantsFromVariantsTable(getVariantStorageEngine().getDBAdaptor());
        // delete
        removeFile("s1.genome.vcf", studyConfiguration, Collections.emptyMap());

        VariantHbaseTestUtils.printVariantsFromVariantsTable(getVariantStorageEngine().getDBAdaptor());

        System.out.println("studyConfiguration = " + studyConfiguration);
        System.out.println("studyConfiguration.getAttributes().toJson() = " + studyConfiguration.getAttributes().toJson());

        variants = buildVariantsIdx();
        assertEquals(0, variants.size());
        assertEquals(0, studyConfiguration.getIndexedFiles().size());
    }

    private Map<String, Variant> buildVariantsIdx() throws Exception {
        VariantHadoopDBAdaptor dbAdaptor = getVariantStorageEngine().getDBAdaptor();
        Map<String, Variant> variants = new HashMap<>();
        System.out.println("Build Variant map");
        for (Variant variant : dbAdaptor) {
            String v = variant.toString();
            assertFalse(variants.containsKey(v));
            variants.put(v, variant);
            VariantAnnotation a = variant.getAnnotation();
            variant.setAnnotation(null);
            System.out.println(variant.toJson());
            variant.setAnnotation(a);
        }
        System.out.println("End. size : " + variants.size());
        return variants;
    }

}
