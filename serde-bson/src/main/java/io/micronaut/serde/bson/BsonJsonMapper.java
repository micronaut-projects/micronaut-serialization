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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.SerdeRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.AbstractBsonWriter;
import org.bson.BsonReader;
import org.bson.json.JsonMode;
import org.bson.json.JsonReader;
import org.bson.json.JsonWriter;
import org.bson.json.JsonWriterSettings;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Textual JSON Bson mapper.
 *
 * @author Denis Stepanov
 */
@Singleton
@Primary
@BootstrapContextCompatible
@Internal
public final class BsonJsonMapper extends AbstractBsonMapper {

    @Inject
    public BsonJsonMapper(SerdeRegistry registry) {
        super(registry);
    }

    public BsonJsonMapper(SerdeRegistry registry, Class<?> view) {
        super(registry, view);
    }

    @Override
    public JsonMapper cloneWithViewClass(Class<?> viewClass) {
        return new BsonJsonMapper(registry, viewClass);
    }

    @Override
    protected BsonReader createBsonReader(ByteBuffer byteBuffer) {
        return new JsonReader(new String(byteBuffer.array(), StandardCharsets.UTF_8));
    }

    @Override
    protected AbstractBsonWriter createBsonWriter(OutputStream outputStream) {
        return new JsonWriter(new OutputStreamWriter(outputStream), JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build());
    }

}
