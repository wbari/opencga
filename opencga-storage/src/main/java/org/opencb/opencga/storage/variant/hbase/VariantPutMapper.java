package org.opencb.opencga.storage.variant.hbase;

import java.io.IOException;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.opencb.biodata.models.variant.Variant;

/**
 *
 * @author Cristina Yenyxe Gonzalez Garcia <cyenyxe@ebi.ac.uk>
 * @author Matthias Heimel <mh719@cam.ac.uk>
 */
public class VariantPutMapper extends Mapper<LongWritable, Variant, ImmutableBytesWritable, Put> {

    private VariantToHBaseConverter converter;
            
    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        // TODO Get 'includeSamples', 'includeStats' and 'includeEffect' configuration
        converter = new VariantToHBaseConverter();
    }

    
    @Override
    protected void map(LongWritable key, Variant value, Context context) throws IOException, InterruptedException {
        Put put = converter.convertToStorageType(value);
        ImmutableBytesWritable bytes = new ImmutableBytesWritable(put.getRow());
        context.write(bytes, put);
    }
    
}
