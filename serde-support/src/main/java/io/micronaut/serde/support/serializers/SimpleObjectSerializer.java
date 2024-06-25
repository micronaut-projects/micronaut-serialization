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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectSerializer;

import java.io.IOException;
import java.util.List;

/**
 * Simple object serializer.
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 1.0
 */
@Internal
final class SimpleObjectSerializer<T> implements ObjectSerializer<T> {

    private final List<SerBean.SerProperty<T, Object>> writeProperties;

    SimpleObjectSerializer(SerBean<T> serBean) {
        this.writeProperties = serBean.writeProperties;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        if (value == null) {
            encoder.encodeNull();
        } else {
            Encoder childEncoder = encoder.encodeObject(type);
            for (SerBean.SerProperty<T, Object> property : writeProperties) {
                childEncoder.encodeKey(property.name);
                Object v = property.get(value);
                if (v == null) {
                    childEncoder.encodeNull();
                } else {
                    property.serializer.serialize(childEncoder, context, property.argument, v);
                }
            }
            childEncoder.finishStructure();
        }
    }

    @Override
    public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        for (SerBean.SerProperty<T, Object> property : writeProperties) {
            encoder.encodeKey(property.name);
            Object v = property.get(value);
            if (v == null) {
                encoder.encodeNull();
            } else {
                property.serializer.serialize(encoder, context, property.argument, v);
            }
        }
    }
}
