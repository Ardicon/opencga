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

package org.opencb.opencga.catalog.models;

import org.opencb.opencga.catalog.models.acls.AclParams;
import org.opencb.opencga.catalog.models.acls.permissions.SampleAclEntry;
import org.opencb.opencga.core.common.TimeUtils;

import java.util.*;

/**
 * Created by jacobo on 11/09/14.
 */
public class Sample extends Annotable<SampleAclEntry> {

    private long id;
    private String name;
    private String source;
    private Individual individual;

    private String creationDate;
    private Status status;
    private String description;
    private String type;
    private boolean somatic;
    private List<OntologyTerm> ontologyTerms;

    private Map<String, Object> attributes;


    public Sample() {
    }

    public Sample(long id, String name, String source, Individual individual, String description) {
        this(id, name, source, individual, description, "", false, Collections.emptyList(), new LinkedList<>(), new HashMap<>());
    }

    public Sample(long id, String name, String source, Individual individual, String description, String type, boolean somatic,
                  List<SampleAclEntry> acl, List<AnnotationSet> annotationSets, Map<String, Object> attributes) {
        this.id = id;
        this.name = name;
        this.source = source;
        this.individual = individual;
        this.type = type;
        this.somatic = somatic;
        this.creationDate = TimeUtils.getTime();
        this.status = new Status();
        this.description = description;
        this.ontologyTerms = Collections.emptyList();
        this.acl = acl;
        this.annotationSets = annotationSets;
        this.attributes = attributes;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Sample{");
        sb.append("acl=").append(acl);
        sb.append(", id=").append(id);
        sb.append(", name='").append(name).append('\'');
        sb.append(", source='").append(source).append('\'');
        sb.append(", individual=").append(individual);
        sb.append(", creationDate='").append(creationDate).append('\'');
        sb.append(", status=").append(status);
        sb.append(", annotationSets=").append(annotationSets);
        sb.append(", description='").append(description).append('\'');
        sb.append(", type='").append(type).append('\'');
        sb.append(", somatic=").append(somatic);
        sb.append(", ontologyTerms=").append(ontologyTerms);
        sb.append(", attributes=").append(attributes);
        sb.append('}');
        return sb.toString();
    }

    public long getId() {
        return id;
    }

    public Sample setId(long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public Sample setName(String name) {
        this.name = name;
        return this;
    }

    public String getSource() {
        return source;
    }

    public Sample setSource(String source) {
        this.source = source;
        return this;
    }

    public Individual getIndividual() {
        return individual;
    }

    public Sample setIndividual(Individual individual) {
        this.individual = individual;
        return this;
    }

    public String getCreationDate() {
        return creationDate;
    }

    public Sample setCreationDate(String creationDate) {
        this.creationDate = creationDate;
        return this;
    }

    public Status getStatus() {
        return status;
    }

    public Sample setStatus(Status status) {
        this.status = status;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Sample setDescription(String description) {
        this.description = description;
        return this;
    }

    public boolean isSomatic() {
        return somatic;
    }

    public Sample setSomatic(boolean somatic) {
        this.somatic = somatic;
        return this;
    }

    public String getType() {
        return type;
    }

    public Sample setType(String type) {
        this.type = type;
        return this;
    }

    public List<OntologyTerm> getOntologyTerms() {
        return ontologyTerms;
    }

    public Sample setOntologyTerms(List<OntologyTerm> ontologyTerms) {
        this.ontologyTerms = ontologyTerms;
        return this;
    }

    public Sample setAcl(List<SampleAclEntry> acl) {
        this.acl = acl;
        return this;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Sample setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
        return this;
    }

    // Acl params to communicate the WS and the sample manager
    public static class SampleAclParams extends AclParams {

        private String individual;
        private String file;
        private String cohort;

        public SampleAclParams() {
        }

        public SampleAclParams(String permissions, Action action, String individual, String file, String cohort) {
            super(permissions, action);
            this.individual = individual;
            this.file = file;
            this.cohort = cohort;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("SampleAclParams{");
            sb.append("permissions='").append(permissions).append('\'');
            sb.append(", action=").append(action);
            sb.append(", individual='").append(individual).append('\'');
            sb.append(", file='").append(file).append('\'');
            sb.append(", cohort='").append(cohort).append('\'');
            sb.append('}');
            return sb.toString();
        }

        public String getIndividual() {
            return individual;
        }

        public SampleAclParams setIndividual(String individual) {
            this.individual = individual;
            return this;
        }

        public String getFile() {
            return file;
        }

        public SampleAclParams setFile(String file) {
            this.file = file;
            return this;
        }

        public String getCohort() {
            return cohort;
        }

        public SampleAclParams setCohort(String cohort) {
            this.cohort = cohort;
            return this;
        }
    }

}
