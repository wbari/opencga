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

import org.apache.commons.collections.map.LinkedMap;
import org.opencb.commons.datastore.core.*;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.catalog.models.AnnotationSet;
import org.opencb.opencga.catalog.models.Cohort;

import java.util.Map;

import static org.opencb.commons.datastore.core.QueryParam.Type.*;

/**
 * Created by pfurio on 3/22/16.
 */
public interface CohortDBAdaptor extends AnnotationSetDBAdaptor<Cohort> {

    enum QueryParams implements QueryParam {
        ID("id", DECIMAL, ""),
        NAME("name", TEXT, ""),
        TYPE("type", TEXT, ""),
        CREATION_DATE("creationDate", TEXT, ""),
        STATUS("status", TEXT_ARRAY, ""),
        STATUS_NAME("status.name", TEXT, ""),
        STATUS_MSG("status.msg", TEXT, ""),
        STATUS_DATE("status.date", TEXT, ""),
        DESCRIPTION("description", TEXT, ""),

        ACL("acl", TEXT_ARRAY, ""),
        ACL_MEMBER("acl.member", TEXT_ARRAY, ""),
        ACL_PERMISSIONS("acl.permissions", TEXT_ARRAY, ""),
        SAMPLES("samples", DECIMAL, ""),

        ANNOTATION_SETS("annotationSets", TEXT_ARRAY, ""),
        VARIABLE_SET_ID("variableSetId", INTEGER, ""),
        VARIABLE_NAME("variableName", TEXT, ""),
        ANNOTATION_SET_NAME("annotationSetName", TEXT_ARRAY, ""),

        ANNOTATION("annotation", TEXT_ARRAY, ""),

        ATTRIBUTES("attributes", TEXT, "Format: <key><operation><stringValue> where <operation> is [<|<=|>|>=|==|!=|~|!~]"),
        NATTRIBUTES("nattributes", DECIMAL, "Format: <key><operation><numericalValue> where <operation> is [<|<=|>|>=|==|!=|~|!~]"),
        BATTRIBUTES("battributes", BOOLEAN, "Format: <key><operation><true|false> where <operation> is [==|!=]"),

        STATS("stats", TEXT, "Format: <key><operation><stringValue> where <operation> is [<|<=|>|>=|==|!=|~|!~]"),
        NSTATS("nstats", DECIMAL, "Format: <key><operation><numericalValue> where <operation> is [<|<=|>|>=|==|!=|~|!~]"),
        BSTATS("bstats", BOOLEAN, "Format: <key><operation><true|false> where <operation> is [==|!=]"),

        STUDY_ID("studyId", DECIMAL, ""),
        STUDY("study", INTEGER_ARRAY, ""); // Alias to studyId in the database. Only for the webservices.

        private static Map<String, QueryParams> map;
        static {
            map = new LinkedMap();
            for (QueryParams params : QueryParams.values()) {
                map.put(params.key(), params);
            }
        }

        private final String key;
        private Type type;
        private String description;

        QueryParams(String key, Type type, String description) {
            this.key = key;
            this.type = type;
            this.description = description;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public Type type() {
            return type;
        }

        @Override
        public String description() {
            return description;
        }

        public static Map<String, QueryParams> getMap() {
            return map;
        }

        public static QueryParams getParam(String key) {
            return map.get(key);
        }
    }

    default boolean exists(long cohortId) throws CatalogDBException {
        return count(new Query(QueryParams.ID.key(), cohortId)).first() > 0;
    }

    default void checkId(long cohortId) throws CatalogDBException {
        if (cohortId < 0) {
            throw CatalogDBException.newInstance("Cohort id '{}' is not valid: ", cohortId);
        }

        if (!exists(cohortId)) {
            throw CatalogDBException.newInstance("Cohort id '{}' does not exist", cohortId);
        }
    }

    QueryResult<Cohort> insert(Cohort cohort, long studyId, QueryOptions options) throws CatalogDBException;

    QueryResult<Cohort> get(long cohortId, QueryOptions options) throws CatalogDBException;

    QueryResult<Cohort> getAllInStudy(long studyId, QueryOptions options) throws CatalogDBException;

    QueryResult<Cohort> update(long cohortId, ObjectMap parameters, QueryOptions options) throws CatalogDBException;

    @Deprecated
    QueryResult<AnnotationSet> annotate(long cohortId, AnnotationSet annotationSet, boolean overwrite) throws CatalogDBException;

    @Deprecated
    QueryResult<AnnotationSet> deleteAnnotation(long cohortId, String annotationId) throws CatalogDBException;

    long getStudyId(long cohortId) throws CatalogDBException;

}
