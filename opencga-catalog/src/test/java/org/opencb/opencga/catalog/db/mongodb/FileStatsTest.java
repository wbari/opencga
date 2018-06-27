package org.opencb.opencga.catalog.db.mongodb;

import org.junit.Test;
import org.opencb.opencga.core.models.stats.FileStats;

/**
 * Created by wasim on 27/06/18.
 */
public class FileStatsTest extends MongoDBAdaptorTest {
    @Test
    public void createFileStatsTest() throws Exception {
        FileStats fileStats = catalogDBAdaptor.getCatalogFileDBAdaptor().createStats("1");
        System.out.println(fileStats);

        // assert bla bla bla

    }

}
