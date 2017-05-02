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

package org.opencb.opencga.catalog.db.api;

/**
 * Created by pfurio on 23/05/16.
 */

import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.catalog.models.Session;
import org.opencb.opencga.catalog.models.acls.permissions.StudyAclEntry;

import java.util.List;

public interface MetaDBAdaptor {

    boolean isRegisterOpen();

    QueryResult<Session> addAdminSession(Session session) throws CatalogDBException;

    void logout(String sessionId) throws CatalogDBException;

    String getAdminPassword() throws CatalogDBException;

    boolean checkValidAdminSession(String id);

    QueryResult<StudyAclEntry> getDaemonAcl(List<String> members) throws CatalogDBException;

}
