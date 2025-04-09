package io.micronaut.serde.jackson.mixin;

public class MuxedEvent3 {
    String compartment;
    String content;

    public MuxedEvent3(String compartment, String content) {
        this.compartment = compartment;
        this.content = content;
    }

    public MuxedEvent3() {
    }
}
