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

import com.google.protobuf.MessageLite;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;
import org.opencb.opencga.storage.hadoop.variant.archive.ArchiveDriver;
import org.opencb.opencga.storage.hadoop.variant.index.VariantTableStudyRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * @author Matthias Haimel mh719+git@cam.ac.uk.
 */
public class GenomeHelper {
    private final Logger logger = LoggerFactory.getLogger(GenomeHelper.class);

    public static final String CONFIG_STUDY_ID = "opencga.study.id";

    //upload HBase jars and jars for any of the configured job classes via the distributed cache (tmpjars).
    public static final String CONFIG_HBASE_ADD_DEPENDENCY_JARS = "opencga.hbase.addDependencyJars";
    public static final String CONFIG_HBASE_COLUMN_FAMILY = "opencga.hbase.column_family";

    public static final String METADATA_PREFIX = "_";
    public static final String DEFAULT_METADATA_ROW_KEY = "_METADATA";
    public static final String DEFAULT_ROWKEY_SEPARATOR = "_";
    public static final String DEFAULT_COLUMN_FAMILY = "0"; // MUST BE UPPER CASE!!!

    public static final String VARIANT_COLUMN_PREFIX = "_V";
    public static final byte[] VARIANT_COLUMN_B_PREFIX = Bytes.toBytes(VARIANT_COLUMN_PREFIX);

    private final int chunkSize;
    private final char separator;
    private final byte[] columnFamily;
    private final byte[] metaRowKey;
    private final String metaRowKeyString;

    private final Configuration conf;

    private final int studyId;

    public GenomeHelper(GenomeHelper other) {
        this(other.getConf());
    }

    public GenomeHelper(Configuration conf) {
        this.conf = conf;
        this.separator = conf.get(ArchiveDriver.CONFIG_ARCHIVE_ROW_KEY_SEPARATOR, DEFAULT_ROWKEY_SEPARATOR).charAt(0);
        // TODO: Check if columnFamily is upper case
        // Phoenix local indexes fail if the default_column_family is lower case
        // TODO: Report this bug to phoenix JIRA
        this.columnFamily = Bytes.toBytes(conf.get(CONFIG_HBASE_COLUMN_FAMILY, DEFAULT_COLUMN_FAMILY));
        this.metaRowKeyString = DEFAULT_METADATA_ROW_KEY;
        this.metaRowKey = Bytes.toBytes(metaRowKeyString);
        this.chunkSize = conf.getInt(ArchiveDriver.CONFIG_ARCHIVE_CHUNK_SIZE, ArchiveDriver.DEFAULT_CHUNK_SIZE);
        this.studyId = conf.getInt(CONFIG_STUDY_ID, -1);
    }

    public Configuration getConf() {
        return conf;
    }

    public static void setChunkSize(Configuration conf, Integer size) {
        conf.setInt(ArchiveDriver.CONFIG_ARCHIVE_CHUNK_SIZE, size);
    }

    public static void setStudyId(Configuration conf, Integer studyId) {
        conf.setInt(CONFIG_STUDY_ID, studyId);
    }

    public int getStudyId() {
        return this.studyId;
    }

    public char getSeparator() {
        return separator;
    }

    public byte[] getColumnFamily() {
        return columnFamily;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public byte[] getMetaRowKey() {
        return metaRowKey;
    }

    public String getMetaRowKeyString() {
        return metaRowKeyString;
    }

    /**
     * TODO: Query CellBase to get the chromosomes and sizes!
     * @param numberOfSplits    Number of splits
     * @param keyGenerator      Function to generate the rowKeys given a chromosome and a start
     * @return                  List of splits
     */
    public static List<byte[]> generateBootPreSplitsHuman(int numberOfSplits, BiFunction<String, Integer, byte[]> keyGenerator) {
        String[] chr = new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15",
                "16", "17", "18", "19", "20", "21", "22", "X", "Y", };
        long[] posarr = new long[]{249250621, 243199373, 198022430, 191154276, 180915260, 171115067, 159138663,
                146364022, 141213431, 135534747, 135006516, 133851895, 115169878, 107349540, 102531392, 90354753,
                81195210, 78077248, 59128983, 63025520, 48129895, 51304566, 155270560, 59373566, };
        Map<String, Long> regions = new HashMap<>();
        for (int i = 0; i < chr.length; i++) {
            regions.put(chr[i], posarr[i]);
        }
        return generateBootPreSplits(numberOfSplits, keyGenerator, regions);
    }

    static List<byte[]> generateBootPreSplits(int numberOfSplits, BiFunction<String, Integer, byte[]> keyGenerator,
                                              Map<String, Long> regionsMap) {
        // Create a sorted map for the regions that sorts as will sort HBase given the row_key generator
        // In archive table, chr1 goes after chr19, and in Variants table, chr1 is always the first
        SortedMap<String, Long> sortedRegions = new TreeMap<>((s1, s2) ->
                Bytes.compareTo(keyGenerator.apply(s1, 0), keyGenerator.apply(s2, 0)));
        sortedRegions.putAll(regionsMap);

        long total = sortedRegions.values().stream().reduce((a, b) -> a + b).orElse(0L);
        long chunkSize = total / numberOfSplits;
        List<byte[]> splitList = new ArrayList<>();
        long splitPos = chunkSize;
        while (splitPos < total) {
            long tmpPos = 0;
            String chr = null;

            for (Map.Entry<String, Long> entry : sortedRegions.entrySet()) {
                long v = entry.getValue();
                if ((tmpPos + v) > splitPos) {
                    chr = entry.getKey();
                    break;
                }
                tmpPos += v;
            }
            byte[] rowKey = keyGenerator.apply(chr, (int) (splitPos - tmpPos));
            splitList.add(rowKey);
            splitPos += chunkSize;
        }
        return splitList;
    }

    public <T extends MessageLite> Put wrapAsPut(byte[] column, byte[] row, T meta) {
        byte[] data = meta.toByteArray();
        Put put = new Put(row);
        put.addColumn(getColumnFamily(), column, data);
        return put;
    }

    public static List<Cell> getVariantColumns(Cell[] cells) {
        return Arrays.stream(cells).filter(c -> Bytes.startsWith(CellUtil.cloneQualifier(c), VARIANT_COLUMN_B_PREFIX))
                .collect(Collectors.toList());
    }

    public static String getVariantcolumn(VariantTableStudyRow row) {
        return VARIANT_COLUMN_PREFIX + "_" + row.getPos() + "_" + row.getRef() + "_" + row.getAlt();
    }
}
