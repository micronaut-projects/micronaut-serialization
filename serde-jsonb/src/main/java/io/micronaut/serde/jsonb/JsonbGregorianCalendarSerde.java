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
package io.micronaut.serde.jsonb;

import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.support.SerdeRegistrar;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * JSON-B default mapping for {@link GregorianCalendar}.
 */
@Internal
@Singleton
final class JsonbGregorianCalendarSerde implements Serde<GregorianCalendar> {
    private final boolean strictIJson;

    /**
     * @param serdeConfiguration The active Serde configuration used to select
     * strict I-JSON date/time formatting
     */
    JsonbGregorianCalendarSerde(SerdeConfiguration serdeConfiguration) {
        this.strictIJson = serdeConfiguration.isWriteDateTimesAsStrictIJson();
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends GregorianCalendar> type, GregorianCalendar value) throws IOException {
        encoder.encodeString(JsonbCalendarSerde.format(value, strictIJson));
    }

    @Override
    public GregorianCalendar deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super GregorianCalendar> type) throws IOException {
        Calendar calendar = JsonbCalendarSerde.parse(decoder.decodeString());
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
}
