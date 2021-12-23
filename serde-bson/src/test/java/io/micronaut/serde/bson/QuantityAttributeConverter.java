
package io.micronaut.serde.bson;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import jakarta.inject.Singleton;

import java.io.IOException;

// Keep Serde<Object> so it isn't pickup as Serde<Quantity>
@Singleton
public class QuantityAttributeConverter implements Serde<Object> {

    @Override
    public Quantity deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type) throws IOException {
        if (!type.isAnnotationPresent(MyAnn.class)) {
            throw new IllegalStateException("MyAnn is expected to be present");
        }
        return new Quantity(decoder.decodeInt());
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Object value, Argument<? extends Object> type) throws IOException {
        if (!type.isAnnotationPresent(MyAnn.class)) {
            throw new IllegalStateException("MyAnn is expected to be present");
        }
        encoder.encodeInt(((Quantity) value).getAmount());
    }
}
