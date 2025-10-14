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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;

/**
 * The errors catching {@link Serializer}.
 *
 * @param <T> The serializer type
 * @author Denis Stepanov
 */
@Internal
class ErrorCatchingSerializer<T> implements Serializer<T> {

    private final Serializer<T> serializer;

    ErrorCatchingSerializer(Serializer<T> serializer) {
        this.serializer = serializer;
    }

    @Override
    public final void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T values) throws IOException {
        try {
            serializer.serialize(encoder, context, type, values);
        } catch (StackOverflowError e) {
            throw new SerdeException("Infinite recursion serializing type: " + type.getType().getSimpleName() + " at path " + encoder.currentPath(), e);
        } catch (IntrospectionException e) {
            throw new SerdeException("Error serializing value at path: " + encoder.currentPath() + ". No serializer found for type: " + type, e);
        } catch (SerdeException e) {
            throw e;
        } catch (Exception e) {
            throw new SerdeException("Error serializing value at path: " + encoder.currentPath(), e);
        }
    }

    @Override
    public final Serializer<T> createSpecific(EncoderContext context, Argument<? extends T> type) throws SerdeException {
        return serializer.createSpecific(context, type);
    }

    @Override
    public final boolean isEmpty(EncoderContext context, T value) {
        return serializer.isEmpty(context, value);
    }

    @Override
    public final boolean isAbsent(EncoderContext context, T value) {
        return serializer.isAbsent(context, value);
    }

    /**
     * @return The wrapped serializer
     */
    public final Serializer<T> getSerializer() {
        return serializer;
    }
}
