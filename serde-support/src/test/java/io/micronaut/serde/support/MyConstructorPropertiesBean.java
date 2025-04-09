package io.micronaut.serde.support;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public final class MyConstructorPropertiesBean {
    private final String message;
    private final boolean valid;
    private final Object additionalData;

    public MyConstructorPropertiesBean(String message, boolean valid, Object additionalData) {
        this.message = message;
        this.valid = valid;
        this.additionalData = additionalData;
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
}
