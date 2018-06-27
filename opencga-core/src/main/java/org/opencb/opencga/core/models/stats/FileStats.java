package org.opencb.opencga.core.models.stats;

import java.util.Map;

/**
 * Created by wasim on 25/06/18.
 */
public class FileStats {

    // file Buket based on Sizes ???
//    public Map<String, Integer> bucketsBySize;
  //  public Map<String, Integer> bucketsByType;
  //  public Map<String, Integer> bucketsByFormat;
    // Bucket by samples ???
    public Map<String, Integer> bucketsBySamples;

    public FileStats() {
    }

    public FileStats(Map<String, Integer> bucketsBySamples) {
        this.bucketsBySamples = bucketsBySamples;
     }

    public Map<String, Integer> getBucketsBySamples() {
        return bucketsBySamples;
    }

    public FileStats setBucketsBySamples(Map<String, Integer> bucketsBySamples) {
        this.bucketsBySamples = bucketsBySamples;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileStats fileStats = (FileStats) o;

        return bucketsBySamples != null ? bucketsBySamples.equals(fileStats.bucketsBySamples) : fileStats.bucketsBySamples == null;
    }

    @Override
    public int hashCode() {
        return bucketsBySamples != null ? bucketsBySamples.hashCode() : 0;
    }
}
