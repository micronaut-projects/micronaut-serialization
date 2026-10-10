package io.micronaut.serde.support.watch;

import io.micronaut.context.annotation.Requires;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;

/**
 * A bean that received the mapper and keeps a clone of it, as an application bean that needs other settings does.
 */
@Singleton
@Requires(property = "serde.watch.clone-holder", value = "true")
public class CloneHolder {

    private final ObjectMapper clone;

    public CloneHolder(ObjectMapper mapper) {
        this.clone = mapper.cloneWithConfiguration(null, null, null);
    }

    public ObjectMapper getClone() {
        return clone;
    }
}
