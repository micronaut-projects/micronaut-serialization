package io.micronaut.serde.support;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public final class MyMixSetterConstructorPropertiesBean {
    private final String message;
    private final boolean valid;
    private Object additionalData;

    public MyMixSetterConstructorPropertiesBean(String message, boolean valid) {
        this.message = message;
        this.valid = valid;
    }

    public String getMessage() {
        return message;
    }

    public boolean isValid() {
        return valid;
    }

    public Object getAdditionalData() {
        return additionalData;
    }

    public void setAdditionalData(Object additionalData) {
        this.additionalData = additionalData;
    }
}
