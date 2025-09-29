package io.micronaut.serde.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonInclude
public record MyBeanWithNestedObjectNonNull(String id,
                                            @JsonUnwrapped MyNestedBean inner) {

    @Serdeable
    @JsonInclude
    record MyNestedBean(Key key, String name) {
    }

    @Serdeable
    @JsonInclude
    record Key(String strId, Long longId) {
    }
}
