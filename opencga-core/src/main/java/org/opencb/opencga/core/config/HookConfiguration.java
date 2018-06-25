package org.opencb.opencga.core.config;

public class HookConfiguration {

    private String field;
    private String value;
    private Stage stage;
    private Action action;
    private String where;
    private String what;

    public HookConfiguration() {
    }

    public HookConfiguration(String field, String value, Stage stage, Action action, String where, String what) {
        this.field = field;
        this.value = value;
        this.stage = stage;
        this.action = action;
        this.where = where;
        this.what = what;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("HookConfiguration{");
        sb.append("field='").append(field).append('\'');
        sb.append(", value='").append(value).append('\'');
        sb.append(", stage=").append(stage);
        sb.append(", action=").append(action);
        sb.append(", where='").append(where).append('\'');
        sb.append(", what='").append(what).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public enum Stage {
        CREATE   // We only support CREATE at the moment
    }

    public enum Action {
        ADD,
        SET,
        REMOVE,
        ABORT
    }


    public String getField() {
        return field;
    }

    public HookConfiguration setField(String field) {
        this.field = field;
        return this;
    }

    public String getValue() {
        return value;
    }

    public HookConfiguration setValue(String value) {
        this.value = value;
        return this;
    }

    public Stage getStage() {
        return stage;
    }

    public HookConfiguration setStage(Stage stage) {
        this.stage = stage;
        return this;
    }

    public Action getAction() {
        return action;
    }

    public HookConfiguration setAction(Action action) {
        this.action = action;
        return this;
    }

    public String getWhere() {
        return where;
    }

    public HookConfiguration setWhere(String where) {
        this.where = where;
        return this;
    }

    public String getWhat() {
        return what;
    }

    public HookConfiguration setWhat(String what) {
        this.what = what;
        return this;
    }
}
