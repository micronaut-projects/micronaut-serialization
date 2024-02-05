package io.micronaut.serde.jackson.mixin;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable(validate = false)
@JsonDeserialize(as = FooMessage.class)
public interface MessageMixin {
}
