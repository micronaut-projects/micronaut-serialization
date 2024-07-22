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
package io.micronaut.serde.support.deserializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;

/**
 * The error catching {@link UpdatingDeserializer}.
 *
 * @param <T> The deserializer type
 * @author Denis Stepanov
 */
@Internal
final class ErrorCatchingUpdatingDeserializer<T> extends ErrorCatchingDeserializer<T> implements UpdatingDeserializer<T> {

    private final UpdatingDeserializer<T> deserializer;

    ErrorCatchingUpdatingDeserializer(UpdatingDeserializer<T> deserializer) {
        super(deserializer);
        this.deserializer = deserializer;
    }

    @Override
    public void deserializeInto(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type, T value) throws IOException {
        try {
            deserializer.deserializeInto(decoder, decoderContext, type, value);
        } catch (StackOverflowError e) {
            throw new SerdeException("Infinite recursion deserializing type: " + type, e);
        } catch (IntrospectionException e) {
            throw new SerdeException("Error deserializing. No deserializing found for type: " + type, e);
        } catch (SerdeException e) {
            throw e;
        } catch (Exception e) {
            throw new SerdeException("Error deserializing value: " + value + " of type: " + type, e);
        }
    }
}
