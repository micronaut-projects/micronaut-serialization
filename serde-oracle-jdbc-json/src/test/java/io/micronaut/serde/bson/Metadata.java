package io.micronaut.serde.bson;

public class Metadata {

    private byte[] etag;

    private byte[] asof;

    public byte[] getEtag() {
        return etag;
    }

    public void setEtag(byte[] etag) {
        this.etag = etag;
    }

    public byte[] getAsof() {
        return asof;
    }

    public void setAsof(byte[] asof) {
        this.asof = asof;
    }
}
