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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;

import java.io.IOException;
import java.util.Arrays;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

@Internal
final class TimeZoneSerde implements SerdeRegistrar<TimeZone> {
    private final boolean rejectDeprecatedThreeLetterIds;

    TimeZoneSerde() {
        this(false);
    }

    TimeZoneSerde(SerdeConfiguration configuration) {
        this(configuration.isRejectDeprecatedThreeLetterTimeZoneIds());
    }

    private TimeZoneSerde(boolean rejectDeprecatedThreeLetterIds) {
        this.rejectDeprecatedThreeLetterIds = rejectDeprecatedThreeLetterIds;
    }

    @Override
    public Argument<TimeZone> getType() {
        return Argument.of(TimeZone.class);
    }

    @Override
    public Iterable<Argument<?>> getTypes() {
        return Arrays.asList(getType(), Argument.of(SimpleTimeZone.class));
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends TimeZone> type, TimeZone value)
        throws IOException {
        encoder.encodeString(value.getID());
    }

    @Override
    public TimeZone deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super TimeZone> type)
        throws IOException {
        String value = decoder.decodeString();
        if (rejectDeprecatedThreeLetterIds && isDeprecatedThreeLetterTimeZone(value)) {
            throw new SerdeException("Deprecated three-letter time zone IDs are not supported: " + value);
        }
        TimeZone timeZone = TimeZone.getTimeZone(value);
        if (SimpleTimeZone.class.isAssignableFrom(type.getType())) {
            return new SimpleTimeZone(timeZone.getRawOffset(), timeZone.getID());
        }
        return timeZone;
    }

    private static boolean isDeprecatedThreeLetterTimeZone(String value) {
        return value.length() == 3 && !"UTC".equals(value) && !"GMT".equals(value);
    }
}
