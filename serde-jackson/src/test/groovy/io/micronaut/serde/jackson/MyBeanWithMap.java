package io.micronaut.serde.jackson;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
record MyBeanWithMap(String fooBar, int abcXyz, @Nullable @JsonInclude(JsonInclude.Include.NON_EMPTY) MyNestedBeanWithMap nested) {

    @Serdeable
    record MyNestedBeanWithMap(Long id, @Nullable @JsonAnyGetter @JsonAnySetter Map<String, Object> map) {
    }
}
