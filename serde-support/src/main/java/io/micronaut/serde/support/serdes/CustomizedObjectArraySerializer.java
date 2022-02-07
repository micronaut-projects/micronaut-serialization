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
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;

import java.io.IOException;

/**
 * Deserializer for object arrays.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public final class CustomizedObjectArraySerializer implements Serializer<Object[]> {

    private final Argument<Object> componentType;
    private final Serializer<Object> componentSerializer;

    public CustomizedObjectArraySerializer(Argument<Object> componentType, Serializer<Object> componentSerializer) {
        this.componentType = componentType;
        this.componentSerializer = componentSerializer;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Object[]> type, Object[] value)
            throws IOException {
        final Encoder arrayEncoder = encoder.encodeArray(type);
        // TODO: need better generics handling in core for arrays
        for (Object v : value) {
            componentSerializer.serialize(
                    arrayEncoder,
                    context,
                    componentType, v
            );
        }
        arrayEncoder.finishStructure();
    }

    @Override
    public boolean isEmpty(EncoderContext context, Object[] value) {
        return ArrayUtils.isEmpty(value);
    }
}
