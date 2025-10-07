package io.micronaut.serde.jackson.generic.scenario2;

import com.amazonaws.lambda.thirdparty.com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY,
    property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StatusDeleteResponse.class, name = "Status"),
    @JsonSubTypes.Type(value = NamespaceDeleteResponse.class, name = "Namespace")
})
@Serdeable
public interface DeleteResponse<T> {
    @Nullable
    T object();

    @Nullable
    V1Status status();
}
