package io.micronaut.serde.jackson.generic.scenario1;

import com.amazonaws.lambda.thirdparty.com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import org.jspecify.annotations.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY,
    property = "kind",
    defaultImpl = ResourceDeleteResponse.class, visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = StatusDeleteResponse.class, name = "Status")
})
@Serdeable
public interface DeleteResponse<T> {
    @Nullable
    T object();

    @Nullable
    V1Status status();
}
