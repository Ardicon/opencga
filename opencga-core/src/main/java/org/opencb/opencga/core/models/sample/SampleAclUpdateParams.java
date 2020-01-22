package org.opencb.opencga.core.models.sample;

import org.opencb.opencga.core.models.AclParams;

public class SampleAclUpdateParams extends AclParams {

    private String sample;
    private String individual;
    private String file;
    private String cohort;

    private boolean propagate;

    public SampleAclUpdateParams() {
    }

    public SampleAclUpdateParams(String permissions, Action action, String sample, String individual, String file, String cohort,
                                 boolean propagate) {
        super(permissions, action);
        this.sample = sample;
        this.individual = individual;
        this.file = file;
        this.cohort = cohort;
        this.propagate = propagate;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SampleAclUpdateParams{");
        sb.append("sample='").append(sample).append('\'');
        sb.append(", individual='").append(individual).append('\'');
        sb.append(", file='").append(file).append('\'');
        sb.append(", cohort='").append(cohort).append('\'');
        sb.append(", propagate=").append(propagate);
        sb.append(", permissions='").append(permissions).append('\'');
        sb.append(", action=").append(action);
        sb.append('}');
        return sb.toString();
    }

    public String getSample() {
        return sample;
    }

    public SampleAclUpdateParams setSample(String sample) {
        this.sample = sample;
        return this;
    }

    public String getIndividual() {
        return individual;
    }

    public SampleAclUpdateParams setIndividual(String individual) {
        this.individual = individual;
        return this;
    }

    public String getFile() {
        return file;
    }

    public SampleAclUpdateParams setFile(String file) {
        this.file = file;
        return this;
    }

    public String getCohort() {
        return cohort;
    }

    public SampleAclUpdateParams setCohort(String cohort) {
        this.cohort = cohort;
        return this;
    }

    public boolean isPropagate() {
        return propagate;
    }

    public SampleAclUpdateParams setPropagate(boolean propagate) {
        this.propagate = propagate;
        return this;
    }

    public SampleAclUpdateParams setPermissions(String permissions) {
        super.setPermissions(permissions);
        return this;
    }

    public SampleAclUpdateParams setAction(Action action) {
        super.setAction(action);
        return this;
    }
}
