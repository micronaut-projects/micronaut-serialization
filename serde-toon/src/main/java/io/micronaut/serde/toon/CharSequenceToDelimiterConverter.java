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
package io.micronaut.serde.toon;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Converts a string representation of a delimiter (either enum name or character) to {@link SerdeToonConfiguration.Delimiter}.
 */
@Singleton
@Internal
final class CharSequenceToDelimiterConverter implements TypeConverter<CharSequence, SerdeToonConfiguration.Delimiter> {
    @Override
    public Optional<SerdeToonConfiguration.Delimiter> convert(CharSequence object, Class<SerdeToonConfiguration.Delimiter> targetType, ConversionContext context) {
        if (object == null || object.isEmpty()) {
            return Optional.empty();
        }
        String s = object.toString();
        if (s.length() == 1) {
            char c = s.charAt(0);
            for (SerdeToonConfiguration.Delimiter d : SerdeToonConfiguration.Delimiter.values()) {
                if (d.getCharacter() == c) {
                    return Optional.of(d);
                }
            }
        }
        String trimmed = s.trim();
        for (SerdeToonConfiguration.Delimiter d : SerdeToonConfiguration.Delimiter.values()) {
            if (d.name().equalsIgnoreCase(trimmed)) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }
}
