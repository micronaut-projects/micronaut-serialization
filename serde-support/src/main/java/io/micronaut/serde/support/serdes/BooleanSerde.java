/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.support.serdes;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedSerde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;
import io.micronaut.serde.support.util.DecoderValueKind;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;

final class BooleanSerde implements FormattedSerde<Boolean>, SerdeRegistrar<Boolean>, DecoderValueKind.Provider {
    @Override
    public Boolean deserialize(Decoder decoder,
                               DecoderContext decoderContext,
                               Argument<? super Boolean> type) throws IOException {
        return decoder.decodeBoolean();
    }

    @Override
    public @Nullable Boolean deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super Boolean> type) throws IOException {
        return decoder.decodeBooleanNullable();
    }

    @Override
    public DecoderValueKind decoderValueKind() {
        return DecoderValueKind.BOOLEAN;
    }

    @Override
    public Deserializer<Boolean> createSpecific(DecoderContext context,
                                                Argument<? super Boolean> type,
                                                FormatConfiguration format) throws SerdeException {
        return this;
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends Boolean> type, Boolean value) throws IOException {
        encoder.encodeBoolean(value);
    }

    @Override
    public Serializer<Boolean> createSpecific(EncoderContext context,
                                              Argument<? extends Boolean> type,
                                              FormatConfiguration format) throws SerdeException {
        return switch (format.shape()) {
            case STRING -> new StringShapeBooleanSerializer(this);
            case NUMBER, NUMBER_INT, NUMBER_FLOAT -> new NumericShapeBooleanSerializer(this);
            default -> this;
        };
    }

    @Override
    public boolean isDefault(EncoderContext context, Boolean value) {
        return !value;
    }

    @Override
    public Argument<Boolean> getType() {
        return Argument.of(Boolean.class);
    }

    @Override
    public Iterable<Argument<?>> getTypes() {
        return Arrays.asList(
            getType(), Argument.BOOLEAN
        );
    }

    @Nullable
    @Override
    public Boolean getDefaultValue(DecoderContext context, Argument<? super Boolean> type) {
        return type.isPrimitive() ? false : null;
    }
}

abstract class AbstractBooleanShapeSerializer implements Serializer<Boolean> {
    final Serializer<Boolean> delegate;

    AbstractBooleanShapeSerializer(Serializer<Boolean> delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isEmpty(EncoderContext context, @Nullable Boolean value) {
        return delegate.isEmpty(context, value);
    }

    @Override
    public boolean isAbsent(EncoderContext context, @Nullable Boolean value) {
        return delegate.isAbsent(context, value);
    }

    @Override
    public boolean isDefault(EncoderContext context, Boolean value) {
        return delegate.isDefault(context, value);
    }
}

final class StringShapeBooleanSerializer extends AbstractBooleanShapeSerializer {

    StringShapeBooleanSerializer(Serializer<Boolean> delegate) {
        super(delegate);
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends Boolean> type,
                          Boolean value) throws IOException {
        encoder.encodeString(value.toString());
    }
}

final class NumericShapeBooleanSerializer extends AbstractBooleanShapeSerializer {

    NumericShapeBooleanSerializer(Serializer<Boolean> delegate) {
        super(delegate);
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends Boolean> type,
                          Boolean value) throws IOException {
        encoder.encodeInt(Boolean.FALSE.equals(value) ? 0 : 1);
    }
}
