package io.micronaut.serde.support.watch;

import io.micronaut.context.annotation.Requires;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

/**
 * A bean that received the mapper when it was created, as an application bean does.
 */
@Singleton
@Requires(property = "serde.watch.holder", value = "true")
public class MapperHolder {

    private final JsonMapper mapper;

    public MapperHolder(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public JsonMapper getMapper() {
        return mapper;
    }
}
