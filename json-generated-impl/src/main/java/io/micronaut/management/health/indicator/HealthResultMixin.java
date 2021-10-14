package io.micronaut.management.health.indicator;

import io.micronaut.context.annotation.Requires;
import io.micronaut.json.Decoder;
import io.micronaut.json.Deserializer;
import io.micronaut.json.SerdeRegistry;
import io.micronaut.json.annotation.SerializableBean;
import io.micronaut.json.annotation.SerializationMixin;
import jakarta.inject.Singleton;

import java.io.IOException;

@SerializationMixin(forClass = DefaultHealthResult.class, config = @SerializableBean(allowSerialization = false))
@SerializationMixin(forClass = HealthResult.class, config = @SerializableBean(allowDeserialization = false))
class HealthResultMixin {

    @Singleton
    @Requires(classes = HealthResult.class)
    static class DelegatingDeserializer implements Deserializer<HealthResult> {
        private final Deserializer<DefaultHealthResult> delegate;

        DelegatingDeserializer(SerdeRegistry locator) {
            this.delegate = locator.findInvariantDeserializer(DefaultHealthResult.class);
        }

        @Override
        public HealthResult deserialize(Decoder decoder) throws IOException {
            return delegate.deserialize(decoder);
        }
    }
}
