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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvMessageHandlerTest {

    @SafeVarargs
    private static <E> Iterable<E> lazyIterable(E... values) {
        return () -> List.of(values).iterator();
    }

    @SuppressWarnings("unchecked")
    private static Argument<Object> argument(Class<?> type, Class<?>... parameters) {
        return (Argument<Object>) (Argument<?>) Argument.of(type, parameters);
    }

    @SuppressWarnings("unchecked")
    private CsvMapper createCsvMapper(Iterable<String> readResult) {
        return new CsvMapper() {
            @Override
            public <T> @NonNull Iterable<T> readAll(@NonNull String value, @NonNull Class<T> type) {
                return List.of();
            }

            @Override
            public <T> @NonNull T read(@NonNull List<String> value, @NonNull Class<T> type) {
                throw new UnsupportedOperationException("Not yet implemented");
            }

            @Override
            public <T> @NonNull Iterable<T> read(@NonNull Iterable<List<String>> values, @NonNull Class<T> type) throws CsvException {
                throw new UnsupportedOperationException("Not yet implemented");
            }

            @Override
            public <T> @NonNull Iterable<T> read(@NonNull InputStream input, @NonNull String charset, @NonNull Class<T> type) {
                return (Iterable<T>) readResult;
            }

            @Override
            public <T> @NonNull Iterable<T> read(@NonNull InputStream input, @NonNull Charset charset, @NonNull Class<T> type) {
                return (Iterable<T>) readResult;
            }

            @Override
            public @NonNull List<String> writeAsLine(Object value) {
                throw new UnsupportedOperationException("Not yet implemented");
            }

            @Override
            public @NonNull String writeToString(@NonNull Iterable<?> values) {
                return "name,age\nAlice,30\nBob,25";
            }

            @Override
            public @NonNull String writeToString(@NonNull Iterable<?> values, @NonNull Collection<String> header) {
                throw new UnsupportedOperationException("Not yet implemented");
            }

            @Override
            public void writeValue(@NonNull OutputStream outputStream, Object value, @NonNull Charset charset) throws CsvException {
                String csv = "name,age\nAlice,30\nBob,25";
                try {
                    outputStream.write(csv.getBytes(charset));
                } catch (IOException e) {
                    throw new CsvException("Failed to write CSV", e);
                }
            }

            @Override
            public void writeValues(@NonNull OutputStream outputStream, @NonNull Iterable<?> values, @NonNull Charset charset) throws CsvException {
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
        Iterable<String> values = lazyIterable("Alice", "Bob");
        CsvMessageHandler<List<String>> codec = new CsvMessageHandler<>(createCsvMapper(values));
        ByteArrayInputStream input = new ByteArrayInputStream("name,age\nAlice,30".getBytes(StandardCharsets.UTF_8));
        Argument<List<String>> type = Argument.listOf(String.class);
        List<String> result = codec.read(type, MediaType.TEXT_CSV_TYPE, null, input);
        assertInstanceOf(ArrayList.class, result);
        assertEquals(2, result.size());
        assertEquals("Alice", result.get(0));
        assertEquals("Bob", result.get(1));
    }

    @Test
    void testReadWithSetType() {
        Iterable<String> values = lazyIterable("Alice", "Bob");
        CsvMessageHandler<Set<String>> codec = new CsvMessageHandler<>(createCsvMapper(values));
        ByteArrayInputStream input = new ByteArrayInputStream("name,age\nAlice,30".getBytes(StandardCharsets.UTF_8));
        Argument<Set<String>> type = Argument.setOf(String.class);
        Set<String> result = codec.read(type, MediaType.TEXT_CSV_TYPE, null, input);
        assertInstanceOf(LinkedHashSet.class, result);
        assertEquals(Set.of("Alice", "Bob"), result);
    }

    @Test
    void testReadWithUnsupportedIterableTypeThrows() {
        CsvMessageHandler<Object> codec = new CsvMessageHandler<>(createCsvMapper(lazyIterable("Alice")));
        ByteArrayInputStream input = new ByteArrayInputStream("name,age\nAlice,30".getBytes(StandardCharsets.UTF_8));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> codec.read(argument(SortedSet.class, String.class), MediaType.TEXT_CSV_TYPE, null, input));
        assertTrue(exception.getMessage().contains("SortedSet"));
    }

    @Test
    void testIsReadableForSupportedTypes() {
        CsvMessageHandler<Object> codec = new CsvMessageHandler<>(createCsvMapper(List.of()));
        assertTrue(codec.isReadable(argument(String.class), MediaType.TEXT_CSV_TYPE));
        assertTrue(codec.isReadable(argument(Iterable.class), MediaType.TEXT_CSV_TYPE));
        assertTrue(codec.isReadable(argument(Collection.class, String.class), MediaType.TEXT_CSV_TYPE));
        assertTrue(codec.isReadable(argument(List.class, String.class), MediaType.TEXT_CSV_TYPE));
        assertTrue(codec.isReadable(argument(Set.class, String.class), MediaType.TEXT_CSV_TYPE));
    }

    @Test
    void testIsNotReadableForUnsupportedCollectionType() {
        CsvMessageHandler<Object> codec = new CsvMessageHandler<>(createCsvMapper(List.of()));
        assertFalse(codec.isReadable(argument(SortedSet.class, String.class), MediaType.TEXT_CSV_TYPE));
    }

    @Test
    void testReadWithRawIterableType() {
        Iterable<String> values = lazyIterable("Alice", "Bob");
        CsvMessageHandler<Iterable> codec = new CsvMessageHandler<>(createCsvMapper(values));
        ByteArrayInputStream input = new ByteArrayInputStream("name,age\nAlice,30".getBytes(StandardCharsets.UTF_8));
        Argument<Iterable> type = Argument.of(Iterable.class);
        Iterable<?> result = codec.read(type, MediaType.TEXT_CSV_TYPE, null, input);
        assertSame(values, result);
        var iterator = result.iterator();
        assertEquals("Alice", iterator.next());
        assertEquals("Bob", iterator.next());
    }

    @Test
    void testReadWithNonIterableType() {
        CsvMessageHandler<String> codec = new CsvMessageHandler<>(createCsvMapper(List.of("Alice")));
        ByteArrayInputStream input = new ByteArrayInputStream("name,age\nAlice,30".getBytes(StandardCharsets.UTF_8));
        Argument<String> type = Argument.of(String.class);
        String result = codec.read(type, MediaType.TEXT_CSV_TYPE, null, input);
        assertEquals("Alice", result);
    }

    @Test
    void testReadWithNonIterableTypeThrowsOnMultipleRecords() {
        CsvMessageHandler<String> codec = new CsvMessageHandler<>(createCsvMapper(List.of("Alice", "Bob")));
        ByteArrayInputStream input = new ByteArrayInputStream("name,age\nAlice,30\nBob,25".getBytes(StandardCharsets.UTF_8));
        Argument<String> type = Argument.of(String.class);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> codec.read(type, MediaType.TEXT_CSV_TYPE, null, input));
        CsvShapeException cause = assertInstanceOf(CsvShapeException.class, exception.getCause());
        assertEquals(2, cause.getActualRecordCount());
    }

    @Test
    void testReadWithNonIterableTypeThrowsOnEmptyRecords() {
        CsvMessageHandler<String> codec = new CsvMessageHandler<>(createCsvMapper(List.of()));
        ByteArrayInputStream input = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        Argument<String> type = Argument.of(String.class);
        assertThrows(IllegalArgumentException.class, () -> codec.read(type, MediaType.TEXT_CSV_TYPE, null, input));
    }

    @Test
    void testWriteTo() {
        CsvMessageHandler<List<String>> codec = new CsvMessageHandler<>(createCsvMapper(List.of()));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Argument<List<String>> type = Argument.listOf(String.class);
        List<String> data = List.of("Alice", "Bob");
        codec.writeTo(type, MediaType.TEXT_CSV_TYPE, data, null, output);
        String result = output.toString(StandardCharsets.UTF_8);
        assertEquals("name,age\nAlice,30\nBob,25", result);
    }

    @Test
    void testWriteToSingleObject() {
        CsvMessageHandler<String> codec = new CsvMessageHandler<>(createCsvMapper(List.of()));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Argument<String> type = Argument.of(String.class);
        codec.writeTo(type, MediaType.TEXT_CSV_TYPE, "single", null, output);
        String result = output.toString(StandardCharsets.UTF_8);
        assertEquals("name,age\nAlice,30\nBob,25", result);
    }

    @Test
    void testWriteToWithNullOutputStream() {
        CsvMessageHandler<String> codec = new CsvMessageHandler<>(createCsvMapper(List.of()));
        Argument<String> type = Argument.of(String.class);
        codec.writeTo(type, MediaType.TEXT_CSV_TYPE, "single", null, (OutputStream) null);
    }

    @Test
    void testWriteToWithNullObject() {
        CsvMessageHandler<String> codec = new CsvMessageHandler<>(createCsvMapper(List.of()));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Argument<String> type = Argument.of(String.class);
        codec.writeTo(type, MediaType.TEXT_CSV_TYPE, null, null, output);
        assertEquals("", output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void testWriteToWithNullType() {
        CsvMessageHandler<String> codec = new CsvMessageHandler<>(createCsvMapper(List.of()));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.writeTo(null, MediaType.TEXT_CSV_TYPE, "single", null, output);
        String result = output.toString(StandardCharsets.UTF_8);
        assertEquals("name,age\nAlice,30\nBob,25", result);
    }
}
