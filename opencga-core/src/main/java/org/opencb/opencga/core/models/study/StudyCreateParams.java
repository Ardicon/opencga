package org.opencb.opencga.core.models.study;

import java.util.Map;

public class StudyCreateParams {

    private String id;
    private String name;
    private String alias;
    private Study.Type type;
    private String description;
    private String webhook;

    private Map<String, Object> stats;
    private Map<String, Object> attributes;

    public StudyCreateParams() {
    }

    public StudyCreateParams(String id, String name, String alias, Study.Type type, String description, String webhook,
                             Map<String, Object> stats, Map<String, Object> attributes) {
        this.id = id;
        this.name = name;
        this.alias = alias;
        this.type = type;
        this.description = description;
        this.webhook = webhook;
        this.stats = stats;
        this.attributes = attributes;
    }

    public static StudyCreateParams of(Study study) {
        return new StudyCreateParams(study.getId(), study.getName(), study.getAlias(), study.getType(), study.getDescription(),
                study.getWebhook() != null ? study.getWebhook().toString() : null, study.getStats(), study.getAttributes());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("StudyCreateParams{");
        sb.append("id='").append(id).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", alias='").append(alias).append('\'');
        sb.append(", type=").append(type);
        sb.append(", description='").append(description).append('\'');
        sb.append(", webhook=").append(webhook);
        sb.append(", stats=").append(stats);
        sb.append(", attributes=").append(attributes);
        sb.append('}');
        return sb.toString();
    }

    public String getId() {
        return id;
    }

    public StudyCreateParams setId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public StudyCreateParams setName(String name) {
        this.name = name;
        return this;
    }

    public String getAlias() {
        return alias;
    }

    public StudyCreateParams setAlias(String alias) {
        this.alias = alias;
        return this;
    }

    public Study.Type getType() {
        return type;
    }

    public StudyCreateParams setType(Study.Type type) {
        this.type = type;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public StudyCreateParams setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getWebhook() {
        return webhook;
    }

    public StudyCreateParams setWebhook(String webhook) {
        this.webhook = webhook;
        return this;
    }

    public Map<String, Object> getStats() {
        return stats;
    }

    public StudyCreateParams setStats(Map<String, Object> stats) {
        this.stats = stats;
        return this;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public StudyCreateParams setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
        return this;
    }
}
