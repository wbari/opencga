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

/**
 * 
 */
package org.opencb.opencga.storage.hadoop.variant;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.phoenix.schema.IllegalDataException;
import org.apache.phoenix.schema.types.*;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.VariantSource;
import org.opencb.biodata.models.variant.protobuf.VcfSliceProtos;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.core.common.TimeUtils;
import org.opencb.opencga.storage.core.StoragePipelineResult;
import org.opencb.opencga.storage.core.metadata.StudyConfiguration;
import org.opencb.opencga.storage.core.metadata.FileStudyConfigurationAdaptor;
import org.opencb.opencga.storage.core.variant.VariantStorageEngine;
import org.opencb.opencga.storage.core.variant.VariantStorageBaseTest;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBIterator;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam;
import org.opencb.opencga.storage.core.variant.annotation.VariantAnnotationManager;
import org.opencb.opencga.storage.hadoop.utils.HBaseManager;
import org.opencb.opencga.storage.hadoop.variant.adaptors.VariantHadoopDBAdaptor;
import org.opencb.opencga.storage.hadoop.variant.archive.ArchiveTableHelper;
import org.opencb.opencga.storage.hadoop.variant.archive.VariantHadoopArchiveDBIterator;
import org.opencb.opencga.storage.hadoop.variant.index.VariantTableStudyRow;
import org.opencb.opencga.storage.hadoop.variant.index.phoenix.PhoenixHelper;
import org.opencb.opencga.storage.hadoop.variant.index.phoenix.VariantPhoenixHelper;
import org.opencb.opencga.storage.hadoop.variant.index.phoenix.VariantPhoenixKeyFactory;
import org.opencb.opencga.storage.hadoop.variant.models.protobuf.VariantTableStudyRowsProto;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import static org.opencb.opencga.storage.core.variant.VariantStorageBaseTest.getTmpRootDir;
import static org.opencb.opencga.storage.hadoop.variant.HadoopVariantStorageTest.configuration;

/**
 *  Utility class for VariantStorage hadoop tests
 *
 *  @author Matthias Haimel mh719+git@cam.ac.uk
 */
public class VariantHbaseTestUtils {

    public static VariantHadoopDBAdaptor printVariantsFromArchiveTable(VariantHadoopDBAdaptor dbAdaptor,
                                                                       StudyConfiguration studyConfiguration) throws Exception {
        return printVariantsFromArchiveTable(dbAdaptor, studyConfiguration, System.out);
    }

    public static VariantHadoopDBAdaptor printVariantsFromArchiveTable(VariantHadoopDBAdaptor dbAdaptor,
                                                                       StudyConfiguration studyConfiguration, Path output) throws Exception {
        if (output.toFile().isDirectory()) {
            String archiveTableName = HadoopVariantStorageEngine.getArchiveTableName(studyConfiguration.getStudyId(), dbAdaptor.getConfiguration());
            output = output.resolve("archive._V." + archiveTableName + "." + TimeUtils.getTimeMillis() + ".txt");
        }

        try (FileOutputStream out = new FileOutputStream(output.toFile())) {
            return printVariantsFromArchiveTable(dbAdaptor, studyConfiguration, new PrintStream(out));
        }
    }

    public static VariantHadoopDBAdaptor printVariantsFromArchiveTable(VariantHadoopDBAdaptor dbAdaptor,
                                                                       StudyConfiguration studyConfiguration, PrintStream out) throws Exception {
        GenomeHelper helper = dbAdaptor.getGenomeHelper();
        dbAdaptor.getHBaseManager().act(HadoopVariantStorageEngine.getArchiveTableName(studyConfiguration.getStudyId(), dbAdaptor.getConfiguration()), table -> {
            for (Result result : table.getScanner(helper.getColumnFamily())) {
                GenomeHelper.getVariantColumns(result.rawCells()).stream()
                        .filter(c -> Bytes.startsWith(CellUtil.cloneFamily(c), helper.getColumnFamily()))
                        .forEach(c -> {
                    try {
                        byte[] value = CellUtil.cloneValue(c);
                        if (value != null) {
                            out.println(VariantTableStudyRowsProto.parseFrom(value));
                        }
                    } catch (Exception e) {
                        out.println("e.getMessage() = " + e.getMessage());
                    }
                });

            }
            return 0;
        });
        return dbAdaptor;
    }

