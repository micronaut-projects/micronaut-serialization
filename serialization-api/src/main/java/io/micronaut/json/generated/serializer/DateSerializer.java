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
package io.micronaut.json.generated.serializer;

import io.micronaut.json.Decoder;
import io.micronaut.json.Deserializer;
import io.micronaut.json.Encoder;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerdeRegistry;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Date;

@Singleton
class DateSerializer implements Serializer<Date>, Deserializer<Date> {
    private final Serializer<? super Long> longSerializer;
    private final Deserializer<? extends Long> longDeserializer;

    DateSerializer(SerdeRegistry locator) {
        this.longSerializer = locator.findContravariantSerializer(long.class);
        this.longDeserializer = locator.findInvariantDeserializer(long.class);
    }

    @Override
    public Date deserialize(Decoder decoder) throws IOException {
        return new Date(longDeserializer.deserialize(decoder));
    }

    @Override
    public void serialize(Encoder encoder, Date value) throws IOException {
        longSerializer.serialize(encoder, value.getTime());
    }
}
