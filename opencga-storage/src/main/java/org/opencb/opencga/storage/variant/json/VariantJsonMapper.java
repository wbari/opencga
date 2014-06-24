package org.opencb.opencga.storage.variant.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.opencb.biodata.models.feature.Genotype;
import org.opencb.biodata.models.variant.ArchivedVariantFile;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.stats.VariantStats;

/**
 *
 * @author Cristina Yenyxe Gonzalez Garcia <cyenyxe@ebi.ac.uk>
 * @author Matthias Heimel <mh719@cam.ac.uk>
 */
public class VariantJsonMapper extends Mapper<LongWritable, Text, LongWritable, Variant> {

    protected ObjectMapper jsonObjectMapper;
    
    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        this.jsonObjectMapper = new ObjectMapper();
        jsonObjectMapper.addMixInAnnotations(ArchivedVariantFile.class, ArchivedVariantFileJsonMixin.class);
        jsonObjectMapper.addMixInAnnotations(Genotype.class, GenotypeJsonMixin.class);
        jsonObjectMapper.addMixInAnnotations(VariantStats.class, VariantStatsJsonMixin.class);
    }

    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        Variant variant = jsonObjectMapper.readValue(value.toString(), Variant.class);
        context.write(key, variant);
    }

}
