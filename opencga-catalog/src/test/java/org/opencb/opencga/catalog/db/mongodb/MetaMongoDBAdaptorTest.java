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

package org.opencb.opencga.catalog.db.mongodb;

import org.junit.Test;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.catalog.models.acls.permissions.StudyAclEntry;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * Created by imedina on 07/04/16.
 */
public class MetaMongoDBAdaptorTest extends MongoDBAdaptorTest {

    @Test
    public void createIndex() throws Exception {
        catalogDBAdaptor.getCatalogMetaDBAdaptor().createIndexes();
        catalogDBAdaptor.getCatalogMetaDBAdaptor().createIndexes();
    }

    @Test
    public void getAcl() throws CatalogDBException {
        QueryResult<StudyAclEntry> aclQueryResult = catalogDBAdaptor.getCatalogMetaDBAdaptor().getDaemonAcl(Arrays.asList("admin"));
        assertEquals(1, aclQueryResult.getNumResults());
        assertEquals("admin", aclQueryResult.first().getMember());
    }

}