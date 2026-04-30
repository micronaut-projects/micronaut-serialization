/*
 * Copyright 2017-2021 original authors
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
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;

/**
 * Local date serde. Slightly different to {@link NumericSupportTemporalSerde}, we only support one
 * unit (epoch day)
 */
public final class LocalDateSerde extends DefaultFormattedTemporalSerde<LocalDate> implements TemporalSerde<LocalDate>, SerdeRegistrar<LocalDate> {
    private final boolean writeNumeric;

    /**
     * Allows configuring a default time format for temporal date/time types.
     *
     * @param configuration The configuration
     */
    public LocalDateSerde(SerdeConfiguration configuration) {
        this(stringFormatter(configuration), configuration.getTimeWriteShape());
    }

    private LocalDateSerde(DateTimeFormatter stringFormatter,
                           SerdeConfiguration.TimeShape writeShape) {
        super(stringFormatter);
        this.writeNumeric = writeShape != SerdeConfiguration.TimeShape.STRING;
    }

    @Override
    public TemporalQuery<LocalDate> query() {
        return TemporalQueries.localDate();
    }

    @Override
    public Serializer<LocalDate> createSpecific(EncoderContext context,
                                                Argument<? extends LocalDate> type,
                                                FormatConfiguration format) {
        Serializer<LocalDate> specific = super.createSpecific(context, type, format);
        if (isArrayShape(format)) {
            return new LocalDateArrayShapeSerializer(specific);
        }
        return specific;
    }

    @Override
    public Deserializer<LocalDate> createSpecific(DecoderContext decoderContext,
                                                  Argument<? super LocalDate> type,
                                                  FormatConfiguration format) throws SerdeException {
        Deserializer<LocalDate> specific = super.createSpecific(decoderContext, type, format);
        if (isArrayShape(format)) {
            return new LocalDateArrayShapeDeserializer(specific);
        }
        return specific;
    }

    @Override
    protected DefaultFormattedTemporalSerde<LocalDate> createSpecific(DateTimeFormatter stringFormatter,
                                                                      SerdeConfiguration.TimeShape timeWriteShape,
                                                                      SerdeConfiguration.NumericTimeUnit numericUnit) {
        return new LocalDateSerde(stringFormatter, timeWriteShape);
    }

    @Override
    void serialize0(Encoder encoder, LocalDate value) throws IOException {
        if (writeNumeric) {
            encoder.encodeLong(value.toEpochDay());
        } else {
            super.serialize0(encoder, value);
        }
    }

    @Override
    LocalDate deserializeFallback(DateTimeException exc, String s) {
        long l;
        try {
            l = Long.parseLong(s);
        } catch (NumberFormatException e) {
            exc.addSuppressed(e);
            throw exc;
        }
        return LocalDate.ofEpochDay(l);
    }

    @Override
    public Argument<LocalDate> getType() {
        return Argument.of(LocalDate.class);
    }

    private static boolean isArrayShape(FormatConfiguration format) {
        FormatConfiguration.Shape shape = format.shape();
        return shape == FormatConfiguration.Shape.NUMBER
            || shape == FormatConfiguration.Shape.NUMBER_FLOAT
            || shape == FormatConfiguration.Shape.ARRAY;
    }

    private static DateTimeFormatter stringFormatter(SerdeConfiguration configuration) {
        return createFormatter(configuration).orElse(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private static final class LocalDateArrayShapeSerializer extends TemporalArrayShapeSupport.AbstractSerializer<LocalDate> {
        private LocalDateArrayShapeSerializer(Serializer<LocalDate> delegate) {
            super(delegate);
        }

        @Override
        void serializeArray(Encoder arrayEncoder, LocalDate value) throws IOException {
            arrayEncoder.encodeInt(value.getYear());
            arrayEncoder.encodeInt(value.getMonthValue());
            arrayEncoder.encodeInt(value.getDayOfMonth());
        }
    }

    private static final class LocalDateArrayShapeDeserializer extends TemporalArrayShapeSupport.AbstractDeserializer<LocalDate> {
        private LocalDateArrayShapeDeserializer(Deserializer<LocalDate> delegate) {
            super(delegate);
        }

        @Override
        LocalDate deserializeArray(Decoder arrayDecoder) throws IOException {
            int year = arrayDecoder.decodeInt();
            int month = arrayDecoder.decodeInt();
            int day = arrayDecoder.decodeInt();
            return LocalDate.of(year, month, day);
        }
    }
}
