package io.micronaut.serde.support;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class SimpleInfo {
    private final String info;

    public SimpleInfo(String info) {
        this.info = info;
    }

    public String getInfo() {
        return info;
    }
}
