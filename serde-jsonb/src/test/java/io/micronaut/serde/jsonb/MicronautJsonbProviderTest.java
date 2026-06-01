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
package io.micronaut.serde.jsonb;

import example.jsonb.AdditionalBook;
import example.jsonb.ExcludedStartupBean;
import io.micronaut.context.ApplicationContext;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.bind.spi.JsonbProvider;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicronautJsonbProviderTest {
    @Test
    void discoversProviderWithoutYasson() {
        JsonbProvider provider = JsonbProvider.provider();
        assertInstanceOf(MicronautJsonbReflectionProvider.class, provider);
        assertInstanceOf(MicronautJsonbProvider.class, provider);
    }

    @Test
    void generatedProviderSourceDoesNotRunApplicationContext() throws Exception {
        String source = Files.readString(Path.of("src/main/java/io/micronaut/serde/jsonb/MicronautJsonbProvider.java"));

        assertFalse(source.contains("ApplicationContext.run"), "MicronautJsonbProvider should use ObjectMapper.create reduced contexts");
    }

    @Test
    void serviceLoadedJsonbUsesReducedContext() throws Exception {
        ExcludedStartupBean.STARTS.set(0);

        try (Jsonb jsonb = JsonbBuilder.create()) {
            assertEquals(book(), jsonb.fromJson("{\"qty\":10,\"title\":\"The Stand\"}", Book.class));
        }

        assertEquals(0, ExcludedStartupBean.STARTS.get());
    }

    @Test
    void standaloneJsonbAdditionalPackagesLoadsCustomSerde() throws Exception {
        JsonbConfig config = new JsonbConfig()
            .setProperty(JsonbConfiguration.ADDITIONAL_PACKAGES, "example.jsonb");

        try (Jsonb jsonb = JsonbBuilder.create(config)) {
            AdditionalBook book = new AdditionalBook("The Stand");

            assertEquals("{\"custom\":\"serde:The Stand\"}", jsonb.toJson(book));
            assertEquals(book, jsonb.fromJson("{\"custom\":\"serde:The Stand\"}", AdditionalBook.class));
        }
    }

    @Test
    void injectedJsonbUsesCurrentContextAndCanBeClosedWithoutClosingContext() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            Jsonb jsonb = context.getBean(Jsonb.class);

            assertEquals("{\"custom\":\"serde:The Stand\"}", jsonb.toJson(new AdditionalBook("The Stand")));

            jsonb.close();

            assertTrue(context.isRunning());
            assertEquals("{\"custom\":\"serde:It\"}", context.getBean(Jsonb.class).toJson(new AdditionalBook("It")));
        }
    }

    @Test
    void injectedJsonbDefaultsToGeneratedProviderAndCanOptIntoReflection() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            Jsonb jsonb = context.getBean(Jsonb.class);

            assertThrows(JsonbException.class, () -> jsonb.toJson(new PlainBook("The Stand")));
        }

        try (ApplicationContext context = ApplicationContext.run(Map.of(JsonbConfiguration.REFLECTION_ENABLED, true))) {
            Jsonb jsonb = context.getBean(Jsonb.class);

            assertEquals("{\"name\":\"The Stand\"}", jsonb.toJson(new PlainBook("The Stand")));
        }
    }

    @Test
    void supportsJsonbReadAndWriteOverloads() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            Book book = new Book("The Stand", 10);

            String json = jsonb.toJson(book);
            assertEquals("{\"qty\":10,\"title\":\"The Stand\"}", json);
            assertEquals(book, jsonb.fromJson(json, Book.class));
            assertEquals(book, jsonb.fromJson(json, (java.lang.reflect.Type) Book.class));
            assertEquals(book, jsonb.fromJson(new StringReader(json), Book.class));
            assertEquals(book, jsonb.fromJson(new StringReader(json), (java.lang.reflect.Type) Book.class));
            assertEquals(book, jsonb.fromJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), Book.class));
            assertEquals(book, jsonb.fromJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), (java.lang.reflect.Type) Book.class));

            StringWriter writer = new StringWriter();
            jsonb.toJson(book, writer);
            assertEquals(json, writer.toString());

            StringWriter typedWriter = new StringWriter();
            jsonb.toJson(book, Book.class, typedWriter);
            assertEquals(json, typedWriter.toString());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            jsonb.toJson(book, outputStream);
            assertEquals(json, outputStream.toString(StandardCharsets.UTF_8));

            ByteArrayOutputStream typedOutputStream = new ByteArrayOutputStream();
            jsonb.toJson(book, Book.class, typedOutputStream);
            assertEquals(json, typedOutputStream.toString(StandardCharsets.UTF_8));
        }
    }

    @Test
    void generatedProviderReadsInputStreamWithoutReadAllBytes() throws Exception {
        try (Jsonb jsonb = new MicronautJsonbProvider().create().build()) {
            String json = "{\"qty\":10,\"title\":\"The Stand\"}";

            assertEquals(book(), jsonb.fromJson(new NoReadAllBytesInputStream(json), Book.class));
        }
    }

    @Test
    void reflectionProviderReadsGeneratedInputStreamWithoutReadAllBytes() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            String json = "{\"qty\":10,\"title\":\"The Stand\"}";

            assertEquals(book(), jsonb.fromJson(new NoReadAllBytesInputStream(json), Book.class));
        }
    }

    @Test
    void generatedProviderReadsReaderWithoutTransferTo() throws Exception {
        try (Jsonb jsonb = new MicronautJsonbProvider().create().build()) {
            String json = "{\"qty\":10,\"title\":\"The Stand\"}";

            assertEquals(book(), jsonb.fromJson(new NoTransferToReader(json), Book.class));
        }
    }

    @Test
    void reflectionProviderReadsGeneratedReaderWithoutTransferTo() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            String json = "{\"qty\":10,\"title\":\"The Stand\"}";

            assertEquals(book(), jsonb.fromJson(new NoTransferToReader(json), Book.class));
        }
    }

    @Test
    void generatedProviderReadsStringDirectly() throws Exception {
        JsonbConfig config = new JsonbConfig().withEncoding(StandardCharsets.UTF_16LE.name());
        try (Jsonb jsonb = new MicronautJsonbProvider().create().withConfig(config).build()) {
            assertEquals(book(), jsonb.fromJson("{\"qty\":10,\"title\":\"The Stand\"}", Book.class));
        }
    }

    @Test
    void generatedProviderWritesWriterDirectly() throws Exception {
        try (Jsonb jsonb = new MicronautJsonbProvider().create().build()) {
            StringWriter writer = new StringWriter();

            jsonb.toJson(book(), writer);

            assertEquals("{\"qty\":10,\"title\":\"The Stand\"}", writer.toString());
        }
    }

    @Test
    void generatedProviderDoesNotFallbackForGeneratedErrors() throws Exception {
        try (Jsonb jsonb = new MicronautJsonbProvider().create().build()) {
            assertThrows(JsonbException.class, () -> jsonb.fromJson("{\"qty\":\"bad\",\"title\":\"The Stand\"}", Book.class));
        }
    }

    @Test
    void reflectionProviderHandlesNonIntrospectedFallback() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            assertEquals("{\"name\":\"The Stand\"}", jsonb.toJson(new PlainBook("The Stand")));
        }
        try (Jsonb jsonb = new MicronautJsonbProvider().create().build()) {
            assertThrows(JsonbException.class, () -> jsonb.toJson(new PlainBook("The Stand")));
        }
    }

    @Test
    void generatedProviderSourceDoesNotUseReflectionFallback() throws Exception {
        String source = Files.readString(Path.of("src/main/java/io/micronaut/serde/jsonb/MicronautJsonbProvider.java"));

        for (String forbidden : List.of("Field", "Method", "Constructor", "ParameterizedType", "setAccessible", "getDeclared", "Class.forName", "ReflectionFallback")) {
            assertFalse(source.contains(forbidden), () -> "MicronautJsonbProvider should not use " + forbidden);
        }
    }

    @Test
    void supportsFormattingAndEncodingConfig() throws Exception {
        JsonbConfig config = new JsonbConfig()
            .withFormatting(true)
            .withEncoding(StandardCharsets.UTF_8.name());

        try (Jsonb jsonb = JsonbBuilder.create(config)) {
            assertTrue(jsonb.toJson(new Book("The Stand", 10)).contains("\n"));
        }
    }

    @Test
    void supportsJsonbScalarMappingsWithSerdeGeneratedBeans() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            assertEquals("{\"value\":\"c\"}", jsonb.toJson(new CharacterHolder('c')));
            assertEquals(new CharacterHolder('c'), jsonb.fromJson("{\"value\":\"c\"}", CharacterHolder.class));

            assertEquals("{\"value\":3.4028235E+38}", jsonb.toJson(new FloatHolder(Float.MAX_VALUE)));
            assertEquals(new FloatHolder(Float.MAX_VALUE), jsonb.fromJson("{\"value\":3.4028235E+38}", FloatHolder.class));

            NumberHolder numberHolder = jsonb.fromJson("{\"value\":0}", NumberHolder.class);
            assertEquals(new BigDecimal("0"), numberHolder.value());
        }
    }

    @Test
    void supportsJsonbBinaryDataStrategiesWithSerdeGeneratedBeans() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withBinaryDataStrategy(BinaryDataStrategy.BYTE))) {
            assertEquals("{\"value\":[84,101,115,116]}", jsonb.toJson(new BinaryHolder("Test".getBytes(StandardCharsets.UTF_8))));
        }

        try (Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withBinaryDataStrategy(BinaryDataStrategy.BASE_64))) {
            assertEquals("{\"value\":\"VGVzdA==\"}", jsonb.toJson(new BinaryHolder("Test".getBytes(StandardCharsets.UTF_8))));
            assertArrayEquals("Test".getBytes(StandardCharsets.UTF_8), jsonb.fromJson("{\"value\":\"VGVzdA==\"}", BinaryHolder.class).getValue());
        }

        try (Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withStrictIJSON(true))) {
            assertEquals("{\"value\":\"VGVzdA==\"}", jsonb.toJson(new BinaryHolder("Test".getBytes(StandardCharsets.UTF_8))));
            assertThrows(jakarta.json.bind.JsonbException.class, () -> jsonb.toJson("Test"));
        }
    }

    @Test
    void supportsJsonbTemporalMappingsWithSerdeGeneratedBeans() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            assertEquals("{\"value\":\"PT1H1S\"}", jsonb.toJson(new DurationHolder(Duration.ofHours(1).plusSeconds(1))));
            assertEquals(new DurationHolder(Duration.ofHours(1).plusSeconds(1)), jsonb.fromJson("{\"value\":\"PT1H1S\"}", DurationHolder.class));

            Date date = Date.from(java.time.Instant.parse("1969-12-31T23:00:00Z"));
            assertEquals("{\"value\":\"1969-12-31T23:00:00Z[UTC]\"}", jsonb.toJson(new DateHolder(date)));
            assertEquals(new DateHolder(date), jsonb.fromJson("{\"value\":\"1969-12-31T23:00:00Z[UTC]\"}", DateHolder.class));

            assertThrows(JsonbException.class, () -> jsonb.fromJson("{\"value\":\"CST\"}", TimeZoneHolder.class));
            assertEquals("UTC", jsonb.fromJson("{\"value\":\"UTC\"}", TimeZoneHolder.class).value().getID());
        }
    }

    private static Book book() {
        return new Book("The Stand", 10);
    }

    private static final class NoReadAllBytesInputStream extends ByteArrayInputStream {
        NoReadAllBytesInputStream(String value) {
            super(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public synchronized byte[] readAllBytes() {
            throw new AssertionError("readAllBytes must not be used");
        }
    }

    private static final class NoTransferToReader extends Reader {
        private final StringReader delegate;

        NoTransferToReader(String value) {
            this.delegate = new StringReader(value);
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            return delegate.read(cbuf, off, len);
        }

        @Override
        public long transferTo(java.io.Writer out) {
            throw new AssertionError("transferTo must not be used");
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    @Serdeable
    record Book(@JsonbProperty("title") String title, @JsonbProperty("qty") int quantity) {
        @JsonbCreator
        Book {
        }
    }

    @Serdeable
    record CharacterHolder(Character value) {
    }

    @Serdeable
    record FloatHolder(Float value) {
    }

    @Serdeable
    record NumberHolder(Number value) {
    }

    @Serdeable
    record DurationHolder(Duration value) {
    }

    @Serdeable
    record DateHolder(Date value) {
    }

    @Serdeable
    record TimeZoneHolder(TimeZone value) {
    }

    static final class PlainBook {
        private final String name;

        PlainBook(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @Serdeable
    static final class BinaryHolder {
        private final byte[] value;

        @JsonbCreator
        BinaryHolder(@JsonbProperty("value") byte[] value) {
            this.value = value;
        }

        public byte[] getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof BinaryHolder that && Arrays.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }
    }
}
