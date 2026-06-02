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
import jakarta.json.Json;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.annotation.JsonbNumberFormat;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.json.bind.annotation.JsonbSubtype;
import jakarta.json.bind.annotation.JsonbTypeAdapter;
import jakarta.json.bind.annotation.JsonbTypeDeserializer;
import jakarta.json.bind.annotation.JsonbTypeInfo;
import jakarta.json.bind.annotation.JsonbTypeSerializer;
import jakarta.json.bind.annotation.JsonbVisibility;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.bind.spi.JsonbProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

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
    void reflectionProviderReadsFallbackInputStreamWithoutReadAllBytes() throws Exception {
        JsonbConfig config = new JsonbConfig()
            .setProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY, new RuntimeVisibilityStrategy());

        try (Jsonb jsonb = JsonbBuilder.create(config)) {
            RuntimeVisibilityBook decoded = jsonb.fromJson(new NoReadAllBytesInputStream("{\"visible\":\"read\",\"hidden\":\"secret\"}"), RuntimeVisibilityBook.class);

            assertEquals("read", decoded.visible);
            assertEquals("initial", decoded.hidden);
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
    void reflectionProviderReadsFallbackReaderWithoutTransferTo() throws Exception {
        JsonbConfig config = new JsonbConfig()
            .setProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY, new RuntimeVisibilityStrategy());

        try (Jsonb jsonb = JsonbBuilder.create(config)) {
            RuntimeVisibilityBook decoded = jsonb.fromJson(new NoTransferToReader("{\"visible\":\"read\",\"hidden\":\"secret\"}"), RuntimeVisibilityBook.class);

            assertEquals("read", decoded.visible);
            assertEquals("initial", decoded.hidden);
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
    void reflectionProviderHandlesNonIntrospectedBeanThroughRuntimeIntrospection() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            RuntimeBook book = new RuntimeBook();
            book.title = "The Stand";
            book.ignored = "secret";

            assertEquals("{\"name\":\"The Stand\"}", jsonb.toJson(book));

            RuntimeBook decoded = jsonb.fromJson("{\"name\":\"It\",\"ignored\":\"secret\"}", RuntimeBook.class);
            assertEquals("It", decoded.title);
            assertEquals("initial", decoded.ignored);
        }
    }

    @Test
    void reflectionProviderMapsJsonbMetadataThroughRuntimeIntrospection() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            RuntimeMetadataBook book = new RuntimeMetadataBook();
            book.serialized = "write";
            book.adapted = "value";
            book.amount = new BigDecimal("1234.5");
            SimpleDateFormat utcDayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            utcDayFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            book.day = utcDayFormat.parse("2026-06-01");

            assertEquals(
                "{\"serialized\":\"ser:write\",\"adapted\":\"adapt:value\",\"amount\":\"1,234.50\",\"day\":\"2026-06-01\"}",
                jsonb.toJson(book)
            );

            RuntimeMetadataBook decoded = jsonb.fromJson(
                "{\"serialized\":\"ignored\",\"adapted\":\"adapt:read\",\"amount\":\"1,234.50\",\"day\":\"2026-06-01\",\"deserialized\":\"raw\"}",
                RuntimeMetadataBook.class
            );
            assertEquals("read", decoded.adapted);
            assertEquals("deser:raw", decoded.deserialized);
            assertEquals(0, new BigDecimal("1234.50").compareTo(decoded.amount));
            assertEquals(utcDayFormat.parse("2026-06-01"), decoded.day);
        }
    }

    @Test
    void reflectionProviderUsesJsonbCreatorThroughRuntimeIntrospection() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            RuntimeCreatorBook book = jsonb.fromJson("{\"title\":\"The Stand\",\"qty\":10}", RuntimeCreatorBook.class);

            assertEquals("The Stand", book.title);
            assertEquals(10, book.quantity);
            assertEquals("{\"title\":\"The Stand\",\"qty\":10}", jsonb.toJson(book));
        }
    }

    @Test
    void reflectionProviderUsesRecordsThroughRuntimeIntrospection() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            RuntimeRecordBook book = jsonb.fromJson("{\"title\":\"The Stand\",\"qty\":10}", RuntimeRecordBook.class);

            assertEquals(new RuntimeRecordBook("The Stand", 10), book);
            assertEquals("{\"title\":\"The Stand\",\"qty\":10}", jsonb.toJson(book));
        }
    }

    @Test
    void reflectionProviderUsesVisibilityStrategyThroughRuntimeIntrospection() throws Exception {
        JsonbConfig config = new JsonbConfig()
            .setProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY, new RuntimeVisibilityStrategy());

        try (Jsonb jsonb = JsonbBuilder.create(config)) {
            RuntimeVisibilityBook book = new RuntimeVisibilityBook();
            book.visible = "shown";
            book.hidden = "secret";

            assertEquals("{\"visible\":\"shown\"}", jsonb.toJson(book));

            RuntimeVisibilityBook decoded = jsonb.fromJson("{\"visible\":\"read\",\"hidden\":\"secret\"}", RuntimeVisibilityBook.class);
            assertEquals("read", decoded.visible);
            assertEquals("initial", decoded.hidden);
        }
    }

    @Test
    void reflectionProviderUsesSerdeMetadataForJsonbTypeInfo() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            RuntimeTypedBook book = new RuntimeTypedBook("The Stand", 10);

            assertEquals("{\"kind\":\"book\",\"title\":\"The Stand\",\"qty\":10}", jsonb.toJson(book));

            RuntimeTypedItem decoded = jsonb.fromJson("{\"kind\":\"book\",\"title\":\"It\",\"qty\":20}", RuntimeTypedItem.class);
            RuntimeTypedBook decodedBook = assertInstanceOf(RuntimeTypedBook.class, decoded);
            assertEquals("It", decodedBook.title);
            assertEquals(20, decodedBook.quantity);
        }
    }

    @Test
    void reflectionProviderUsesRuntimeIntrospectionMetadataForJsonbTypeInfo() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            RuntimeFallbackTypedBook book = new RuntimeFallbackTypedBook("The Stand", 10);

            assertEquals("{\"kind\":\"book\",\"title\":\"The Stand\",\"qty\":10}", jsonb.toJson(book));

            RuntimeFallbackTypedItem decoded = jsonb.fromJson("{\"kind\":\"book\",\"title\":\"It\",\"qty\":20}", RuntimeFallbackTypedItem.class);
            RuntimeFallbackTypedBook decodedBook = assertInstanceOf(RuntimeFallbackTypedBook.class, decoded);
            assertEquals("It", decodedBook.title);
            assertEquals(20, decodedBook.quantity);
        }
    }

    @Test
    void generatedProviderSourceDoesNotUseReflectionFallback() throws Exception {
        String source = Files.readString(Path.of("src/main/java/io/micronaut/serde/jsonb/MicronautJsonbProvider.java"));

        for (String forbidden : List.of("Field", "Method", "Constructor", "ParameterizedType", "setAccessible", "getDeclared", "Class.forName", "JsonbReflectionUtil")) {
            assertFalse(source.contains(forbidden), () -> "MicronautJsonbProvider should not use " + forbidden);
        }
    }

    @Test
    void reflectionFallbackDoesNotKeepDuplicateRuntimePropertyModel() throws Exception {
        String source = Files.readString(Path.of("src/main/java/io/micronaut/serde/jsonb/JsonbReflectionUtil.java"));

        assertFalse(source.contains("PropertyModel"));
        assertFalse(source.contains("propertyModels("));
        assertFalse(source.contains("toBean("));
        assertFalse(source.contains("toJsonpValue("));
        assertFalse(source.contains("JsonObjectBuilder"));
        assertFalse(source.contains("JsonArrayBuilder"));
    }

    @Test
    void generatedProviderUsesConfiguredLimitsForGeneratedPaths() throws Exception {
        String source = Files.readString(Path.of("src/main/java/io/micronaut/serde/jsonb/MicronautJsonbProvider.java"));

        assertFalse(methodSource(source, "readGenerated").contains("LimitingStream.DEFAULT_LIMITS"));
        assertFalse(methodSource(source, "writeGenerated").contains("LimitingStream.DEFAULT_LIMITS"));
    }

    @Test
    void configuredMaximumNestingDepthIsEnforcedForGeneratedJsonb() throws Exception {
        JsonbConfig config = new JsonbConfig()
            .setProperty("micronaut.serde.maximum-nesting-depth", 1);

        try (Jsonb jsonb = new MicronautJsonbProvider().create().withConfig(config).build()) {
            assertThrows(JsonbException.class, () -> jsonb.fromJson("{\"value\":{\"value\":\"too-deep\"}}", GeneratedNested.class));
            assertThrows(JsonbException.class, () -> jsonb.toJson(new GeneratedNested(new GeneratedNested(null))));
        }
    }

    @Test
    void configuredMaximumNestingDepthIsEnforcedForReflectionFallbackJsonb() throws Exception {
        JsonbConfig config = new JsonbConfig()
            .setProperty("micronaut.serde.maximum-nesting-depth", 1)
            .setProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY, new RuntimeNestedVisibilityStrategy());

        try (Jsonb jsonb = JsonbBuilder.create(config)) {
            assertThrows(JsonbException.class, () -> jsonb.fromJson("{\"value\":{\"value\":\"too-deep\"}}", RuntimeNested.class));
            RuntimeNested nested = new RuntimeNested();
            nested.value = new RuntimeNested();
            assertThrows(JsonbException.class, () -> jsonb.toJson(nested));
        }
    }

    @Test
    void configuredMaximumNestingDepthIsEnforcedForCustomJsonbSerializerTrees() throws Exception {
        JsonbConfig config = new JsonbConfig()
            .setProperty("micronaut.serde.maximum-nesting-depth", 1)
            .withSerializers(new NestedObjectSerializer());

        try (Jsonb jsonb = JsonbBuilder.create(config)) {
            assertThrows(JsonbException.class, () -> jsonb.toJson(new NestedSerialized()));
        }
    }

    @Test
    void configuredMaximumNestingDepthIsEnforcedForCustomJsonbSerializerJsonValues() throws Exception {
        JsonbConfig config = new JsonbConfig()
            .setProperty("micronaut.serde.maximum-nesting-depth", 1)
            .withSerializers(new NestedJsonValueSerializer());

        try (Jsonb jsonb = JsonbBuilder.create(config)) {
            assertThrows(JsonbException.class, () -> jsonb.toJson(new NestedSerialized()));
        }
    }

    @Test
    void reflectionProviderHotPathsUseRuntimeModelPreflight() throws Exception {
        String source = Files.readString(Path.of("src/main/java/io/micronaut/serde/jsonb/MicronautJsonbReflectionProvider.java"));

        assertFalse(source.contains("ReflectionFallback.validateObjectModel"));
        assertFalse(source.contains("JsonbTypeInfoSupport.validateTypeInfoModel"));
        assertFalse(source.contains("getAnnotation(JsonbVisibility"));
    }

    @Test
    void reflectionProviderValidatesDuplicateNamesThroughRuntimeModel() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            JsonbException exception = assertThrows(JsonbException.class, () -> jsonb.toJson(new DuplicateJsonbNames()));

            assertTrue(exception.getMessage().contains("Duplicate JSON-B property name: same"));
        }
    }

    @Test
    void reflectionProviderValidatesTransientCustomizationThroughRuntimeModel() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            JsonbException exception = assertThrows(JsonbException.class, () -> jsonb.toJson(new TransientCustomizedBook()));

            assertTrue(exception.getMessage().contains("JsonbTransient cannot be combined"));
        }
    }

    @Test
    void reflectionProviderCachesAnnotatedVisibilityStrategyForRepeatedRuntimeMapping() throws Exception {
        CountingRuntimeVisibilityStrategy.INSTANTIATIONS.set(0);

        try (Jsonb jsonb = JsonbBuilder.create()) {
            AnnotatedRuntimeVisibilityBook book = new AnnotatedRuntimeVisibilityBook();
            book.visible = "shown";
            book.hidden = "secret";

            assertEquals("{\"visible\":\"shown\"}", jsonb.toJson(book));
            assertEquals("{\"visible\":\"shown\"}", jsonb.toJson(book));

            AnnotatedRuntimeVisibilityBook decoded = jsonb.fromJson("{\"visible\":\"read\",\"hidden\":\"secret\"}", AnnotatedRuntimeVisibilityBook.class);
            assertEquals("read", decoded.visible);
            assertEquals("initial", decoded.hidden);
        }

        assertEquals(1, CountingRuntimeVisibilityStrategy.INSTANTIATIONS.get());
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
            assertArrayEquals(new byte[] {0, 127, -128}, jsonb.fromJson("{\"value\":[0,127,-128]}", BinaryHolder.class).getValue());
            assertThrows(JsonbException.class, () -> jsonb.fromJson("{\"value\":[128]}", BinaryHolder.class));
            assertThrows(JsonbException.class, () -> jsonb.fromJson("{\"value\":[1.5]}", BinaryHolder.class));
            assertThrows(JsonbException.class, () -> jsonb.fromJson("{\"value\":[\"1\"]}", BinaryHolder.class));
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

        try (Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withStrictIJSON(true))) {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
            calendar.clear();
            calendar.set(1970, Calendar.JANUARY, 1, 0, 0, 0);
            assertEquals("{\"value\":\"1970-01-01T00:00:00Z+00:00\"}", jsonb.toJson(new CalendarHolder(calendar)));
        }
    }

    @Test
    void genericNumberFallbackRejectsIntegerOverflowAndNonIntegralValues() throws Exception {
        Type type = parameterizedType(RuntimeBox.class, Integer.class);

        try (Jsonb jsonb = JsonbBuilder.create()) {
            RuntimeBox<Integer> box = jsonb.fromJson("{\"value\":42}", type);
            assertEquals(42, box.getValue());

            assertThrows(JsonbException.class, () -> jsonb.fromJson("{\"value\":2147483648}", type));
            assertThrows(JsonbException.class, () -> jsonb.fromJson("{\"value\":1.5}", type));
        }
    }

    @Test
    void genericNumberFallbackRejectsByteOverflowAndNonIntegralValues() throws Exception {
        Type type = parameterizedType(RuntimeBox.class, Byte.class);

        try (Jsonb jsonb = JsonbBuilder.create()) {
            RuntimeBox<Byte> box = jsonb.fromJson("{\"value\":127}", type);
            assertEquals((byte) 127, box.getValue());

            assertThrows(JsonbException.class, () -> jsonb.fromJson("{\"value\":128}", type));
            assertThrows(JsonbException.class, () -> jsonb.fromJson("{\"value\":1.5}", type));
        }
    }

    @Test
    void supportsJsonpValuesOptionalArraysPriorityQueuesAndConfiguredCallbacks() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            JsonpHolder holder = new JsonpHolder(
                Json.createObjectBuilder().add("name", "Fred").build(),
                Json.createArrayBuilder().add(1).add(false).build(),
                JsonValue.TRUE
            );

            String json = jsonb.toJson(holder);
            assertEquals("{\"array\":[1,false],\"object\":{\"name\":\"Fred\"},\"value\":true}", json);
            JsonpHolder decoded = jsonb.fromJson(json, JsonpHolder.class);
            assertEquals("Fred", decoded.object.asJsonObject().getString("name"));
            assertEquals(1, decoded.array.asJsonArray().getInt(0));
            assertEquals(JsonValue.TRUE, decoded.value);

            Optional[] optionals = jsonb.fromJson("[\"a\",null,\"b\"]", Optional[].class);
            assertEquals(Optional.of("a"), optionals[0]);
            assertEquals(Optional.empty(), optionals[1]);
            assertEquals("[\"a\",null,\"b\"]", jsonb.toJson(optionals));

            PriorityQueue<Integer> queue = new PriorityQueue<>();
            queue.add(3);
            queue.add(1);
            Type queueType = parameterizedType(PriorityQueue.class, Integer.class);
            assertEquals("[1,3]", jsonb.toJson(queue, queueType));
        }

        JsonbConfig config = new JsonbConfig()
            .withSerializers(new ConfiguredPointSerializer())
            .withDeserializers(new ConfiguredPointDeserializer());
        try (Jsonb jsonb = JsonbBuilder.create(config)) {
            assertEquals("{\"value\":\"ser:a\",\"context\":\"a\",\"nested\":\"raw:a\"}", jsonb.toJson(new ConfiguredPoint("a")));

            ConfiguredPoint point = jsonb.fromJson("{\"value\":\"b\"}", ConfiguredPoint.class);
            assertEquals("deser:b", point.value);

            ConfiguredPointContainer container = new ConfiguredPointContainer(List.of(new ConfiguredPoint("x"), new ConfiguredPoint("y")));
            assertEquals("{\"points\":[{\"value\":\"ser:x\",\"context\":\"x\",\"nested\":\"raw:x\"},{\"value\":\"ser:y\",\"context\":\"y\",\"nested\":\"raw:y\"}]}", jsonb.toJson(container));
        }
    }

    private static Book book() {
        return new Book("The Stand", 10);
    }

    private static String methodSource(String source, String methodName) {
        int start = source.indexOf(methodName + "(");
        assertTrue(start >= 0, () -> "Missing method " + methodName);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char character = source.charAt(i);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i + 1);
                }
            }
        }
        throw new AssertionError("Cannot locate method body for " + methodName);
    }

    private static ParameterizedType parameterizedType(Class<?> rawType, Type... arguments) {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return arguments;
            }

            @Override
            public Type getRawType() {
                return rawType;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
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
    record CalendarHolder(Calendar value) {
    }

    @Serdeable
    record TimeZoneHolder(TimeZone value) {
    }

    @Serdeable
    record GeneratedNested(GeneratedNested value) {
    }

    static final class RuntimeNested {
        private RuntimeNested value;

        protected RuntimeNested() {
        }
    }

    static final class RuntimeNestedVisibilityStrategy implements PropertyVisibilityStrategy {
        @Override
        public boolean isVisible(Field field) {
            return true;
        }

        @Override
        public boolean isVisible(Method method) {
            return false;
        }
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

    static class RuntimeBook {
        private String title;
        private String ignored = "initial";

        protected RuntimeBook() {
        }

        @JsonbProperty("name")
        public String getTitle() {
            return title;
        }

        @JsonbProperty("name")
        public void setTitle(String title) {
            this.title = title;
        }

        @JsonbTransient
        public String getIgnored() {
            return ignored;
        }

        @JsonbTransient
        public void setIgnored(String ignored) {
            this.ignored = ignored;
        }
    }

    @JsonbPropertyOrder({"serialized", "adapted", "amount", "day"})
    static class RuntimeMetadataBook {
        private String serialized;
        private String adapted;
        private BigDecimal amount;
        private Date day;
        private String deserialized;

        protected RuntimeMetadataBook() {
        }

        @JsonbTypeSerializer(PrefixSerializer.class)
        public String getSerialized() {
            return serialized;
        }

        public void setSerialized(String serialized) {
            this.serialized = serialized;
        }

        @JsonbTypeAdapter(PrefixAdapter.class)
        public String getAdapted() {
            return adapted;
        }

        @JsonbTypeAdapter(PrefixAdapter.class)
        public void setAdapted(String adapted) {
            this.adapted = adapted;
        }

        @JsonbNumberFormat("###,##0.00")
        public BigDecimal getAmount() {
            return amount;
        }

        @JsonbNumberFormat("###,##0.00")
        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        @JsonbDateFormat("yyyy-MM-dd")
        public Date getDay() {
            return day;
        }

        @JsonbDateFormat("yyyy-MM-dd")
        public void setDay(Date day) {
            this.day = day;
        }

        public String getDeserialized() {
            return deserialized;
        }

        @JsonbTypeDeserializer(PrefixDeserializer.class)
        public void setDeserialized(String deserialized) {
            this.deserialized = deserialized;
        }
    }

    static final class PrefixAdapter implements JsonbAdapter<String, String> {
        @Override
        public String adaptToJson(String obj) {
            return "adapt:" + obj;
        }

        @Override
        public String adaptFromJson(String obj) {
            return obj.substring("adapt:".length());
        }
    }

    static final class PrefixSerializer implements JsonbSerializer<String> {
        @Override
        public void serialize(String obj, JsonGenerator generator, jakarta.json.bind.serializer.SerializationContext ctx) {
            generator.write("ser:" + obj);
        }
    }

    static final class NestedObjectSerializer implements JsonbSerializer<NestedSerialized> {
        @Override
        public void serialize(NestedSerialized obj, JsonGenerator generator, jakarta.json.bind.serializer.SerializationContext ctx) {
            generator.writeStartObject();
            generator.writeStartObject("nested");
            generator.write("value", true);
            generator.writeEnd();
            generator.writeEnd();
        }
    }

    static final class NestedJsonValueSerializer implements JsonbSerializer<NestedSerialized> {
        @Override
        public void serialize(NestedSerialized obj, JsonGenerator generator, jakarta.json.bind.serializer.SerializationContext ctx) {
            generator.write(Json.createObjectBuilder()
                .add("nested", Json.createObjectBuilder().add("value", true))
                .build());
        }
    }

    static final class NestedSerialized {
    }

    @Serdeable
    static final class JsonpHolder {
        private final JsonValue object;
        private final JsonValue array;
        private final JsonValue value;

        @JsonbCreator
        JsonpHolder(@JsonbProperty("object") JsonValue object,
                    @JsonbProperty("array") JsonValue array,
                    @JsonbProperty("value") JsonValue value) {
            this.object = object;
            this.array = array;
            this.value = value;
        }

        public JsonValue getObject() {
            return object;
        }

        public JsonValue getArray() {
            return array;
        }

        public JsonValue getValue() {
            return value;
        }
    }

    @Serdeable
    static final class ConfiguredPointContainer {
        private final List<ConfiguredPoint> points;

        @JsonbCreator
        ConfiguredPointContainer(@JsonbProperty("points") List<ConfiguredPoint> points) {
            this.points = points;
        }

        public List<ConfiguredPoint> getPoints() {
            return points;
        }
    }

    static final class ConfiguredPoint {
        private final String value;

        ConfiguredPoint(String value) {
            this.value = value;
        }
    }

    static final class ConfiguredPointSerializer implements JsonbSerializer<ConfiguredPoint> {
        @Override
        public void serialize(ConfiguredPoint obj, JsonGenerator generator, jakarta.json.bind.serializer.SerializationContext ctx) {
            generator.writeStartObject();
            generator.write("value", "ser:" + obj.value);
            ctx.serialize("context", obj.value, generator);
            generator.write("nested", "raw:" + obj.value);
            generator.writeEnd();
        }
    }

    static final class ConfiguredPointDeserializer implements JsonbDeserializer<ConfiguredPoint> {
        @Override
        public ConfiguredPoint deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
            String value = null;
            while (parser.hasNext()) {
                JsonParser.Event event = parser.next();
                if (event == JsonParser.Event.KEY_NAME && "value".equals(parser.getString())) {
                    parser.next();
                    value = parser.getString();
                }
            }
            return new ConfiguredPoint("deser:" + value);
        }
    }

    static final class PrefixDeserializer implements JsonbDeserializer<String> {
        @Override
        public String deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
            if (parser.hasNext()) {
                parser.next();
            }
            return "deser:" + parser.getString();
        }
    }

    @JsonbPropertyOrder({"title", "qty"})
    static final class RuntimeCreatorBook {
        private final String title;
        private final int quantity;

        @JsonbCreator
        RuntimeCreatorBook(@JsonbProperty("title") String title, @JsonbProperty("qty") int quantity) {
            this.title = title;
            this.quantity = quantity;
        }

        public String getTitle() {
            return title;
        }

        @JsonbProperty("qty")
        public int getQuantity() {
            return quantity;
        }
    }

    @JsonbPropertyOrder({"title", "qty"})
    record RuntimeRecordBook(@JsonbProperty("title") String title, @JsonbProperty("qty") int quantity) {
    }

    static class RuntimeVisibilityBook {
        private String visible;
        private String hidden = "initial";

        protected RuntimeVisibilityBook() {
        }
    }

    static final class RuntimeVisibilityStrategy implements PropertyVisibilityStrategy {
        @Override
        public boolean isVisible(Field field) {
            return field.getName().equals("visible");
        }

        @Override
        public boolean isVisible(Method method) {
            return false;
        }
    }

    static class RuntimeBox<T> {
        private T value;

        protected RuntimeBox() {
        }

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }
    }

    static final class DuplicateJsonbNames {
        private String first = "one";
        private String second = "two";

        @JsonbProperty("same")
        public String getFirst() {
            return first;
        }

        @JsonbProperty("same")
        public String getSecond() {
            return second;
        }
    }

    static final class TransientCustomizedBook {
        private String name = "The Stand";

        @JsonbTransient
        @JsonbProperty("name")
        public String getName() {
            return name;
        }
    }

    @JsonbVisibility(CountingRuntimeVisibilityStrategy.class)
    static class AnnotatedRuntimeVisibilityBook {
        private String visible;
        private String hidden = "initial";

        protected AnnotatedRuntimeVisibilityBook() {
        }
    }

    static final class CountingRuntimeVisibilityStrategy implements PropertyVisibilityStrategy {
        static final AtomicInteger INSTANTIATIONS = new AtomicInteger();

        CountingRuntimeVisibilityStrategy() {
            INSTANTIATIONS.incrementAndGet();
        }

        @Override
        public boolean isVisible(Field field) {
            return field.getName().equals("visible");
        }

        @Override
        public boolean isVisible(Method method) {
            return false;
        }
    }

    @Serdeable
    @JsonbTypeInfo(
        key = "kind",
        value = @JsonbSubtype(alias = "book", type = RuntimeTypedBook.class)
    )
    abstract static class RuntimeTypedItem {
    }

    @Serdeable
    @JsonbPropertyOrder({"kind", "title", "qty"})
    static final class RuntimeTypedBook extends RuntimeTypedItem {
        private final String title;
        private final int quantity;

        @JsonbCreator
        RuntimeTypedBook(@JsonbProperty("title") String title, @JsonbProperty("qty") int quantity) {
            this.title = title;
            this.quantity = quantity;
        }

        public String getTitle() {
            return title;
        }

        @JsonbProperty("qty")
        public int getQuantity() {
            return quantity;
        }
    }

    @JsonbTypeInfo(
        key = "kind",
        value = @JsonbSubtype(alias = "book", type = RuntimeFallbackTypedBook.class)
    )
    abstract static class RuntimeFallbackTypedItem {
    }

    @JsonbPropertyOrder({"kind", "title", "qty"})
    static final class RuntimeFallbackTypedBook extends RuntimeFallbackTypedItem {
        private final String title;
        private final int quantity;

        @JsonbCreator
        RuntimeFallbackTypedBook(@JsonbProperty("title") String title, @JsonbProperty("qty") int quantity) {
            this.title = title;
            this.quantity = quantity;
        }

        public String getTitle() {
            return title;
        }

        @JsonbProperty("qty")
        public int getQuantity() {
            return quantity;
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
