package io.micronaut.serde.xml.serde;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.xml.XmlGenerator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

public abstract class XmlSerde<T> implements Serde<T> {

    protected abstract void doSerialize(XmlGenerator generator, EncoderContext context, T value, Argument<?> type) throws IOException;

    @Override
    public @Nullable T deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super T> type) throws IOException {
        return null;
    }

    @Override
    public void serialize(@NonNull Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends T> type, @NonNull T value) throws IOException {
        doSerialize((XmlGenerator) encoder, context, value, type);
    }
}
