package io.micronaut.serde.support.watch;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;

/**
 * An application that received a mapper itself, as a server may: what recreates the mapper destroys it.
 */
@Singleton
@Requires(property = "serde.watch.application", value = "true")
public class MapperHoldingApplication implements EmbeddedApplication<MapperHoldingApplication> {

    private final ApplicationContext context;
    private final ApplicationConfiguration configuration;

    public MapperHoldingApplication(ObjectMapper mapper, ApplicationContext context, ApplicationConfiguration configuration) {
        this.context = context;
        this.configuration = configuration;
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return context;
    }

    @Override
    public ApplicationConfiguration getApplicationConfiguration() {
        return configuration;
    }

    @Override
    public Environment getEnvironment() {
        return context.getEnvironment();
    }

    @Override
    public boolean isRunning() {
        return true;
    }
}
