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

import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.config.SerdeConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.AbstractBsonWriter;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.BsonReader;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Binary Bson mapper.
 *
 * @author Denis Stepanov
 */
@Singleton
@Order(200) // lower precedence than BsonJsonMapper
public final class BsonBinaryMapper extends AbstractBsonMapper {

    private static final int MAX_OUTPUT_SIZE_HINT = 64 * 1024;

    /**
     * The initial size of the buffer of {@code writeValueAsBytes}, grown to fit the documents written.
     */
    private int outputSizeHint = BsonOutputBuffer.INITIAL_SIZE;

    @Inject
    public BsonBinaryMapper(SerdeRegistry registry, SerdeConfiguration serdeConfiguration) {
        super(registry, serdeConfiguration);
    }

    public BsonBinaryMapper(SerdeRegistry registry, @Nullable SerdeConfiguration serdeConfiguration, @Nullable Class<?> view) {
        super(registry, serdeConfiguration, view);
    }

    @Override
    public JsonMapper cloneWithViewClass(Class<?> viewClass) {
        return new BsonBinaryMapper(registry, serdeConfiguration, viewClass);
    }

    @Override
    protected BsonReader createBsonReader(ByteBuffer byteBuffer) {
        return new BsonBinaryReader(byteBuffer);
    }

    @Override
    protected AbstractBsonWriter createBsonWriter(OutputStream os) {
        Objects.requireNonNull(os, "Output stream cannot be null");
        return new BsonBinaryWriter(new BsonOutputBuffer()) {
            @Override
            public void flush() {
                try {
                    ((BsonOutputBuffer) getBsonOutput()).pipe(os);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        return encodeToBytes(null, object);
    }

    @Override
    public <T> byte[] writeValueAsBytes(Argument<T> type, @Nullable T object) throws IOException {
        return encodeToBytes(type, object);
    }

    /**
     * Builds the document in one buffer, sized from the previous documents, and copies it out once.
     * Going through {@link #createBsonWriter(OutputStream)} would pipe the buffer into a growing
     * {@link java.io.ByteArrayOutputStream} and copy it again.
     */
    private byte[] encodeToBytes(@Nullable Argument<?> type, @Nullable Object object) throws IOException {
        BsonOutputBuffer buffer = new BsonOutputBuffer(outputSizeHint);
        try (BsonBinaryWriter bsonWriter = new BsonBinaryWriter(buffer)) {
            encodeValue(bsonWriter, type, object);
        }
        int size = buffer.getSize();
        int hint = outputSizeHint;
        if (size > hint) {
            // A racy hint is fine: it only sizes the next buffers
            outputSizeHint = Math.min(size + (size >> 1), MAX_OUTPUT_SIZE_HINT);
        } else if (size < hint >> 2) {
            // Shrinks after a few smaller documents, so that one large document does not size all the next ones
            outputSizeHint = Math.max(hint >> 1, BsonOutputBuffer.INITIAL_SIZE);
        }
        return buffer.toByteArray();
    }
}
