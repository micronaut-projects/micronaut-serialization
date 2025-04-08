package io.micronaut.serde.jackson.mixin.outer;

public class MuxedEvent5 {
    String compartment;
    String content;

    public MuxedEvent5(String compartment, String content) {
        this.compartment = compartment;
        this.content = content;
    }

    public MuxedEvent5() {
    }
}
