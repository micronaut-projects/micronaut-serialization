package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Metadata {

    private String etag;

    private String asof;

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public String getAsof() {
        return asof;
    }

    public void setAsof(String asof) {
        this.asof = asof;
    }
}
