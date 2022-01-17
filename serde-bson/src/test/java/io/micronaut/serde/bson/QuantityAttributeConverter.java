
package io.micronaut.serde.bson;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Objects;

// Keep Serde<Object> so it isn't pickup as Serde<Quantity>
@Singleton
public class QuantityAttributeConverter implements Serde<Object> {

    @Override
    public Quantity deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type) throws IOException {
        Objects.requireNonNull(decoder);
        Objects.requireNonNull(decoderContext);
        if (!type.isAnnotationPresent(MyAnn1.class)) {
            throw new IllegalStateException("MyAnn1 is expected to be present");
        }
        if (!type.isAnnotationPresent(MyAnn2.class)) {
            throw new IllegalStateException("MyAnn2 is expected to be present");
        }
        return new Quantity(decoder.decodeInt());
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Object value, Argument<? extends Object> type) throws IOException {
        Objects.requireNonNull(encoder);
        Objects.requireNonNull(context);
        if (!type.isAnnotationPresent(MyAnn1.class)) {
            throw new IllegalStateException("MyAnn1 is expected to be present");
        }
        encoder.encodeInt(((Quantity) value).getAmount());
    }

    @Override
    public Object getDefaultValue(DecoderContext decoderContext, Argument<? super Object> type) {
        Objects.requireNonNull(decoderContext);
        if (!type.isAnnotationPresent(MyAnn1.class)) {
            throw new IllegalStateException("MyAnn1 is expected to be present");
        }
        if (!type.isAnnotationPresent(MyAnn2.class)) {
            throw new IllegalStateException("MyAnn2 is expected to be present");
        }
        return new Quantity(123456);
    }
}
