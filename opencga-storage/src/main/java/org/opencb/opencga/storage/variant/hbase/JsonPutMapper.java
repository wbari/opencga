package org.opencb.opencga.storage.variant.hbase;

import java.io.IOException;

import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.opencb.biodata.models.feature.Genotype;
import org.opencb.biodata.models.variant.ArchivedVariantFile;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.stats.VariantStats;
import org.opencb.opencga.storage.variant.json.ArchivedVariantFileJsonMixin;
import org.opencb.opencga.storage.variant.json.GenotypeJsonMixin;
import org.opencb.opencga.storage.variant.json.VariantStatsJsonMixin;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 *
 * @author Cristina Yenyxe Gonzalez Garcia <cyenyxe@ebi.ac.uk>
 * @author Matthias Heimel <mh719@cam.ac.uk>
 */
public class JsonPutMapper extends Mapper<LongWritable, Text, ImmutableBytesWritable, Put> {

    private VariantToHBaseConverter converter;
    protected ObjectMapper jsonObjectMapper;
    
    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        this.jsonObjectMapper = new ObjectMapper();
        jsonObjectMapper.addMixInAnnotations(ArchivedVariantFile.class, ArchivedVariantFileJsonMixin.class);
        jsonObjectMapper.addMixInAnnotations(Genotype.class, GenotypeJsonMixin.class);
        jsonObjectMapper.addMixInAnnotations(VariantStats.class, VariantStatsJsonMixin.class);
        
        // TODO Get 'includeSamples', 'includeStats' and 'includeEffect' configuration
        converter = new VariantToHBaseConverter();
    }

    
    @Override
    protected void map(LongWritable key, Text txt, Context context) throws IOException, InterruptedException {
        Variant variant = jsonObjectMapper.readValue(txt.toString(), Variant.class);
        Put put = converter.convertToStorageType(variant);
        ImmutableBytesWritable bytes = new ImmutableBytesWritable(put.getRow());
        context.write(bytes, put);
    }
    
}
