package org.opencb.opencga.core.models.family;

import org.apache.commons.lang3.StringUtils;
import org.opencb.biodata.models.commons.Disorder;
import org.opencb.biodata.models.commons.Phenotype;
import org.opencb.opencga.core.models.common.AnnotationSet;
import org.opencb.opencga.core.models.individual.Individual;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FamilyCreateParams {

    private String id;
    private String name;
    private String description;

    private List<Phenotype> phenotypes;
    private List<Disorder> disorders;
    private List<IndividualCreateParams> members;

    private Integer expectedSize;

    private Map<String, Object> attributes;
    private List<AnnotationSet> annotationSets;

    public FamilyCreateParams() {
    }

    public FamilyCreateParams(String id, String name, String description, List<Phenotype> phenotypes, List<Disorder> disorders,
                              List<IndividualCreateParams> members, Integer expectedSize, Map<String, Object> attributes,
                              List<AnnotationSet> annotationSets) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.phenotypes = phenotypes;
        this.disorders = disorders;
        this.members = members;
        this.expectedSize = expectedSize;
        this.attributes = attributes;
        this.annotationSets = annotationSets;
    }

    public static FamilyCreateParams of(Family family) {
        return new FamilyCreateParams(family.getId(), family.getName(), family.getDescription(), family.getPhenotypes(),
                family.getDisorders(),
                family.getMembers() != null
                        ? family.getMembers().stream().map(IndividualCreateParams::of).collect(Collectors.toList())
                        : Collections.emptyList(),
                family.getExpectedSize(), family.getAttributes(), family.getAnnotationSets());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("FamilyCreateParams{");
        sb.append("id='").append(id).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", phenotypes=").append(phenotypes);
        sb.append(", disorders=").append(disorders);
        sb.append(", members=").append(members);
        sb.append(", expectedSize=").append(expectedSize);
        sb.append(", attributes=").append(attributes);
        sb.append(", annotationSets=").append(annotationSets);
        sb.append('}');
        return sb.toString();
    }

    public Family toFamily() {

        List<Individual> relatives = null;
        if (members != null) {
            relatives = new ArrayList<>(members.size());
            for (IndividualCreateParams member : members) {
                relatives.add(member.toIndividual());
            }
        }

        String familyId = StringUtils.isEmpty(id) ? name : id;
        String familyName = StringUtils.isEmpty(name) ? familyId : name;
        int familyExpectedSize = expectedSize != null ? expectedSize : -1;
        return new Family(familyId, familyName, phenotypes, disorders, relatives, description, familyExpectedSize, annotationSets,
                attributes);
    }

    public String getId() {
        return id;
    }

    public FamilyCreateParams setId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public FamilyCreateParams setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public FamilyCreateParams setDescription(String description) {
        this.description = description;
        return this;
    }

    public List<Phenotype> getPhenotypes() {
        return phenotypes;
    }

    public FamilyCreateParams setPhenotypes(List<Phenotype> phenotypes) {
        this.phenotypes = phenotypes;
        return this;
    }

    public List<Disorder> getDisorders() {
        return disorders;
    }

    public FamilyCreateParams setDisorders(List<Disorder> disorders) {
        this.disorders = disorders;
        return this;
    }

    public List<IndividualCreateParams> getMembers() {
        return members;
    }

    public FamilyCreateParams setMembers(List<IndividualCreateParams> members) {
        this.members = members;
        return this;
    }

    public Integer getExpectedSize() {
        return expectedSize;
    }

    public FamilyCreateParams setExpectedSize(Integer expectedSize) {
        this.expectedSize = expectedSize;
        return this;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public FamilyCreateParams setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
        return this;
    }

    public List<AnnotationSet> getAnnotationSets() {
        return annotationSets;
    }

    public FamilyCreateParams setAnnotationSets(List<AnnotationSet> annotationSets) {
        this.annotationSets = annotationSets;
        return this;
    }
}
