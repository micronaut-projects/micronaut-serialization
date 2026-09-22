package io.micronaut.serde.jackson.nullablenested;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NullableNestedUserInfo(@JsonProperty("job_code") @Nullable String jobCode,
                                     @Nullable String roles) {
}
