package io.micronaut.serde.support;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public final class MySetterPropertiesBean {
    private String message;
    private boolean valid;
    private Object additionalData;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public Object getAdditionalData() {
        return additionalData;
    }

    public void setAdditionalData(Object additionalData) {
        this.additionalData = additionalData;
    }
}
