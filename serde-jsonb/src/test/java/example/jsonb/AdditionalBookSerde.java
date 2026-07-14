/*
 * Copyright 2017-2026 original authors
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
package example.jsonb;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.Serializer;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * Custom serde that is only visible to standalone JSON-B when the package is explicitly included.
 */
@Singleton
public final class AdditionalBookSerde implements Serde<AdditionalBook> {
    @Override
    public void serialize(Encoder encoder,
                          Serializer.EncoderContext context,
                          Argument<? extends AdditionalBook> type,
                          AdditionalBook value) throws IOException {
        Encoder object = encoder.encodeObject(type);
        object.encodeKey("custom");
        object.encodeString("serde:" + value.title());
        object.finishStructure();
    }

    @Override
    public AdditionalBook deserialize(Decoder decoder,
                                      Deserializer.DecoderContext context,
                                      Argument<? super AdditionalBook> type) throws IOException {
        Decoder object = decoder.decodeObject(type);
        String title = null;
        String key;
        while ((key = object.decodeKey()) != null) {
            if ("custom".equals(key)) {
                title = object.decodeString().replaceFirst("^serde:", "");
            } else {
                object.decodeArbitrary();
            }
        }
        object.finishStructure();
        return new AdditionalBook(title);
    }
}
