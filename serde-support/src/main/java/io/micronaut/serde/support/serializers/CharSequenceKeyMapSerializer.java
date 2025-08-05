/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;
import java.util.Map;

/**
 * The char sequence map key serializer.
 *
 * @param <V> The value type
 * @author Denis Stepanov
 */
@Internal
final class CharSequenceKeyMapSerializer<V> extends AbstractMapObjectSerializer<CharSequence, V> {

    CharSequenceKeyMapSerializer(Argument<? extends Map<CharSequence, V>> type, EncoderContext context) throws SerdeException {
        super(type, context);
    }

    @Override
    protected void encodeKey(Encoder encoder, EncoderContext context, CharSequence charSequence) throws IOException {
        encoder.encodeKey(charSequence.toString());
    }

}
