package org.opencb.opencga.storage.hadoop.variant.stats;

import com.google.common.collect.BiMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.util.Bytes;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.VariantSource;
import org.opencb.opencga.storage.core.variant.stats.VariantStatisticsCalculator;
import org.opencb.opencga.storage.core.variant.stats.VariantStatsWrapper;
import org.opencb.opencga.storage.hadoop.variant.AbstractHBaseVariantMapper;
import org.opencb.opencga.storage.hadoop.variant.AnalysisTableMapReduceHelper;
import org.opencb.opencga.storage.hadoop.variant.GenomeHelper;
import org.opencb.opencga.storage.hadoop.variant.converters.stats.VariantStatsToHBaseConverter;
import org.opencb.opencga.storage.hadoop.variant.index.phoenix.VariantPhoenixKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by mh719 on 07/12/2016.
 */
public class AnalysisStatsMapper extends AbstractHBaseVariantMapper<ImmutableBytesWritable, Put> {

    private Logger logger = LoggerFactory.getLogger(AnalysisStatsMapper.class);
    private VariantStatisticsCalculator variantStatisticsCalculator;
    private String studyId;
    private byte[] studiesRow;
    private Map<String, Set<String>> samples;
    private VariantStatsToHBaseConverter variantStatsToHBaseConverter;

    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        super.setup(context);
        this.getHbaseToVariantConverter().setSimpleGenotypes(true);
        studiesRow = VariantPhoenixKeyFactory.generateVariantRowKey(GenomeHelper.DEFAULT_METADATA_ROW_KEY, 0);
        variantStatisticsCalculator = new VariantStatisticsCalculator(true);
        this.variantStatisticsCalculator.setAggregationType(VariantSource.Aggregation.NONE, null);
        this.studyId = Integer.valueOf(this.getStudyConfiguration().getStudyId()).toString();
        BiMap<Integer, String> sampleIds = getStudyConfiguration().getSampleIds().inverse();
        variantStatsToHBaseConverter = new VariantStatsToHBaseConverter(this.getHelper(), this.getStudyConfiguration());
        // map from cohort Id to <cohort name, <sample names>>
        this.samples = this.getStudyConfiguration().getCohortIds().entrySet().stream()
                .map(e -> new MutablePair<>(e.getKey(), this.getStudyConfiguration().getCohorts().get(e.getValue())))
                .map(p -> new MutablePair<>(p.getKey(),
                        p.getValue().stream().map(i -> sampleIds.get(i)).collect(Collectors.toSet())))
                .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));
        this.samples.forEach((k, v) -> logger.info("Calculate {} stats for cohort {} with {}", studyId, k, StringUtils.join(v, ",")));
    }

    @Override
    protected void map(ImmutableBytesWritable key, Result value, Context context) throws IOException, InterruptedException {
        boolean done = false;
        if (!Bytes.startsWith(value.getRow(), this.studiesRow)) { // ignore _METADATA row
            try {
                Variant variant = this.getHbaseToVariantConverter().convert(value);
                List<VariantStatsWrapper> annotations = this.variantStatisticsCalculator.calculateBatch(
                        Collections.singletonList(variant), this.studyId, "notused", this.samples);
                for (VariantStatsWrapper annotation : annotations) {
                    Put convert = this.variantStatsToHBaseConverter.convert(annotation);
                    if (null != convert) {
                        context.write(key, convert);
                        done = true;
                        context.getCounter(AnalysisTableMapReduceHelper.COUNTER_GROUP_NAME, "stats.put").increment(1);
                    }
                }
                if (done) {
                    context.getCounter(AnalysisTableMapReduceHelper.COUNTER_GROUP_NAME, "variants").increment(1);
                }
            } catch (IllegalStateException e) {
                throw new IllegalStateException("Problem with row [hex:" + Bytes.toHex(key.copyBytes()) + "]", e);
            }
        }
    }
}
