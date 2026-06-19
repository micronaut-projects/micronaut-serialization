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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.config.SerdeConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CSV object mapper.
 */
@Singleton
@Named(CsvMapper.NAME)
public final class CsvMapper implements ObjectMapper {

    public static final String NAME = "csv";
    private final MutableAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();

    public CsvMapper(SerdeRegistry serdeRegistry,
                     SerdeConfiguration serdeConfiguration) {
        support();
    }

    private void support() {
        annotationMetadata.addAnnotation(Format.class.getName(),
            Map.of(AnnotationMetadata.VALUE_MEMBER, "CSV"));
    }

    @Override
    public @Nullable <T> T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        return null;
    }

    @Override
    public @Nullable <T> T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        Objects.requireNonNull(inputStream, "Input stream cannot be null");
        return readValue(inputStream.readAllBytes(), type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable <T> T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        Objects.requireNonNull(byteArray, "Byte array cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        if (!List.class.isAssignableFrom(type.getType())) {
            throw new IOException("CSV mapper only supports List targets");
        }
        return (T) CsvConverter.parse(new String(byteArray, StandardCharsets.UTF_8), type);
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
}
