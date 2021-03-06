package org.opencb.opencga.core.models.individual;

import org.opencb.opencga.core.models.AclParams;

public class IndividualAclUpdateParams extends AclParams {

    private String individual;
    private String sample;
    private boolean propagate;

    public IndividualAclUpdateParams() {
    }

    public IndividualAclUpdateParams(String permissions, Action action, String individual, String sample, boolean propagate) {
        super(permissions, action);
        this.individual = individual;
        this.sample = sample;
        this.propagate = propagate;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("IndividualAclUpdateParams{");
        sb.append("individual='").append(individual).append('\'');
        sb.append(", sample='").append(sample).append('\'');
        sb.append(", propagate=").append(propagate);
        sb.append(", permissions='").append(permissions).append('\'');
        sb.append(", action=").append(action);
        sb.append('}');
        return sb.toString();
    }

    public String getIndividual() {
        return individual;
    }

    public IndividualAclUpdateParams setIndividual(String individual) {
        this.individual = individual;
        return this;
    }

    public String getSample() {
        return sample;
    }

    public IndividualAclUpdateParams setSample(String sample) {
        this.sample = sample;
        return this;
    }

    public boolean isPropagate() {
        return propagate;
    }

    public IndividualAclUpdateParams setPropagate(boolean propagate) {
        this.propagate = propagate;
        return this;
    }

    public IndividualAclUpdateParams setPermissions(String permissions) {
        super.setPermissions(permissions);
        return this;
    }

    public IndividualAclUpdateParams setAction(Action action) {
        super.setAction(action);
        return this;
    }
}
