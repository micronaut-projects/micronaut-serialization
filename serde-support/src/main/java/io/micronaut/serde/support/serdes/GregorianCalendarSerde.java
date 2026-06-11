/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedSerde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Serde mapping for {@link GregorianCalendar}.
 */
@Internal
final class GregorianCalendarSerde implements FormattedSerde<GregorianCalendar>, SerdeRegistrar<GregorianCalendar> {
    private static final Argument<GregorianCalendar> ARGUMENT = Argument.of(GregorianCalendar.class);
    private static final Argument<Calendar> CALENDAR_ARGUMENT = Argument.of(Calendar.class);

    private final CalendarSerde calendarSerde;

    /**
     * @param calendarSerde The calendar serde
     */
    GregorianCalendarSerde(CalendarSerde calendarSerde) {
        this.calendarSerde = calendarSerde;
    }

    @Override
    public Serializer<GregorianCalendar> createSpecific(EncoderContext context, Argument<? extends GregorianCalendar> type) {
        return serializer(calendarSerde.createSpecific(context, calendarType(type)));
    }

    @Override
    public Serializer<GregorianCalendar> createSpecific(EncoderContext context,
                                                        Argument<? extends GregorianCalendar> type,
                                                        FormatConfiguration format) {
        return serializer(calendarSerde.createSpecific(context, calendarType(type), format));
    }

    @Override
    public Deserializer<GregorianCalendar> createSpecific(DecoderContext context, Argument<? super GregorianCalendar> type)
        throws SerdeException {
        return deserializer(calendarSerde.createSpecific(context, calendarSuperType(type)));
    }

    @Override
    public Deserializer<GregorianCalendar> createSpecific(DecoderContext context,
                                                          Argument<? super GregorianCalendar> type,
                                                          FormatConfiguration format) throws SerdeException {
        return deserializer(calendarSerde.createSpecific(context, calendarSuperType(type), format));
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends GregorianCalendar> type, GregorianCalendar value) throws IOException {
        calendarSerde.serialize(encoder, context, ARGUMENT, value);
    }

    @Override
    public GregorianCalendar deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super GregorianCalendar> type) throws IOException {
        Calendar calendar = calendarSerde.deserialize(decoder, decoderContext, CALENDAR_ARGUMENT);
        GregorianCalendar gregorianCalendar = new GregorianCalendar(calendar.getTimeZone());
        gregorianCalendar.clear();
        gregorianCalendar.setTimeInMillis(calendar.getTimeInMillis());
        return gregorianCalendar;
    }

    @Override
    public @Nullable GregorianCalendar deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super GregorianCalendar> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }

    @Override
    public Argument<GregorianCalendar> getType() {
        return ARGUMENT;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Argument<? extends Calendar> calendarType(Argument<? extends GregorianCalendar> type) {
        return (Argument) type;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Argument<? super Calendar> calendarSuperType(Argument<? super GregorianCalendar> type) {
        return (Argument) type;
    }

    private static Serializer<GregorianCalendar> serializer(Serializer<Calendar> serializer) {
        return new Serializer<>() {
            @Override
            public void serialize(Encoder encoder,
                                  EncoderContext context,
                                  Argument<? extends GregorianCalendar> type,
                                  GregorianCalendar value) throws IOException {
                serializer.serialize(encoder, context, calendarType(type), value);
            }
        };
    }

    private static Deserializer<GregorianCalendar> deserializer(Deserializer<Calendar> deserializer) {
        return new Deserializer<>() {
            @Override
            public GregorianCalendar deserialize(Decoder decoder,
                                                 DecoderContext decoderContext,
                                                 Argument<? super GregorianCalendar> type) throws IOException {
                return toGregorianCalendar(deserializer.deserialize(decoder, decoderContext, calendarSuperType(type)));
            }

            @Override
            public @Nullable GregorianCalendar deserializeNullable(Decoder decoder,
                                                                   DecoderContext context,
                                                                   Argument<? super GregorianCalendar> type) throws IOException {
                Calendar calendar = deserializer.deserializeNullable(decoder, context, calendarSuperType(type));
                return calendar == null ? null : toGregorianCalendar(calendar);
            }
        };
    }

    private static GregorianCalendar toGregorianCalendar(Calendar calendar) {
        GregorianCalendar gregorianCalendar = new GregorianCalendar(calendar.getTimeZone());
        gregorianCalendar.clear();
        gregorianCalendar.setTimeInMillis(calendar.getTimeInMillis());
        return gregorianCalendar;
    }
}
