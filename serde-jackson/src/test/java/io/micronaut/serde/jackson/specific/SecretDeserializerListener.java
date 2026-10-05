package io.micronaut.serde.jackson.specific;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts the user-written deserializers created with the context and the type.
 */
@Requires(property = "spec.name", value = "SpecificSerdeBeanSpec")
@Singleton
public final class SecretDeserializerListener implements BeanCreatedEventListener<SecretSerde.SecretDeserializer> {

    final AtomicInteger created = new AtomicInteger();

    @Override
    public SecretSerde.SecretDeserializer onCreated(BeanCreatedEvent<SecretSerde.SecretDeserializer> event) {
        created.incrementAndGet();
        return event.getBean();
    }
}
