package io.micronaut.serde.jackson.mixin;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MuxedEvent2 {
    @JsonProperty
    String compartment;
    @JsonProperty
    String content;
    String name;

    public MuxedEvent2(String compartment, String content, String name) {
        this.compartment = compartment;
        this.content = content;
        this.name = name;
    }

    public MuxedEvent2() {
    }
}
