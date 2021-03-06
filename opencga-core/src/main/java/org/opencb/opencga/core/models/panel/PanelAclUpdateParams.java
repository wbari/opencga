package org.opencb.opencga.core.models.panel;

import org.opencb.opencga.core.models.AclParams;

public class PanelAclUpdateParams extends AclParams {

    private String panel;

    public PanelAclUpdateParams() {
    }

    public PanelAclUpdateParams(String permissions, Action action, String panel) {
        super(permissions, action);
        this.panel = panel;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PanelAclUpdateParams{");
        sb.append("panel='").append(panel).append('\'');
        sb.append(", permissions='").append(permissions).append('\'');
        sb.append(", action=").append(action);
        sb.append('}');
        return sb.toString();
    }

    public String getPanel() {
        return panel;
    }

    public PanelAclUpdateParams setPanel(String panel) {
        this.panel = panel;
        return this;
    }

    public PanelAclUpdateParams setPermissions(String permissions) {
        super.setPermissions(permissions);
        return this;
    }

    public PanelAclUpdateParams setAction(Action action) {
        super.setAction(action);
        return this;
    }
}
