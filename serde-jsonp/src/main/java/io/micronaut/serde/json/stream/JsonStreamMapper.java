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
package io.micronaut.serde.json.stream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.function.Consumer;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.util.JsonNodeDecoder;
import io.micronaut.serde.util.JsonNodeEncoder;
import io.micronaut.serde.util.BufferingJsonNodeProcessor;
import jakarta.inject.Singleton;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import org.reactivestreams.Processor;

/**
 * Implementation of the {@link io.micronaut.json.JsonMapper} interface for JSON-P.
 */
@Singleton
public class JsonStreamMapper implements JsonMapper {
    private final SerdeRegistry registry;

    public JsonStreamMapper(SerdeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public <T> T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        final Deserializer<? extends T> deserializer = this.registry.findDeserializer(type);
        return deserializer.deserialize(
                JsonNodeDecoder.create(tree),
                registry.newDecoderContext(null),
                type
        );
    }

    @Override
    public <T> T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        try (JsonParser parser = Json.createParser(inputStream)) {
            return readValue(parser, type);
        }
    }

    @Override
    public <T> T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        try (JsonParser parser = Json.createParser(new ByteArrayInputStream(byteArray))) {
            return readValue(parser, type);
        }
    }

    private <T> T readValue(JsonParser parser, Argument<T> type) throws IOException {
        final Deserializer<? extends T> deserializer = this.registry.findDeserializer(type);
        return deserializer.deserialize(
                new JsonParserDecoder(parser),
                registry.newDecoderContext(null),
                type
        );
    }

    @Override
    public Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe,
                                                            boolean streamArray) {
        return new BufferingJsonNodeProcessor(onSubscribe, streamArray) {
            @NonNull
            @Override
            protected JsonNode parseOne(@NonNull InputStream is) throws IOException {
                try (JsonParser parser = Json.createParser(is)) {
                    final JsonParserDecoder decoder = new JsonParserDecoder(parser);
                    final Object o = decoder.decodeArbitrary();
                    return writeValueToTree(o);
                }
            }
        };
    }

    @Override
    public JsonNode writeValueToTree(Object value) throws IOException {
        JsonNodeEncoder encoder = JsonNodeEncoder.create();
        serialize(encoder, value);
        return encoder.getCompletedValue();
    }

    @Override
    public void writeValue(OutputStream outputStream, Object object) throws IOException {
        try (JsonGenerator generator = Json.createGenerator(Objects.requireNonNull(outputStream, "Output stream cannot be null"))) {
            if (object == null) {
                generator.writeNull();
            } else {
                JsonStreamEncoder encoder = new JsonStreamEncoder(generator);
                serialize(encoder, object);
            }
            generator.flush();
        }
    }

    private void serialize(Encoder encoder, Object object) throws IOException {
        serialize(encoder, object, Argument.of(object.getClass()));
    }

    private void serialize(Encoder encoder, Object object, Argument type) throws IOException {
        final Serializer<Object> serializer = registry.findSerializer(type);
        serializer.serialize(
                encoder,
                registry.newEncoderContext(null),
                object,
                type
        );
    }

    @Override
    public byte[] writeValueAsBytes(Object object) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, object);
        return output.toByteArray();
    }

    @Override
    public JsonStreamConfig getStreamConfig() {
        return null;
    }

}
