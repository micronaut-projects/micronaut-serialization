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
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;

/**
 * The error catching {@link io.micronaut.serde.support.serializers.ObjectSerializer}.
 *
 * @param <T> The serializer type
 * @author Denis Stepanov
 */
@Internal
final class ErrorCatchingObjectSerializer<T> extends ErrorCatchingSerializer<T> implements ObjectSerializer<T> {

    private final ObjectSerializer<T> serializer;

    ErrorCatchingObjectSerializer(ObjectSerializer<T> serializer) {
        super(serializer);
        this.serializer = serializer;
    }

    @Override
    public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        try {
            serializer.serializeInto(encoder, context, type, value);
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

}
