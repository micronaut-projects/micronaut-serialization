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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerializerRegistrar;
import io.micronaut.serde.util.CustomizableSerializer;

import java.util.Map;

/**
 * The map serializer.
 *
 * @param <K> The key type
 * @param <V> The value type
 * @author Denis Stepanov
 */
@Internal
final class CustomizedMapSerializer<K, V> implements CustomizableSerializer<Map<K, V>>, SerializerRegistrar<Map<K, V>> {

    @Override
    public ObjectSerializer<Map<K, V>> createSpecific(EncoderContext context, Argument<? extends Map<K, V>> type) throws SerdeException {
        final Argument<?>[] generics = type.getTypeParameters();
        final boolean hasGenerics = ArrayUtils.isNotEmpty(generics) && generics.length == 2;
        if (hasGenerics) {
            Argument<?> keyArgument = generics[0];
            if (keyArgument.getType() == String.class) {
                return new StringKeyMapSerializer<>((Argument) type, context);
            }
            if (CharSequence.class.isAssignableFrom(keyArgument.getType())) {
                return new CharSequenceKeyMapSerializer<>((Argument) type, context);
            }
        }
        return new RuntimeMapSerializer<>(type, context);

    }

    @Override
    public Argument<Map<K, V>> getType() {
        return (Argument) Argument.mapOf(Argument.ofTypeVariable(Object.class, "K"), Argument.ofTypeVariable(Object.class, "V"));
    }

}
