package io.micronaut.serde.support;

import io.micronaut.http.hateoas.AbstractResource;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class MyInfo extends AbstractResource<MyInfo> {
    private final String info;

    public MyInfo(String info) {
        this.info = info;
    }

    public String getInfo() {
        return info;
    }
}
