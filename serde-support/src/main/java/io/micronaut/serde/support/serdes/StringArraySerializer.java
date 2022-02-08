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
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;

import java.io.IOException;

/**
 * Deserializer for string arrays.
 *
 * @author Denis Stepanov
 * @since 1.0.0
 */
final class StringArraySerializer implements Serializer<String[]> {

    static final StringArraySerializer INSTANCE = new StringArraySerializer();

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends String[]> type, String[] strings)
            throws IOException {
        encoder.encodeArray(e -> {
            for (String string : strings) {
                encoder.encodeString(string);
            }
        });
    }

    @Override
    public boolean isEmpty(EncoderContext context, String[] strings) {
        return strings == null || strings.length == 0;
    }
}
