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

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;

import java.io.IOException;
import java.util.ArrayList;

/**
 * A specific collection of String.
 *
 * @author Denis Stepanov
 */
final class StringListDeserializer implements Deserializer<ArrayList<String>> {

    static final StringListDeserializer INSTANCE = new StringListDeserializer();

    private StringListDeserializer() {
    }

    @Override
    public ArrayList<String> deserialize(Decoder decoder, DecoderContext context, Argument<? super ArrayList<String>> type) throws IOException {
        final Decoder arrayDecoder = decoder.decodeArray();
        ArrayList<String> collection = new ArrayList<>();
        while (arrayDecoder.hasNextArrayValue()) {
            if (arrayDecoder.decodeNull()) {
                collection.add(null);
            } else {
                collection.add(arrayDecoder.decodeString());
            }
        }
        arrayDecoder.finishStructure();
        return collection;
    }

    @Override
    public ArrayList<String> getDefaultValue(DecoderContext context, Argument<? super ArrayList<String>> type) {
        return new ArrayList<>();
    }

}
