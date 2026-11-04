package io.micronaut.serde.support.watch;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;

/**
 * A bean that received a provider of the mapper and keeps the clone it made from the first mapper the provider gave
 * it, as the JSON media type codec of core keeps its mapper.
 */
@Singleton
@Requires(property = "serde.watch.clone-holder", value = "true")
public class LazyCloneHolder {

    private final BeanProvider<ObjectMapper> mapperProvider;
    private ObjectMapper clone;

    public LazyCloneHolder(BeanProvider<ObjectMapper> mapperProvider) {
        this.mapperProvider = mapperProvider;
    }

    public synchronized ObjectMapper getClone() {
        if (clone == null) {
            clone = mapperProvider.get().cloneWithConfiguration(null, null, null);
        }
        return clone;
    }
}
