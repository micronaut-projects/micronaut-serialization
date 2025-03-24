package io.micronaut.serde.jackson.mixin;

public class MuxedEvent {
    String compartment;
    String content;

    public MuxedEvent(String compartment, String content) {
        this.compartment = compartment;
        this.content = content;
    }

    public MuxedEvent() {
    }
}
