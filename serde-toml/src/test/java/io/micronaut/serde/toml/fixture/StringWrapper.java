package io.micronaut.serde.toml.fixture;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class StringWrapper {
    private String string;

    public StringWrapper() {
    }

    public StringWrapper(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }

    public void setString(String string) {
        this.string = string;
    }
}
