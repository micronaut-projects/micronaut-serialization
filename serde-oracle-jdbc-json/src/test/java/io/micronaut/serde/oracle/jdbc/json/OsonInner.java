package io.micronaut.serde.oracle.jdbc.json;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class OsonInner {
    private String label;
    private String missing;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getMissing() {
        return missing;
    }

    public void setMissing(String missing) {
        this.missing = missing;
    }
}
