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
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;

/**
 * Simple object serializer.
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 1.0
 */
@Internal
public final class SimpleObjectSerializer<T> implements Serializer<T> {
    private final SerBean<Object> serBean;

    public SimpleObjectSerializer(SerBean<Object> serBean) {
        this.serBean = serBean;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        try {
            if (value == null) {
                encoder.encodeNull();
            } else {
                Encoder childEncoder = encoder.encodeObject(type);
                for (SerBean.SerProperty<Object, Object> property : serBean.writeProperties) {
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
        } catch (StackOverflowError e) {
            throw new SerdeException("Infinite recursion serializing type: " + type.getType().getSimpleName() + " at path " + encoder.currentPath(), e);
        } catch (IntrospectionException e) {
            throw new SerdeException("Error serializing value at path: " + encoder.currentPath() + ". No serializer found for " + "type: " + type, e);
        }
    }
}
