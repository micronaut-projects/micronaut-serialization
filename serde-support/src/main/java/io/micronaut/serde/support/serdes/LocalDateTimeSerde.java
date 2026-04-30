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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQuery;

/**
 * Temporal serde for LocalDateTime.
 *
 * @since 1.0.0
 */
public final class LocalDateTimeSerde extends DefaultFormattedTemporalSerde<LocalDateTime>
        implements TemporalSerde<LocalDateTime>, SerdeRegistrar<LocalDateTime> {

    public LocalDateTimeSerde(SerdeConfiguration configuration) {
        this(stringFormatter(configuration));
    }

    private LocalDateTimeSerde(DateTimeFormatter stringFormatter) {
        super(stringFormatter);
    }

    @Override
    public TemporalQuery<LocalDateTime> query() {
        return LocalDateTime::from;
    }

    @Override
    public Serializer<LocalDateTime> createSpecific(EncoderContext context,
                                                    Argument<? extends LocalDateTime> type,
                                                    FormatConfiguration format) {
        Serializer<LocalDateTime> specific = super.createSpecific(context, type, format);
        if (isArrayShape(format)) {
            return new LocalDateTimeArrayShapeSerializer(specific, TemporalArrayShapeSupport.writeDateTimestampsAsNanos(context));
        }
        return specific;
    }

    @Override
    public Deserializer<LocalDateTime> createSpecific(DecoderContext decoderContext,
                                                      Argument<? super LocalDateTime> type,
                                                      FormatConfiguration format) throws SerdeException {
        Deserializer<LocalDateTime> specific = super.createSpecific(decoderContext, type, format);
        if (isArrayShape(format)) {
            return new LocalDateTimeArrayShapeDeserializer(specific, TemporalArrayShapeSupport.readDateTimestampsAsNanos(decoderContext));
        }
        return specific;
    }

    @Override
    protected DefaultFormattedTemporalSerde<LocalDateTime> createSpecific(DateTimeFormatter stringFormatter,
                                                                          SerdeConfiguration.TimeShape timeWriteShape,
                                                                          SerdeConfiguration.NumericTimeUnit numericUnit) {
        return new LocalDateTimeSerde(stringFormatter);
    }

    @Override
    public Argument<LocalDateTime> getType() {
        return Argument.of(LocalDateTime.class);
    }

    private static boolean isArrayShape(FormatConfiguration format) {
        return TemporalArrayShapeSupport.isNumericOrArrayShape(format);
    }

    private static DateTimeFormatter stringFormatter(SerdeConfiguration configuration) {
        return createFormatter(configuration).orElse(DateTimeFormatter.ISO_DATE_TIME);
    }

    private static final class LocalDateTimeArrayShapeSerializer extends TemporalArrayShapeSupport.AbstractSerializer<LocalDateTime> {
        private final boolean writeNanos;

        private LocalDateTimeArrayShapeSerializer(Serializer<LocalDateTime> delegate, boolean writeNanos) {
            super(delegate);
            this.writeNanos = writeNanos;
        }

        @Override
        void serializeArray(Encoder arrayEncoder, LocalDateTime value) throws IOException {
            arrayEncoder.encodeInt(value.getYear());
            arrayEncoder.encodeInt(value.getMonthValue());
            arrayEncoder.encodeInt(value.getDayOfMonth());
            TemporalArrayShapeSupport.serializeLocalTime(arrayEncoder, value.toLocalTime(), writeNanos);
        }
    }

    private static final class LocalDateTimeArrayShapeDeserializer extends TemporalArrayShapeSupport.AbstractDeserializer<LocalDateTime> {
        private final boolean readNanos;

        private LocalDateTimeArrayShapeDeserializer(Deserializer<LocalDateTime> delegate, boolean readNanos) {
            super(delegate);
            this.readNanos = readNanos;
        }

        @Override
        LocalDateTime deserializeArray(Decoder arrayDecoder) throws IOException {
            int year = arrayDecoder.decodeInt();
            int month = arrayDecoder.decodeInt();
            int day = arrayDecoder.decodeInt();
            return LocalDateTime.of(
                LocalDate.of(year, month, day),
                TemporalArrayShapeSupport.deserializeLocalTime(arrayDecoder, readNanos)
            );
        }
    }
}
