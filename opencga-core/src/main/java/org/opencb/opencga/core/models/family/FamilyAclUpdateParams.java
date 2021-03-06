package org.opencb.opencga.core.models.family;

import org.opencb.opencga.core.models.AclParams;

public class FamilyAclUpdateParams extends AclParams {

    private String family;

    public FamilyAclUpdateParams() {
    }

    public FamilyAclUpdateParams(String permissions, Action action, String family) {
        super(permissions, action);
        this.family = family;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("FamilyAclUpdateParams{");
        sb.append("family='").append(family).append('\'');
        sb.append(", permissions='").append(permissions).append('\'');
        sb.append(", action=").append(action);
        sb.append('}');
        return sb.toString();
    }

    public String getFamily() {
        return family;
    }

    public FamilyAclUpdateParams setFamily(String family) {
        this.family = family;
        return this;
    }

    public FamilyAclUpdateParams setPermissions(String permissions) {
        super.setPermissions(permissions);
        return this;
    }

    public FamilyAclUpdateParams setAction(Action action) {
        super.setAction(action);
        return this;
    }
}
