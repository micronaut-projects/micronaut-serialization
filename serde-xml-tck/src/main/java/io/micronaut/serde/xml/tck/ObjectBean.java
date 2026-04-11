package io.micronaut.serde.xml.tck;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class ObjectBean {

    Object simpleBeans;

    public ObjectBean() {
    }

    public ObjectBean(Object simpleBeans) {
        this.simpleBeans = simpleBeans;
    }

    public Object getSimpleBeans() {
        return simpleBeans;
    }
}