    public static void printVariantsFromVariantsTable(VariantHadoopDBAdaptor dbAdaptor) throws IOException {
        printVariantsFromVariantsTable(dbAdaptor, getTmpRootDir());
    }

    public static void printVariantsFromVariantsTable(VariantHadoopDBAdaptor dbAdaptor, Path dir) throws IOException {
        String tableName = HadoopVariantStorageEngine.getVariantTableName(VariantStorageBaseTest.DB_NAME, dbAdaptor.getConfiguration());
        HBaseManager hm = new HBaseManager(configuration.get());
        if (!hm.tableExists(tableName)) {
            System.out.println("Table " + tableName + " does not exist!");
            return;
        }
        System.out.println("Query from HBase : " + tableName);
        GenomeHelper genomeHelper = dbAdaptor.getGenomeHelper();
        Path outputFile;
        if (dir.toFile().isDirectory()) {
            outputFile = dir.resolve("variant." + tableName + "." + TimeUtils.getTimeMillis() + ".txt");
        } else {
            outputFile = dir;
        }
        System.out.println("Variant table file = " + outputFile);
        PrintStream os = new PrintStream(new FileOutputStream(outputFile.toFile()));
        int numVariants = hm.act(tableName, table -> {
            int num = 0;
            ResultScanner resultScanner = table.getScanner(genomeHelper.getColumnFamily());
            for (Result result : resultScanner) {
                if (Bytes.toString(result.getRow()).startsWith(genomeHelper.getMetaRowKeyString())) {
                    continue;
                }
                Variant variant = VariantPhoenixKeyFactory.extractVariantFromVariantRowKey(result.getRow());
                os.println("Variant = " + variant);
                for (Map.Entry<byte[], byte[]> entry : result.getFamilyMap(genomeHelper.getColumnFamily()).entrySet()) {
                    String key = Bytes.toString(entry.getKey());
                    PhoenixHelper.Column column = VariantPhoenixHelper.VariantColumn.getColumn(key);
                    if (column != null) {
                        os.println("\t" + key + " = " + length(entry.getValue()) + ", "
                                + column.getPDataType().toObject(entry.getValue()));
                    } else if (key.endsWith(VariantPhoenixHelper.STATS_PROTOBUF_SUFIX)
                            || key.endsWith("_" + VariantTableStudyRow.FILTER_OTHER)
                            || key.endsWith("_" + VariantTableStudyRow.COMPLEX)) {
                        os.println("\t" + key + " = " + length(entry.getValue()) + ", " + Arrays.toString(entry.getValue()));
                    } else if (key.startsWith(VariantPhoenixHelper.POPULATION_FREQUENCY_PREFIX)) {
                        os.println("\t" + key + " = " + length(entry.getValue()) + ", " + PFloatArray.INSTANCE.toObject(entry.getValue()));
                    } else if (key.endsWith("_" + VariantTableStudyRow.HET_REF)
                            || key.endsWith("_" + VariantTableStudyRow.HOM_VAR)
                            || key.endsWith("_" + VariantTableStudyRow.NOCALL)
                            || key.endsWith("_" + VariantTableStudyRow.OTHER)) {
                        os.println("\t" + key + " = " +  PUnsignedIntArray.INSTANCE.toObject(entry.getValue()));
                    } else if (key.endsWith("_" + VariantTableStudyRow.HOM_REF)
                            || key.endsWith("_" + VariantTableStudyRow.CALL_CNT)
                            || key.endsWith("_" + VariantTableStudyRow.PASS_CNT)) {
                        os.println("\t" + key + " = " + PUnsignedInt.INSTANCE.toObject(entry.getValue()));
                    } else if (key.endsWith(VariantPhoenixHelper.MAF_SUFIX)
                            || key.endsWith(VariantPhoenixHelper.MGF_SUFIX)) {
                        os.println("\t" + key + " = " + PFloat.INSTANCE.toObject(entry.getValue()));
                    } else if (entry.getValue().length == 4) {
                        Object o = null;
                        try {
                            o = PUnsignedInt.INSTANCE.toObject(entry.getValue());
                        } catch (IllegalDataException ignore) {}
                        os.println("\t" + key + " = "
                                + PInteger.INSTANCE.toObject(entry.getValue()) + " , "
                                + o + " , "
                                + PFloat.INSTANCE.toObject(entry.getValue()) + " , ");
                    } else {
                        os.println("\t" + key + " ~ " + length(entry.getValue()) + ", "
                                + Bytes.toString(entry.getValue()));
                    }

                }
                os.println("--------------------");
                if (!variant.getChromosome().equals(genomeHelper.getMetaRowKeyString())) {
                    num++;
                }
            }
            os.close();
            resultScanner.close();
            return num;
        });
    }

