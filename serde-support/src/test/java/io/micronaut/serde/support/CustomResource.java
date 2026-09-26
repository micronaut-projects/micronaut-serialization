package io.micronaut.serde.support;

import io.micronaut.core.type.Argument;
import io.micronaut.http.hateoas.AbstractResource;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * A resource type with its own deserializer, which also matches {@link io.micronaut.http.hateoas.Resource}.
 */
public class CustomResource extends AbstractResource<CustomResource> {

    @Singleton
    static final class CustomResourceDeserializer implements Deserializer<CustomResource> {
        @Override
        public CustomResource deserialize(Decoder decoder, DecoderContext context, Argument<? super CustomResource> type) throws IOException {
            decoder.skipValue();
            return new CustomResource();
        }
    }
}
