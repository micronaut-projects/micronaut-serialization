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
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.config.SerdeConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.AbstractBsonWriter;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.BsonReader;
import org.bson.io.BasicOutputBuffer;

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

    @Inject
    public BsonBinaryMapper(SerdeRegistry registry, SerdeConfiguration serdeConfiguration) {
        super(registry, serdeConfiguration);
    }

    public BsonBinaryMapper(SerdeRegistry registry, SerdeConfiguration serdeConfiguration, Class<?> view) {
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
        return new BsonBinaryWriter(new BasicOutputBuffer()) {
            @Override
            public void flush() {
                try {
                    ((BasicOutputBuffer) getBsonOutput()).pipe(os);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

}
