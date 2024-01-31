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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.support.util.BufferingJsonNodeProcessor;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import org.bson.AbstractBsonWriter;
import org.bson.BsonReader;
import org.reactivestreams.Processor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Abstract Bson mapper.
 *
 * @author Denis Stepanov
 */
@Internal
public abstract class AbstractBsonMapper implements ObjectMapper {
    protected final SerdeRegistry registry;
    @Nullable
    protected final SerdeConfiguration serdeConfiguration;
    protected final Class<?> view;
    protected Serializer.EncoderContext encoderContext;
    protected Deserializer.DecoderContext decoderContext;

    public AbstractBsonMapper(SerdeRegistry registry, SerdeConfiguration serdeConfiguration) {
        this(registry, serdeConfiguration, null);
    }

    protected AbstractBsonMapper(SerdeRegistry registry, SerdeConfiguration serdeConfiguration, Class<?> view) {
        this.registry = registry;
        this.serdeConfiguration = serdeConfiguration;
        this.view = view;
        this.encoderContext = registry.newEncoderContext(view);
        this.decoderContext = registry.newDecoderContext(view);
    }

    protected abstract BsonReader createBsonReader(ByteBuffer byteBuffer);

    protected abstract AbstractBsonWriter createBsonWriter(OutputStream bsonOutput) throws IOException;

    @NonNull
    private LimitingStream.RemainingLimits limits() {
        return serdeConfiguration == null ? LimitingStream.DEFAULT_LIMITS : LimitingStream.limitsFromConfiguration(serdeConfiguration);
    }

    @Override
    public <T> JsonNode writeValueToTree(Argument<T> type, T value) throws IOException {
        JsonNodeEncoder encoder = JsonNodeEncoder.create(limits());
        serialize(encoder, value);
        return encoder.getCompletedValue();
    }

    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, T object) throws IOException {
        try (AbstractBsonWriter bsonWriter = createBsonWriter(outputStream)) {
            if (object == null) {
                bsonWriter.writeNull();
            } else {
                BsonWriterEncoder encoder = new BsonWriterEncoder(bsonWriter, limits());
                serialize(encoder, object, type);
            }
            bsonWriter.flush();
        }
    }

    @Override
    public <T> byte[] writeValueAsBytes(Argument<T> type, T object) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, type, object);
        return output.toByteArray();
    }

    @Override
    public <T> T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        final Deserializer<? extends T> deserializer = this.decoderContext.findDeserializer(type).createSpecific(decoderContext, type);
        return deserializer.deserialize(JsonNodeDecoder.create(tree, limits()), decoderContext, type);
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
        return decoderContext.findDeserializer(type)
                .createSpecific(decoderContext, type)
                .deserialize(new BsonReaderDecoder(bsonReader, limits()), decoderContext, type);
    }

    @Override
    public Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe,
                                                            boolean streamArray) {
        return new BufferingJsonNodeProcessor(onSubscribe, streamArray) {
            @NonNull
            @Override
            protected JsonNode parseOne(@NonNull InputStream is) throws IOException {
                try (BsonReader bsonReader = createBsonReader(toByteBuffer(is))) {
                    final BsonReaderDecoder decoder = new BsonReaderDecoder(bsonReader, limits());
                    final Object o = decoder.decodeArbitrary();
                    return writeValueToTree(o);
                }
            }

            @Override
            protected JsonNode parseOne(byte[] remaining) throws IOException {
                try (BsonReader bsonReader = createBsonReader(ByteBuffer.wrap(remaining))) {
                    final BsonReaderDecoder decoder = new BsonReaderDecoder(bsonReader, limits());
                    final Object o = decoder.decodeArbitrary();
                    return writeValueToTree(o);
                }
            }
        };
    }

    @Override
    public JsonNode writeValueToTree(Object value) throws IOException {
        JsonNodeEncoder encoder = JsonNodeEncoder.create(limits());
        serialize(encoder, value);
        return encoder.getCompletedValue();
    }

    @Override
    public void writeValue(OutputStream outputStream, Object object) throws IOException {
        try (AbstractBsonWriter bsonWriter = createBsonWriter(outputStream)) {
            if (object == null) {
                bsonWriter.writeNull();
            } else {
                BsonWriterEncoder encoder = new BsonWriterEncoder(bsonWriter, limits());
                serialize(encoder, object);
            }
            bsonWriter.flush();
        }
    }

    private void serialize(Encoder encoder, Object object) throws IOException {
        serialize(encoder, object, Argument.of(object.getClass()));
    }

    private void serialize(Encoder encoder, Object object, Argument type) throws IOException {
        final Serializer<Object> serializer = encoderContext.findSerializer(type).createSpecific(encoderContext, type);
        serializer.serialize(encoder, encoderContext, type, object);
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] byteBuffer = new byte[512];
        int nbByteRead /* = 0*/;
        while ((nbByteRead = inputStream.read(byteBuffer)) != -1) {
            // appends buffer
            baos.write(byteBuffer, 0, nbByteRead);
        }
        return ByteBuffer.wrap(baos.toByteArray());
    }
}
