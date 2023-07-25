package io.micronaut.serde.support;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class TestStatus {

    private boolean valid;

    private String message;
    @Nullable
    private Object additionalData;

    public TestStatus(boolean valid, String message, @Nullable Object additionalData) {
        this.valid = valid;
        this.message = message;
        this.additionalData = additionalData;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getAdditionalData() {
        return additionalData;
    }

    public void setAdditionalData(Object additionalData) {
        this.additionalData = additionalData;
    }
}
