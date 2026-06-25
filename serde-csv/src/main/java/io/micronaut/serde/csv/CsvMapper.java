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
package io.micronaut.serde.csv;

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * CSV object mapper.
 */
@Singleton
@Named(CsvMapper.NAME)
public final class CsvMapper implements ObjectMapper {

    public static final String NAME = "csv";
    private final SerdeRegistry registry;
    @Nullable
    private final SerdeConfiguration serdeConfiguration;
    private final SerdeCsvConfiguration csvConfiguration;

    public CsvMapper(SerdeRegistry serdeRegistry,
                     @Nullable SerdeConfiguration serdeConfiguration,
                     @Nullable SerdeCsvConfiguration csvConfiguration) {
        this.registry = serdeRegistry;
        this.serdeConfiguration = serdeConfiguration;
        this.csvConfiguration = csvConfiguration == null ? new SerdeCsvConfiguration() : csvConfiguration;
    }

    @Override
    public SerdeRegistry getSerdeRegistry() {
        return registry;
    }

    @Override
    public <T> @Nullable T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(null);
        Deserializer<? extends T> deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext, type);
        return deserializer.deserializeNullable(JsonNodeDecoder.create(tree, limits()), decoderContext, type);
    }

    @Override
    public @Nullable <T> T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        Objects.requireNonNull(inputStream, "Input stream cannot be null");
        return readValue(inputStream.readAllBytes(), type);
    }

    @Override
    public <T> @Nullable T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        Objects.requireNonNull(byteArray, "Byte array cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        JsonNode tree = CsvConverter.parse(new String(byteArray, StandardCharsets.UTF_8), type, csvConfiguration);
        return readValueFromTree(tree, type);
    }

    @Override
    public JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        return JsonNode.nullNode();
    }

    @Override
    public <T> JsonNode writeValueToTree(Argument<T> type, @Nullable T value) throws IOException {
        return JsonNode.nullNode();
    }

    @Override
    public void writeValue(OutputStream outputStream, @Nullable Object object) throws IOException {

    }

    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, @Nullable T object) throws IOException {

    }

    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        return new byte[0];
    }

    @Override
    public <T> byte[] writeValueAsBytes(Argument<T> type, @Nullable T object) throws IOException {
        return new byte[0];
    }

    @Override
    public JsonStreamConfig getStreamConfig() {
        return JsonStreamConfig.DEFAULT;
    }

    private LimitingStream.RemainingLimits limits() {
        return serdeConfiguration == null ? LimitingStream.DEFAULT_LIMITS : LimitingStream.limitsFromConfiguration(serdeConfiguration);
    }
}
