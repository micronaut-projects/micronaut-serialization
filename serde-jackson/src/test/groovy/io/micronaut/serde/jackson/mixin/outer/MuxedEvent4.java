package io.micronaut.serde.jackson.mixin.outer;

public class MuxedEvent4 {
    String compartment;
    String content;

    public MuxedEvent4(String compartment, String content) {
        this.compartment = compartment;
        this.content = content;
    }

    public MuxedEvent4() {
    }
}
