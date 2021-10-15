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

import io.micronaut.context.annotation.Requires;
import io.micronaut.json.Decoder;
import io.micronaut.json.Deserializer;
import io.micronaut.json.Encoder;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerdeRegistry;
import io.netty.handler.codec.http.HttpMethod;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * Jackson-databind creates this serializer magically, we need to add our own
 */
@Singleton
@Requires(classes = HttpMethod.class)
class NettyHttpMethodSerializer implements Serializer<HttpMethod>, Deserializer<HttpMethod> {
    private final Serializer<? super String> stringSerializer;
    private final Deserializer<? extends String> stringDeserializer;

    @Inject
    NettyHttpMethodSerializer(SerdeRegistry locator) {
        this.stringSerializer = locator.findSerializer(String.class);
        this.stringDeserializer = locator.findDeserializer(String.class);
    }

    @Override
    public HttpMethod deserialize(Decoder decoder) throws IOException {
        return HttpMethod.valueOf(stringDeserializer.deserialize(decoder));
    }

    @Override
    public void serialize(Encoder encoder, HttpMethod value) throws IOException {
        stringSerializer.serialize(encoder, value.name());
    }
}
