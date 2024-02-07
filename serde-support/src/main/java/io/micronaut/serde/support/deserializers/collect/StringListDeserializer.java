/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.serde.support.deserializers.collect;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.support.DeserializerRegistrar;

import java.io.IOException;
import java.util.ArrayList;

/**
 * A specific collection of String.
 *
 * @author Denis Stepanov
 */
@Internal
final class StringListDeserializer implements DeserializerRegistrar<ArrayList<String>> {

    @Override
    public ArrayList<String> deserialize(Decoder decoder, DecoderContext context, Argument<? super ArrayList<String>> type) throws IOException {
        final Decoder arrayDecoder = decoder.decodeArray();
        ArrayList<String> collection = new ArrayList<>();
        while (arrayDecoder.hasNextArrayValue()) {
            collection.add(arrayDecoder.decodeStringNullable());
        }
        arrayDecoder.finishStructure();
        return collection;
    }

    @Override
    public ArrayList<String> deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super ArrayList<String>> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }

    @Override
    public ArrayList<String> getDefaultValue(DecoderContext context, Argument<? super ArrayList<String>> type) {
        return new ArrayList<>();
    }

    @Override
    public Argument<ArrayList<String>> getType() {
        return (Argument) Argument.of(ArrayList.class, String.class);
    }
}
