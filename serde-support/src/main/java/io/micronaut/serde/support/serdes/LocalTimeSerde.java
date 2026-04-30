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
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;

/**
 * LocalTime serde.
 *
 * @since 1.0.0
 */
public final class LocalTimeSerde extends NumericSupportTemporalSerde<LocalTime> implements SerdeRegistrar<LocalTime> {
    /**
     * Allows configuring a default time format for temporal date/time types.
     *
     * @param configuration The configuration
     */
    public LocalTimeSerde(SerdeConfiguration configuration) {
        this(
            stringFormatter(configuration),
            configuration.getTimeWriteShape(),
            configuration.getNumericTimeUnit()
        );
    }

    private LocalTimeSerde(DateTimeFormatter stringFormatter,
                           SerdeConfiguration.TimeShape writeShape,
                           SerdeConfiguration.NumericTimeUnit numericUnit) {
        super(
            stringFormatter,
            SerdeConfiguration.NumericTimeUnit.NANOSECONDS,
            writeShape,
            numericUnit
        );
    }

    @Override
    public TemporalQuery<LocalTime> query() {
        return TemporalQueries.localTime();
    }

    @Override
    public Serializer<LocalTime> createSpecific(EncoderContext context,
                                                Argument<? extends LocalTime> type,
                                                @NonNull FormatConfiguration format) {
        Serializer<LocalTime> specific = super.createSpecific(context, type, format);
        if (isArrayShape(format)) {
            return new LocalTimeArrayShapeSerializer(specific, TemporalArrayShapeSupport.writeDateTimestampsAsNanos(context));
        }
        return specific;
    }

    @Override
    public Deserializer<LocalTime> createSpecific(DecoderContext decoderContext,
                                                  Argument<? super LocalTime> type,
                                                  @NonNull FormatConfiguration format) throws SerdeException {
        Deserializer<LocalTime> specific = super.createSpecific(decoderContext, type, format);
        if (isArrayShape(format)) {
            return new LocalTimeArrayShapeDeserializer(specific, TemporalArrayShapeSupport.readDateTimestampsAsNanos(decoderContext));
        }
        return specific;
    }

    @Override
    protected LocalTime fromNanos(long seconds, int nanos) {
        return LocalTime.ofSecondOfDay(seconds).withNano(nanos);
    }

    @Override
    protected long getSecondPart(LocalTime value) {
        return value.toSecondOfDay();
    }

    @Override
    protected int getNanoPart(LocalTime value) {
        return value.getNano();
    }

    @Override
    protected DefaultFormattedTemporalSerde<LocalTime> createSpecific(DateTimeFormatter stringFormatter,
                                                                      SerdeConfiguration.TimeShape timeWriteShape,
                                                                      SerdeConfiguration.NumericTimeUnit numericUnit) {
        return new LocalTimeSerde(stringFormatter, timeWriteShape, numericUnit);
    }

    @Override
    public Argument<LocalTime> getType() {
        return Argument.of(LocalTime.class);
    }

    private static boolean isArrayShape(@NonNull FormatConfiguration format) {
        return TemporalArrayShapeSupport.isNumericOrArrayShape(format);
    }

    private static DateTimeFormatter stringFormatter(SerdeConfiguration configuration) {
        return createFormatter(configuration).orElse(DateTimeFormatter.ISO_LOCAL_TIME);
    }

    private static final class LocalTimeArrayShapeSerializer extends TemporalArrayShapeSupport.AbstractSerializer<LocalTime> {
        private final boolean writeNanos;

        private LocalTimeArrayShapeSerializer(Serializer<LocalTime> delegate, boolean writeNanos) {
            super(delegate);
            this.writeNanos = writeNanos;
        }

        @Override
        void serializeArray(Encoder arrayEncoder, LocalTime value) throws IOException {
            TemporalArrayShapeSupport.serializeLocalTime(arrayEncoder, value, writeNanos);
        }
    }

    private static final class LocalTimeArrayShapeDeserializer extends TemporalArrayShapeSupport.AbstractDeserializer<LocalTime> {
        private final boolean readNanos;

        private LocalTimeArrayShapeDeserializer(Deserializer<LocalTime> delegate, boolean readNanos) {
            super(delegate);
            this.readNanos = readNanos;
        }

        @Override
        LocalTime deserializeArray(Decoder arrayDecoder) throws IOException {
            return TemporalArrayShapeSupport.deserializeLocalTime(arrayDecoder, readNanos);
        }
    }
}
