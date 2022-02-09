/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;

import java.io.IOException;
import java.util.Collection;

/**
 * The String iterable serializer.
 *
 * @author Denis Stepanov
 */
final class StringIterableSerializer implements Serializer<Iterable<String>> {

    static final StringIterableSerializer INSTANCE = new StringIterableSerializer();

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Iterable<String>> type, Iterable<String> values) throws IOException {
        final Encoder childEncoder = encoder.encodeArray(type);
        for (String value : values) {
            if (value == null) {
                encoder.encodeNull();
                continue;
            }
            childEncoder.encodeString(value);
        }
        childEncoder.finishStructure();
    }

    @Override
    public boolean isEmpty(EncoderContext context, Iterable<String> value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Collection) {
            return ((Collection<String>) value).isEmpty();
        } else {
            return !value.iterator().hasNext();
        }
    }

    @Override
    public boolean isAbsent(EncoderContext context, Iterable<String> value) {
        return value == null;
    }

}