    private static String length(byte[] array) {
        return "(" + array.length + " B)";
    }

    private static void printVariantsFromDBAdaptor(VariantHadoopDBAdaptor dbAdaptor, Path dir) throws IOException {
        String tableName = HadoopVariantStorageEngine.getVariantTableName(VariantStorageBaseTest.DB_NAME, dbAdaptor.getConfiguration());
        Path outputFile;
        if (dir.toFile().isDirectory()) {
            outputFile = dir.resolve("variant." + tableName + "." + TimeUtils.getTimeMillis() + ".json");
        } else {
            outputFile = dir;
        }
        System.out.println("Variant table file = " + outputFile);
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile.toFile()))) {
            PrintStream out = new PrintStream(os);
            printVariantsFromDBAdaptor(dbAdaptor, out);
        }
    }

    private static void printVariantsFromDBAdaptor(VariantHadoopDBAdaptor dbAdaptor, PrintStream out) {
        VariantDBIterator iterator = dbAdaptor.iterator(new Query(), new QueryOptions("simpleGenotypes", true));
        while (iterator.hasNext()) {
            Variant variant = iterator.next();
            out.println(variant.toJson());
        }
    }

    public static void printArchiveTable(StudyConfiguration studyConfiguration, VariantHadoopDBAdaptor dbAdaptor, Path outDir) throws Exception {
        String archiveTableName = HadoopVariantStorageEngine.getArchiveTableName(studyConfiguration.getStudyId(), dbAdaptor.getConfiguration());
        for (Integer fileId : studyConfiguration.getIndexedFiles()) {
            try (OutputStream os = new FileOutputStream(outDir.resolve("archive." + fileId + "." + archiveTableName + "." + TimeUtils.getTimeMillis() + ".txt").toFile())) {
                printArchiveTable(dbAdaptor, studyConfiguration, fileId, os);
            }
        }
    }

    public static void printArchiveTable(VariantHadoopDBAdaptor dbAdaptor,
                                                           StudyConfiguration studyConfiguration, int fileId, OutputStream os) throws Exception {
        VariantHadoopArchiveDBIterator archive = (VariantHadoopArchiveDBIterator) dbAdaptor.iterator(
                new Query()
                        .append(VariantQueryParam.STUDIES.key(), studyConfiguration.getStudyId())
                        .append(VariantQueryParam.FILES.key(), fileId),
                new QueryOptions("archive", true));

        ArchiveTableHelper archiveHelper = dbAdaptor.getArchiveHelper(studyConfiguration.getStudyId(), fileId);
        for (Result result : archive.getResultScanner()) {
            byte[] value = result.getValue(archiveHelper.getColumnFamily(), archiveHelper.getColumn());
            VcfSliceProtos.VcfSlice vcfSlice = VcfSliceProtos.VcfSlice.parseFrom(value);
            os.write(vcfSlice.toString().getBytes());
        }

    }

    public static void printTables(Configuration conf) throws IOException {
        System.out.println("Print tables!");
        System.out.println("conf.get(HConstants.ZOOKEEPER_QUORUM) = " + conf.get(HConstants.ZOOKEEPER_QUORUM));
        try (Connection con = ConnectionFactory.createConnection(conf)) {
            HBaseManager.act(con, "all", (table, admin) -> {
                for (NamespaceDescriptor ns : admin.listNamespaceDescriptors()) {
                    System.out.println(ns.getName());
                    for (TableName tableName : admin.listTableNamesByNamespace(ns.getName())) {
                        System.out.println("      " + tableName);
                    }
                    System.out.println("---");
                }
                return null;
            });
        }
    }

    public static void printVariants(StudyConfiguration studyConfiguration, VariantHadoopDBAdaptor dbAdaptor, URI outDir) throws Exception {
        printVariants(studyConfiguration, dbAdaptor, Paths.get(outDir));
    }

    public static void printVariants(StudyConfiguration studyConfiguration, VariantHadoopDBAdaptor dbAdaptor, Path outDir) throws Exception {
        FileStudyConfigurationAdaptor.write(studyConfiguration, outDir.resolve("study_configuration.json"));
        printVariantsFromArchiveTable(dbAdaptor, studyConfiguration, outDir);
        printVariantsFromVariantsTable(dbAdaptor, outDir);
        printVariantsFromDBAdaptor(dbAdaptor, outDir);
        printArchiveTable(studyConfiguration, dbAdaptor, outDir);
    }

    public static void removeFile(HadoopVariantStorageEngine variantStorageManager, String dbName, int fileId,
                                  StudyConfiguration studyConfiguration, Map<? extends String, ?> otherParams) throws Exception {
        ObjectMap params = new ObjectMap(VariantStorageEngine.Options.STUDY_CONFIGURATION.key(), studyConfiguration)
                .append(VariantStorageEngine.Options.STUDY_ID.key(), studyConfiguration.getStudyId())
                .append(VariantStorageEngine.Options.DB_NAME.key(), dbName);
        if (otherParams != null) {
            params.putAll(otherParams);
        }

        variantStorageManager.getConfiguration().getStorageEngine(variantStorageManager.getStorageEngineId()).getVariant().getOptions()
                .putAll(params);
        variantStorageManager.dropFile(studyConfiguration.getStudyName(), fileId);
        studyConfiguration.copy(
                variantStorageManager
                        .getDBAdaptor()
                        .getStudyConfigurationManager()
                        .getStudyConfiguration(studyConfiguration.getStudyId(), null)
                        .first());
//        return variantStorageManager.readVariantSource(etlResult.getTransformResult(), new ObjectMap());
    }

    public static VariantSource loadFile(HadoopVariantStorageEngine variantStorageManager, String dbName, URI outputUri,
                                         String resourceName, int fileId, StudyConfiguration studyConfiguration, Map<? extends String, ?> otherParams, boolean doTransform, boolean loadArchive, boolean loadVariant) throws Exception {
        URI fileInputUri = VariantStorageBaseTest.getResourceUri(resourceName);

        ObjectMap params = new ObjectMap(VariantStorageEngine.Options.TRANSFORM_FORMAT.key(), "proto")
                .append(VariantStorageEngine.Options.STUDY_CONFIGURATION.key(), studyConfiguration)
                .append(VariantStorageEngine.Options.STUDY_ID.key(), studyConfiguration.getStudyId())
                .append(VariantStorageEngine.Options.STUDY_NAME.key(), studyConfiguration.getStudyName())
                .append(VariantStorageEngine.Options.DB_NAME.key(), dbName).append(VariantStorageEngine.Options.ANNOTATE.key(), false)
                .append(VariantAnnotationManager.SPECIES, "hsapiens").append(VariantAnnotationManager.ASSEMBLY, "GRch37")
                .append(VariantStorageEngine.Options.CALCULATE_STATS.key(), false)
                .append(HadoopVariantStorageEngine.HADOOP_LOAD_DIRECT, true)
                .append(HadoopVariantStorageEngine.HADOOP_LOAD_ARCHIVE, loadArchive)
                .append(HadoopVariantStorageEngine.HADOOP_LOAD_VARIANT, loadVariant);

        if (otherParams != null) {
            params.putAll(otherParams);
        }
        if (fileId > 0) {
            params.append(VariantStorageEngine.Options.FILE_ID.key(), fileId);
        }
        StoragePipelineResult etlResult = VariantStorageBaseTest.runETL(variantStorageManager, fileInputUri, outputUri, params, doTransform, doTransform, true);
        StudyConfiguration updatedStudyConfiguration = variantStorageManager.getDBAdaptor().getStudyConfigurationManager().getStudyConfiguration(studyConfiguration.getStudyId(), null).first();
        studyConfiguration.copy(updatedStudyConfiguration);

        return variantStorageManager.readVariantSource(doTransform ? etlResult.getTransformResult() : etlResult.getInput());
    }

    public static VariantSource loadFile(HadoopVariantStorageEngine variantStorageManager, String dbName, URI outputUri,
                                         String resourceName, int fileId, StudyConfiguration studyConfiguration) throws Exception {
        return loadFile(variantStorageManager, dbName, outputUri, resourceName, fileId, studyConfiguration, null, true, true, true);
    }

    public static VariantSource loadFile(HadoopVariantStorageEngine variantStorageManager, String dbName, URI outputUri,
                                         String resourceName, StudyConfiguration studyConfiguration, Map<? extends String, ?> otherParams) throws Exception {
        return loadFile(variantStorageManager, dbName, outputUri, resourceName, -1, studyConfiguration, otherParams, true, true, true);
    }
}
