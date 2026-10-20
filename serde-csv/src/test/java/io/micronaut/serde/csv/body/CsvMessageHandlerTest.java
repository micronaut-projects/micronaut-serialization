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
package io.micronaut.serde.csv.body;

import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.error.CsvShapeException;
import io.micronaut.serde.csv.mapper.CsvMapper;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvMessageHandlerTest {

    private CsvMapper createCsvMapper(Iterable<String> readResult) {
        return new CsvMapper() {
            @Override
            public <T> Iterable<T> readAll(String value, Class<T> type) throws CsvException {
                return List.of();
            }

            @Override
            public <T> T read(List<String> value, Class<T> type) throws CsvException {
                throw new UnsupportedOperationException("Not yet implemented");
            }

            @Override
            public <T> Iterable<T> read(@NonNull Iterable<List<String>> values, Class<T> type) throws CsvException {
                throw new UnsupportedOperationException("Not yet implemented");
            }

            @Override
            public <T> Iterable<T> read(@NonNull InputStream input, @NonNull String charset, Class<T> type) throws CsvException {
                return (Iterable<T>) readResult;
            }

            @Override
            public <T> @NonNull Iterable<T> read(@NonNull InputStream input, @NonNull Charset charset, @NonNull Class<T> type) throws CsvException {
                return (Iterable<T>) readResult;
            }

            @Override
            public List<String> writeAsLine(Object value) throws CsvException {
                throw new UnsupportedOperationException("Not yet implemented");
            }

            @Override
            public String writeToString(Iterable<?> values) throws CsvException {
                return "name,age\nAlice,30\nBob,25";
            }

            @Override
            public String writeToString(Iterable<?> values, Collection<String> header) throws CsvException {
                throw new UnsupportedOperationException("Not yet implemented");
            }

            @Override
            public void writeValue(OutputStream outputStream, Object value, Charset charset) throws CsvException {
                String csv = "name,age\nAlice,30\nBob,25";
                try {
                    outputStream.write(csv.getBytes(charset));
                } catch (IOException e) {
                    throw new CsvException("Failed to write CSV", e);
                }
            }

            @Override
            public void writeValues(OutputStream outputStream, Iterable<?> values, Charset charset) throws CsvException {
                String csv = "name,age\nAlice,30\nBob,25";
                try {
                    outputStream.write(csv.getBytes(charset));
                } catch (IOException e) {
                    throw new CsvException("Failed to write CSV", e);
                }
            }
        };
    }

    @Test
    void testReadWithIterableType() {
        CsvMessageHandler<List<String>> codec = new CsvMessageHandler<>(createCsvMapper(List.of("Alice", "Bob")));
        ByteArrayInputStream input = new ByteArrayInputStream("name,age\nAlice,30".getBytes());
        Argument<List<String>> type = (Argument<List<String>>) (Argument<?>) Argument.of(List.class, String.class);
        List<String> result = codec.read(type, new MediaType("text/csv"), null, input);
        assertEquals(2, result.size());
        assertEquals("Alice", result.get(0));
        assertEquals("Bob", result.get(1));
    }

    @Test
    void testReadWithNonIterableType() {
        CsvMessageHandler<String> codec = new CsvMessageHandler<>(createCsvMapper(List.of("Alice")));
        ByteArrayInputStream input = new ByteArrayInputStream("name,age\nAlice,30".getBytes());
        Argument<String> type = Argument.of(String.class);
        String result = codec.read(type, new MediaType("text/csv"), null, input);
        assertEquals("Alice", result);
    }

    @Test
    void testReadWithNonIterableTypeThrowsOnMultipleRecords() {
        CsvMessageHandler<String> codec = new CsvMessageHandler<>(createCsvMapper(List.of("Alice", "Bob")));
        ByteArrayInputStream input = new ByteArrayInputStream("name,age\nAlice,30\nBob,25".getBytes());
        Argument<String> type = Argument.of(String.class);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> codec.read(type, new MediaType("text/csv"), null, input));
        CsvShapeException cause = assertInstanceOf(CsvShapeException.class, exception.getCause());
        assertEquals(2, cause.getActualRecordCount());
    }

    @Test
    void testReadWithNonIterableTypeThrowsOnEmptyRecords() {
        CsvMessageHandler<String> codec = new CsvMessageHandler<>(createCsvMapper(List.of()));
        ByteArrayInputStream input = new ByteArrayInputStream("".getBytes());
        Argument<String> type = Argument.of(String.class);
        assertThrows(IllegalArgumentException.class, () -> codec.read(type, new MediaType("text/csv"), null, input));
    }

    @Test
    void testWriteTo() {
        CsvMessageHandler<List<String>> codec = new CsvMessageHandler<>(createCsvMapper(List.of()));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Argument<List<String>> type = (Argument<List<String>>) (Argument<?>) Argument.of(List.class, String.class);
        List<String> data = List.of("Alice", "Bob");
        codec.writeTo(type, new MediaType("text/csv"), data, null, output);
        String result = output.toString(StandardCharsets.UTF_8);
        assertEquals("name,age\nAlice,30\nBob,25", result);
    }

    @Test
    void testWriteToSingleObject() {
        CsvMessageHandler<String> codec = new CsvMessageHandler<>(createCsvMapper(List.of()));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Argument<String> type = Argument.of(String.class);
        codec.writeTo(type, new MediaType("text/csv"), "single", null, output);
        String result = output.toString(StandardCharsets.UTF_8);
        assertEquals("name,age\nAlice,30\nBob,25", result);
    }

    @Test
    void testWriteToWithNullOutputStream() {
        CsvMessageHandler<String> codec = new CsvMessageHandler<>(createCsvMapper(List.of()));
        Argument<String> type = Argument.of(String.class);
        codec.writeTo(type, new MediaType("text/csv"), "single", null, (OutputStream) null);
    }

    @Test
    void testWriteToWithNullType() {
        CsvMessageHandler<String> codec = new CsvMessageHandler<>(createCsvMapper(List.of()));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.writeTo(null, new MediaType("text/csv"), "single", null, output);
        String result = output.toString(Charset.forName("UTF-8"));
        assertEquals("name,age\nAlice,30\nBob,25", result);
    }
}
