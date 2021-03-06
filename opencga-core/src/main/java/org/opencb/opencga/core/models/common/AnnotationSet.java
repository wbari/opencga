/*
 * Copyright 2015-2017 OpenCB
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

package org.opencb.opencga.core.models.common;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.opencb.opencga.core.common.TimeUtils;

import java.util.Collections;
import java.util.Map;

/**
 * Created by imedina on 25/11/14.
 */
public class AnnotationSet {

    private String id;
    @Deprecated
    private String name;
    private String variableSetId;
    private Map<String, Object> annotations;
    @Deprecated
    private String creationDate;
    @Deprecated
    private int release;
    @Deprecated
    private Map<String, Object> attributes;


    public AnnotationSet() {
    }

    public AnnotationSet(String id, String variableSetId, Map<String, Object> annotations) {
        this(id, variableSetId, annotations, TimeUtils.getTime(), 1, Collections.emptyMap());
    }

    public AnnotationSet(String id, String variableSetId, Map<String, Object> annotations, Map<String, Object> attributes) {
        this(id, variableSetId, annotations, TimeUtils.getTime(), 1, attributes);
    }

    public AnnotationSet(String id, String variableSetId, Map<String, Object> annotations, String creationDate, int release,
                         Map<String, Object> attributes) {
        this.id = id;
        this.name = id;
        this.variableSetId = variableSetId;
        this.annotations = annotations;
        this.creationDate = creationDate;
        this.release = release;
        this.attributes = attributes;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AnnotationSet{");
        sb.append("id='").append(id).append('\'');
        sb.append(", variableSetId=").append(variableSetId);
        sb.append(", annotations=").append(annotations);
        sb.append(", creationDate='").append(creationDate).append('\'');
        sb.append(", release=").append(release);
        sb.append(", attributes=").append(attributes);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AnnotationSet that = (AnnotationSet) o;
        return new EqualsBuilder().append(variableSetId, that.variableSetId).append(release, that.release).append(id, that.id)
                .append(annotations, that.annotations).append(creationDate, that.creationDate).append(attributes, that.attributes)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(id).append(variableSetId).append(annotations).append(creationDate).append(release)
                .append(attributes).toHashCode();
    }

    public String getId() {
        return id;
    }

    public AnnotationSet setId(String id) {
        this.id = id;
        this.name = StringUtils.isEmpty(this.name) ? this.id : this.name;
        return this;
    }

    public String getName() {
        return name;
    }

    public AnnotationSet setName(String name) {
        this.name = name;
        this.id = StringUtils.isEmpty(this.id) ? this.name : this.id;
        return this;
    }

    public String getVariableSetId() {
        return variableSetId;
    }

    public AnnotationSet setVariableSetId(String variableSetId) {
        this.variableSetId = variableSetId;
        return this;
    }

    public Map<String, Object> getAnnotations() {
        return annotations;
    }

    public AnnotationSet setAnnotations(Map<String, Object> annotations) {
        this.annotations = annotations;
        return this;
    }

    public String getCreationDate() {
        return creationDate;
    }

    public AnnotationSet setCreationDate(String creationDate) {
        this.creationDate = creationDate;
        return this;
    }

    public int getRelease() {
        return release;
    }

    public AnnotationSet setRelease(int release) {
        this.release = release;
        return this;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public AnnotationSet setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
        return this;
    }
}
