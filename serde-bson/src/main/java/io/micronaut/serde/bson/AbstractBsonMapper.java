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
package io.micronaut.serde.bson;

import io.micronaut.core.annotation.Internal;
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
import io.micronaut.serde.util.SimpleBufferingJsonNodeProcessor;
import jakarta.inject.Singleton;
import org.bson.AbstractBsonWriter;
import org.bson.BsonReader;
import org.reactivestreams.Processor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.function.Consumer;

/**
 * Abstract Bson mapper.
 *
 * @author Denis Stepanov
 */
@Singleton
@Internal
public abstract class AbstractBsonMapper implements JsonMapper {
    private final SerdeRegistry registry;

    public AbstractBsonMapper(SerdeRegistry registry) {
        this.registry = registry;
    }

    protected abstract BsonReader createBsonReader(ByteBuffer byteBuffer);

    protected abstract AbstractBsonWriter createBsonWriter(OutputStream bsonOutput) throws IOException;

    @Override
    public <T> T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        final Deserializer<? extends T> deserializer = this.registry.findDeserializer(type);
        return deserializer.deserialize(JsonNodeDecoder.create(tree), registry.newDecoderContext(null), type);
    }

    @Override
    public <T> T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        return readValue(toByteBuffer(inputStream), type);
    }

    @Override
    public <T> T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        return readValue(ByteBuffer.wrap(byteArray), type);
    }

    private <T> T readValue(ByteBuffer byteBuffer, Argument<T> type) throws IOException {
        try (BsonReader bsonReader = createBsonReader(byteBuffer)) {
            return readValue(bsonReader, type);
        }
    }

    private <T> T readValue(BsonReader bsonReader, Argument<T> type) throws IOException {
        return registry.findDeserializer(type).deserialize(new BsonReaderDecoder(bsonReader), registry.newDecoderContext(null), type);
    }

    @Override
    public Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe,
                                                            boolean streamArray) {
        return new SimpleBufferingJsonNodeProcessor(onSubscribe, streamArray) {
            @NonNull
            @Override
            protected JsonNode parseOne(@NonNull InputStream is) throws IOException {
                try (BsonReader bsonReader = createBsonReader(toByteBuffer(is))) {
                    final BsonReaderDecoder decoder = new BsonReaderDecoder(bsonReader);
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
        try (AbstractBsonWriter bsonWriter = createBsonWriter(outputStream)) {
            if (object == null) {
                bsonWriter.writeNull();
            } else {
                BsonWriterEncoder encoder = new BsonWriterEncoder(bsonWriter, false);
                serialize(encoder, object);
            }
            bsonWriter.flush();
        }
    }

    private void serialize(Encoder encoder, Object object) throws IOException {
        serialize(encoder, object, Argument.of(object.getClass()));
    }

    private void serialize(Encoder encoder, Object object, Argument type) throws IOException {
        final Serializer<Object> serializer = registry.findSerializer(type);
        serializer.serialize(encoder, registry.newEncoderContext(null), object, type);
    }

    @Override
    public byte[] writeValueAsBytes(Object object) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, object);
        return output.toByteArray();
    }

    @Override
    public JsonStreamConfig getStreamConfig() {
        return JsonStreamConfig.DEFAULT;
    }

    private ByteBuffer toByteBuffer(InputStream inputStream) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(inputStream.available());
        ReadableByteChannel channel = Channels.newChannel(inputStream);
        while (true) {
            int read = channel.read(byteBuffer);
            if (read == 0) {
                throw new IllegalStateException("Read only 0 bytes!");
            }
            if (read == -1) {
                return byteBuffer;
            }
        }
    }
}
