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
package io.micronaut.serde.jsonp;

import io.micronaut.core.annotation.AnnotationMetadata;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonConfig;
import jakarta.json.JsonException;
import jakarta.json.JsonMergePatch;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonPatch;
import jakarta.json.JsonPatchBuilder;
import jakarta.json.JsonPointer;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerationException;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;
import jakarta.json.stream.JsonParsingException;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.FormatSchema;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.PrettyPrinter;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.core.TreeNode;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.JsonpCharacterEscapes;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static io.micronaut.serde.jsonp.MicronautJsonProvider.PatchBuilder.COPY;
import static io.micronaut.serde.jsonp.MicronautJsonProvider.PatchBuilder.MOVE;

/**
 * Micronaut-native Jakarta JSON-P provider.
 *
 * @since 3.1.0
 */
public final class MicronautJsonProvider extends JsonProvider {
    private static final JsonFactory JSON_FACTORY = JsonFactory.builder()
        .characterEscapes(JsonpCharacterEscapes.instance())
        .build();

    @Override
    public JsonParser createParser(Reader reader) {
        try {
            return new JacksonJsonParser(JSON_FACTORY.createParser(ObjectReadContext.empty(), reader), true);
        } catch (JacksonException e) {
            throw parsing("Cannot create JSON parser", e);
        }
    }

    @Override
    public JsonParser createParser(InputStream in) {
        try {
            return new JacksonJsonParser(JSON_FACTORY.createParser(ObjectReadContext.empty(), in), true);
        } catch (JacksonException e) {
            throw parsing("Cannot create JSON parser", e);
        }
    }

    @Override
    public JsonParserFactory createParserFactory(Map<String, ?> config) {
        return new ParserFactory(copyConfig(config));
    }

    @Override
    public JsonGenerator createGenerator(Writer writer) {
        return createGeneratorFactory(Map.of()).createGenerator(writer);
    }

    @Override
    public JsonGenerator createGenerator(OutputStream out) {
        return createGeneratorFactory(Map.of()).createGenerator(out);
    }

    @Override
    public JsonGeneratorFactory createGeneratorFactory(Map<String, ?> config) {
        return new GeneratorFactory(copyConfig(config, JsonGenerator.PRETTY_PRINTING));
    }

    @Override
    public JsonReader createReader(Reader reader) {
        return createReaderFactory(Map.of()).createReader(reader);
    }

    @Override
    public JsonReader createReader(InputStream in) {
        return createReaderFactory(Map.of()).createReader(in);
    }

    @Override
    public JsonWriter createWriter(Writer writer) {
        return createWriterFactory(Map.of()).createWriter(writer);
    }

    @Override
    public JsonWriter createWriter(OutputStream out) {
        return createWriterFactory(Map.of()).createWriter(out);
    }

    @Override
    public JsonWriterFactory createWriterFactory(Map<String, ?> config) {
        return new WriterFactory(copyConfig(config, JsonGenerator.PRETTY_PRINTING));
    }

    @Override
    public JsonReaderFactory createReaderFactory(Map<String, ?> config) {
        return new ReaderFactory(copyConfig(config, JsonConfig.KEY_STRATEGY));
    }

    @Override
    public JsonObjectBuilder createObjectBuilder() {
        return new ObjectBuilder();
    }

    @Override
    public JsonObjectBuilder createObjectBuilder(JsonObject object) {
        ObjectBuilder builder = new ObjectBuilder();
        object.forEach(builder::add);
        return builder;
    }

    @Override
    public JsonObjectBuilder createObjectBuilder(Map<String, ?> map) {
        ObjectBuilder builder = new ObjectBuilder();
        map.forEach((key, value) -> builder.add(key, Values.from(value)));
        return builder;
    }

    @Override
    public JsonArrayBuilder createArrayBuilder() {
        return new ArrayBuilder();
    }

    @Override
    public JsonArrayBuilder createArrayBuilder(JsonArray array) {
        ArrayBuilder builder = new ArrayBuilder();
        array.forEach(builder::add);
        return builder;
    }

    @Override
    public JsonArrayBuilder createArrayBuilder(Collection<?> collection) {
        ArrayBuilder builder = new ArrayBuilder();
        collection.forEach(value -> builder.add(Values.from(value)));
        return builder;
    }

    @Override
    public JsonBuilderFactory createBuilderFactory(Map<String, ?> config) {
        return new BuilderFactory(copyConfig(config));
    }

    @Override
    public JsonPointer createPointer(String jsonPointer) {
        return new Pointer(jsonPointer);
    }

    @Override
    public JsonPatchBuilder createPatchBuilder() {
        return new PatchBuilder();
    }

    @Override
    public JsonPatchBuilder createPatchBuilder(JsonArray array) {
        return new PatchBuilder(array);
    }

    @Override
    public JsonPatch createPatch(JsonArray array) {
        return new Patch(array);
    }

    @Override
    public JsonPatch createDiff(JsonStructure source, JsonStructure target) {
        if (source.equals(target)) {
            return new Patch(new JsonArrayValue(List.of()));
        }
        return new PatchBuilder().replace("", target).build();
    }

    @Override
    public JsonMergePatch createMergePatch(JsonValue patch) {
        return new MergePatch(patch);
    }

    @Override
    public JsonMergePatch createMergeDiff(JsonValue source, JsonValue target) {
        if (source.equals(target)) {
            return new MergePatch(new JsonObjectValue(Map.of()));
        }
        if (source instanceof JsonObject sourceObject && target instanceof JsonObject targetObject) {
            ObjectBuilder diff = new ObjectBuilder();
            for (Map.Entry<String, JsonValue> entry : sourceObject.entrySet()) {
                if (!targetObject.containsKey(entry.getKey())) {
                    diff.add(entry.getKey(), JsonValue.NULL);
                }
            }
            for (Map.Entry<String, JsonValue> entry : targetObject.entrySet()) {
                JsonValue sourceValue = sourceObject.get(entry.getKey());
                JsonValue targetValue = entry.getValue();
                if (sourceValue == null) {
                    diff.add(entry.getKey(), targetValue);
                } else if (!sourceValue.equals(targetValue)) {
                    diff.add(entry.getKey(), createMergeDiff(sourceValue, targetValue).toJsonValue());
                }
            }
            return new MergePatch(diff.build());
        }
        return new MergePatch(target);
    }

    @Override
    public JsonString createValue(String value) {
        return new JsonStringValue(value);
    }

    @Override
    public JsonNumber createValue(int value) {
        return new JsonNumberValue(BigDecimal.valueOf(value));
    }

    @Override
    public JsonNumber createValue(long value) {
        return new JsonNumberValue(BigDecimal.valueOf(value));
    }

    @Override
    public JsonNumber createValue(double value) {
        return new JsonNumberValue(BigDecimal.valueOf(value));
    }

    @Override
    public JsonNumber createValue(BigDecimal value) {
        return new JsonNumberValue(value);
    }

    @Override
    public JsonNumber createValue(BigInteger value) {
        return new JsonNumberValue(new BigDecimal(value));
    }

    @Override
    public JsonNumber createValue(Number value) {
        return Values.number(value);
    }

    private static Map<String, ?> copyConfig(@Nullable Map<String, ?> config, String... supportedKeys) {
        if (config == null || config.isEmpty()) {
            return Map.of();
        }
        if (supportedKeys.length == 0) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (String supportedKey : supportedKeys) {
            if (config.containsKey(supportedKey)) {
                copy.put(supportedKey, config.get(supportedKey));
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static JsonParsingException parsing(String message, Throwable e) {
        return new JsonParsingException(message, e, location(e));
    }

    private static JsonGenerationException generation(String message, Throwable e) {
        return new JsonGenerationException(message, e);
    }

    private static JsonLocation location(Throwable e) {
        if (e instanceof StreamReadException streamReadException && streamReadException.getLocation() != null) {
            return toJsonLocation(streamReadException.getLocation());
        }
        return Location.UNKNOWN;
    }

    private static JsonLocation toJsonLocation(TokenStreamLocation location) {
        long offset = location.getCharOffset() >= 0 ? location.getCharOffset() : location.getByteOffset();
        return new Location(location.getLineNr(), location.getColumnNr(), offset);
    }

    private static void writeJsonValue(tools.jackson.core.JsonGenerator generator, JsonValue value) {
        switch (value.getValueType()) {
            case OBJECT -> {
                generator.writeStartObject();
                for (Map.Entry<String, JsonValue> entry : value.asJsonObject().entrySet()) {
                    generator.writeName(entry.getKey());
                    writeJsonValue(generator, entry.getValue());
                }
                generator.writeEndObject();
            }
            case ARRAY -> {
                generator.writeStartArray();
                for (JsonValue item : value.asJsonArray()) {
                    writeJsonValue(generator, item);
                }
                generator.writeEndArray();
            }
            case STRING -> generator.writeString(((JsonString) value).getString());
            case NUMBER -> generator.writeNumber(((JsonNumber) value).bigDecimalValue());
            case TRUE -> generator.writeBoolean(true);
            case FALSE -> generator.writeBoolean(false);
            case NULL -> generator.writeNull();
            default -> throw new JsonException("Unsupported JSON value type: " + value.getValueType());
        }
    }

    private static JsonValue readJsonValue(tools.jackson.core.JsonParser parser, JsonConfig.KeyStrategy keyStrategy) {
        JsonToken token = parser.currentToken();
        if (token == null) {
            token = parser.nextToken();
        }
        if (token == null) {
            throw new JsonException("Expected JSON value");
        }
        return switch (token) {
            case START_OBJECT -> readObject(parser, keyStrategy);
            case START_ARRAY -> readArray(parser, keyStrategy);
            case VALUE_STRING -> new JsonStringValue(parser.getString());
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> new JsonNumberValue(parser.getDecimalValue());
            case VALUE_TRUE -> JsonValue.TRUE;
            case VALUE_FALSE -> JsonValue.FALSE;
            case VALUE_NULL -> JsonValue.NULL;
            default -> throw new JsonException("Unexpected JSON token: " + token);
        };
    }

    private static JsonObject readObject(tools.jackson.core.JsonParser parser, JsonConfig.KeyStrategy keyStrategy) {
        Map<String, JsonValue> values = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.PROPERTY_NAME) {
                throw new JsonException("Expected object key");
            }
            String key = parser.currentName();
            parser.nextToken();
            JsonValue value = readJsonValue(parser, keyStrategy);
            if (values.containsKey(key)) {
                switch (keyStrategy) {
                    case FIRST -> {
                    }
                    case LAST -> values.put(key, value);
                    case NONE -> throw new JsonException("Duplicate key '" + key + "'");
                    default -> throw new JsonException("Unsupported key strategy: " + keyStrategy);
                }
            } else {
                values.put(key, value);
            }
        }
        return new JsonObjectValue(values);
    }

    private static JsonArray readArray(tools.jackson.core.JsonParser parser, JsonConfig.KeyStrategy keyStrategy) {
        List<JsonValue> values = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            values.add(readJsonValue(parser, keyStrategy));
        }
        return new JsonArrayValue(values);
    }

    private record Location(long getLineNumber, long getColumnNumber, long getStreamOffset) implements JsonLocation {
        static final Location UNKNOWN = new Location(-1, -1, -1);
    }

    private record ParserFactory(Map<String, ?> config) implements JsonParserFactory {

        @Override
            public JsonParser createParser(Reader reader) {
                return new MicronautJsonProvider().createParser(reader);
            }

            @Override
            public JsonParser createParser(InputStream in) {
                return new MicronautJsonProvider().createParser(in);
            }

            @Override
            public JsonParser createParser(InputStream in, Charset charset) {
                return createParser(new InputStreamReader(in, charset));
            }

            @Override
            public JsonParser createParser(JsonObject object) {
                return createParser(new StringReader(object.toString()));
            }

            @Override
            public JsonParser createParser(JsonArray array) {
                return createParser(new StringReader(array.toString()));
            }

            @Override
            public Map<String, ?> getConfigInUse() {
                return config;
            }
        }

    private static final class GeneratorFactory implements JsonGeneratorFactory {
        private final Map<String, ?> config;
        private final boolean prettyPrint;

        GeneratorFactory(Map<String, ?> config) {
            this.config = config;
            this.prettyPrint = Boolean.TRUE.equals(config.get(JsonGenerator.PRETTY_PRINTING));
        }

        @Override
        public JsonGenerator createGenerator(Writer writer) {
            try {
                tools.jackson.core.JsonGenerator generator = JSON_FACTORY.createGenerator(writeContext(), writer);
                return new JacksonJsonGenerator(generator);
            } catch (JacksonException e) {
                throw generation("Cannot create JSON generator", e);
            }
        }

        @Override
        public JsonGenerator createGenerator(OutputStream out) {
            try {
                tools.jackson.core.JsonGenerator generator = JSON_FACTORY.createGenerator(writeContext(), out);
                return new JacksonJsonGenerator(generator);
            } catch (JacksonException e) {
                throw generation("Cannot create JSON generator", e);
            }
        }

        @Override
        public JsonGenerator createGenerator(OutputStream out, Charset charset) {
            return createGenerator(new OutputStreamWriter(out, charset));
        }

        @Override
        public Map<String, ?> getConfigInUse() {
            return config;
        }

        private ObjectWriteContext writeContext() {
            return prettyPrint ? PrettyWriteContext.INSTANCE : ObjectWriteContext.empty();
        }
    }

    private static final class PrettyWriteContext extends ObjectWriteContext.Base {
        private static final PrettyWriteContext INSTANCE = new PrettyWriteContext();

        @Override
        public PrettyPrinter getPrettyPrinter() {
            return new DefaultPrettyPrinter();
        }

        @Override
        public boolean hasPrettyPrinter() {
            return true;
        }

        @Override
        public TokenStreamFactory tokenStreamFactory() {
            return JSON_FACTORY;
        }

        @Override
        public @Nullable FormatSchema getSchema() {
            return null;
        }

        @Override
        public void writeTree(tools.jackson.core.JsonGenerator generator, TreeNode tree) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeValue(tools.jackson.core.JsonGenerator generator, Object value) {
            throw new UnsupportedOperationException();
        }
    }

    private record ReaderFactory(Map<String, ?> config) implements JsonReaderFactory {

        @Override
            public JsonReader createReader(Reader reader) {
                return new ReaderImpl(reader, keyStrategy());
            }

            @Override
            public JsonReader createReader(InputStream in) {
                return new ReaderImpl(in, keyStrategy());
            }

            @Override
            public JsonReader createReader(InputStream in, Charset charset) {
                return createReader(new InputStreamReader(in, charset));
            }

            @Override
            public Map<String, ?> getConfigInUse() {
                return config;
            }

            private JsonConfig.KeyStrategy keyStrategy() {
                Object configured = config.get(JsonConfig.KEY_STRATEGY);
                return configured instanceof JsonConfig.KeyStrategy strategy ? strategy : JsonConfig.KeyStrategy.LAST;
            }
        }

    private record WriterFactory(Map<String, ?> config) implements JsonWriterFactory {

        @Override
            public JsonWriter createWriter(Writer writer) {
                return new WriterImpl(new GeneratorFactory(config).createGenerator(writer));
            }

            @Override
            public JsonWriter createWriter(OutputStream out) {
                return createWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            }

            @Override
            public JsonWriter createWriter(OutputStream out, Charset charset) {
                return createWriter(new OutputStreamWriter(out, charset));
            }

            @Override
            public Map<String, ?> getConfigInUse() {
                return config;
            }
        }

    private record BuilderFactory(Map<String, ?> config) implements JsonBuilderFactory {

        @Override
            public JsonObjectBuilder createObjectBuilder() {
                return new ObjectBuilder();
            }

            @Override
            public JsonObjectBuilder createObjectBuilder(JsonObject object) {
                return new MicronautJsonProvider().createObjectBuilder(object);
            }

            @Override
            public JsonObjectBuilder createObjectBuilder(Map<String, Object> map) {
                return new MicronautJsonProvider().createObjectBuilder(map);
            }

            @Override
            public JsonArrayBuilder createArrayBuilder() {
                return new ArrayBuilder();
            }

            @Override
            public JsonArrayBuilder createArrayBuilder(JsonArray array) {
                return new MicronautJsonProvider().createArrayBuilder(array);
            }

            @Override
            public JsonArrayBuilder createArrayBuilder(Collection<?> collection) {
                return new MicronautJsonProvider().createArrayBuilder(collection);
            }

            @Override
            public Map<String, ?> getConfigInUse() {
                return config;
            }
        }

    private static final class ReaderImpl implements JsonReader {
        private final tools.jackson.core.JsonParser parser;
        private final JsonConfig.KeyStrategy keyStrategy;
        private boolean closed;
        private boolean read;

        ReaderImpl(Reader reader, JsonConfig.KeyStrategy keyStrategy) {
            try {
                this.parser = JSON_FACTORY.createParser(ObjectReadContext.empty(), reader);
            } catch (JacksonException e) {
                throw parsing("Cannot create JSON reader", e);
            }
            this.keyStrategy = keyStrategy;
        }

        ReaderImpl(InputStream inputStream, JsonConfig.KeyStrategy keyStrategy) {
            try {
                this.parser = JSON_FACTORY.createParser(ObjectReadContext.empty(), inputStream);
            } catch (JacksonException e) {
                throw parsing("Cannot create JSON reader", e);
            }
            this.keyStrategy = keyStrategy;
        }

        @Override
        public JsonStructure read() {
            JsonValue value = readValue();
            if (value instanceof JsonStructure structure) {
                return structure;
            }
            throw new JsonException("JSON document is not an object or array");
        }

        @Override
        public JsonObject readObject() {
            JsonStructure structure = read();
            if (structure instanceof JsonObject object) {
                return object;
            }
            throw new JsonException("JSON document is not an object");
        }

        @Override
        public JsonArray readArray() {
            JsonStructure structure = read();
            if (structure instanceof JsonArray array) {
                return array;
            }
            throw new JsonException("JSON document is not an array");
        }

        @Override
        public JsonValue readValue() {
            if (closed) {
                throw new IllegalStateException("JsonReader is closed");
            }
            if (read) {
                throw new IllegalStateException("JsonReader can only read one value");
            }
            read = true;
            try {
                return readJsonValue(parser, keyStrategy);
            } catch (JacksonException e) {
                throw parsing("Cannot read JSON", e);
            }
        }

        @Override
        public void close() {
            closed = true;
            try {
                parser.close();
            } catch (JacksonException e) {
                throw parsing("Cannot close JSON reader", e);
            }
        }
    }

    private static final class WriterImpl implements JsonWriter {
        private final JsonGenerator generator;
        private boolean closed;
        private boolean written;

        WriterImpl(JsonGenerator generator) {
            this.generator = generator;
        }

        @Override
        public void writeArray(JsonArray array) {
            checkWritable();
            generator.write(array);
            generator.flush();
            written = true;
        }

        @Override
        public void writeObject(JsonObject object) {
            checkWritable();
            generator.write(object);
            generator.flush();
            written = true;
        }

        @Override
        public void write(JsonStructure value) {
            checkWritable();
            generator.write(value);
            generator.flush();
            written = true;
        }

        @Override
        public void write(JsonValue value) {
            checkWritable();
            generator.write(value);
            generator.flush();
            written = true;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                generator.close();
            }
        }

        private void checkWritable() {
            if (closed) {
                throw new IllegalStateException("JsonWriter is closed");
            }
            if (written) {
                throw new IllegalStateException("JsonWriter can only write one value");
            }
        }
    }

    private static final class JacksonJsonParser implements JsonParser {
        private final tools.jackson.core.JsonParser parser;
        private @Nullable Event currentEvent;
        private @Nullable JsonToken nextToken;

        JacksonJsonParser(tools.jackson.core.JsonParser parser, boolean prefetch) {
            this.parser = parser;
            if (prefetch) {
                try {
                    nextToken = parser.nextToken();
                } catch (JacksonException e) {
                    throw parsing("Cannot read JSON event", e);
                }
            }
        }

        @Override
        public boolean hasNext() {
            if (nextToken != null) {
                return true;
            }
            try {
                nextToken = parser.nextToken();
                return nextToken != null;
            } catch (JacksonException e) {
                throw parsing("Cannot read JSON event", e);
            }
        }

        @Override
        public Event next() {
            JsonToken token = nextToken;
            nextToken = null;
            if (token == null) {
                try {
                    token = parser.nextToken();
                } catch (JacksonException e) {
                    throw parsing("Cannot read JSON event", e);
                }
            }
            if (token == null) {
                throw new NoSuchElementException();
            }
            currentEvent = toEvent(token);
            return currentEvent;
        }

        @Override
        public @Nullable Event currentEvent() {
            return currentEvent;
        }

        @Override
        public String getString() {
            if (currentEvent != Event.KEY_NAME && currentEvent != Event.VALUE_STRING && currentEvent != Event.VALUE_NUMBER) {
                throw new IllegalStateException("Current parser event does not have a string value");
            }
            try {
                return parser.getString();
            } catch (JacksonException e) {
                throw parsing("Cannot read JSON string", e);
            }
        }

        @Override
        public boolean isIntegralNumber() {
            if (currentEvent != Event.VALUE_NUMBER) {
                throw new IllegalStateException("Current parser event is not a number");
            }
            return parser.currentToken() == JsonToken.VALUE_NUMBER_INT;
        }

        @Override
        public int getInt() {
            if (currentEvent != Event.VALUE_NUMBER) {
                throw new IllegalStateException("Current parser event is not a number");
            }
            try {
                return parser.getIntValue();
            } catch (JacksonException e) {
                throw parsing("Cannot read JSON number", e);
            }
        }

        @Override
        public long getLong() {
            if (currentEvent != Event.VALUE_NUMBER) {
                throw new IllegalStateException("Current parser event is not a number");
            }
            try {
                return parser.getLongValue();
            } catch (JacksonException e) {
                throw parsing("Cannot read JSON number", e);
            }
        }

        @Override
        public BigDecimal getBigDecimal() {
            if (currentEvent != Event.VALUE_NUMBER) {
                throw new IllegalStateException("Current parser event is not a number");
            }
            try {
                return parser.getDecimalValue();
            } catch (JacksonException e) {
                throw parsing("Cannot read JSON number", e);
            }
        }

        @Override
        public JsonLocation getLocation() {
            return toJsonLocation(parser.currentLocation());
        }

        @Override
        public JsonObject getObject() {
            JsonValue value = getValue();
            if (value instanceof JsonObject object) {
                return object;
            }
            throw new IllegalStateException("Current parser event is not an object");
        }

        @Override
        public JsonValue getValue() {
            if (currentEvent == null) {
                next();
            }
            if (currentEvent == Event.KEY_NAME) {
                advanceValue();
            }
            if (currentEvent == Event.END_OBJECT || currentEvent == Event.END_ARRAY) {
                throw new IllegalStateException("Current parser event is not a value");
            }
            try {
                return readJsonValue(parser, JsonConfig.KeyStrategy.LAST);
            } catch (JacksonException e) {
                throw parsing("Cannot read JSON value", e);
            }
        }

        @Override
        public JsonArray getArray() {
            JsonValue value = getValue();
            if (value instanceof JsonArray array) {
                return array;
            }
            throw new IllegalStateException("Current parser event is not an array");
        }

        @Override
        public Stream<JsonValue> getArrayStream() {
            return getArray().stream();
        }

        @Override
        public Stream<Map.Entry<String, JsonValue>> getObjectStream() {
            return getObject().entrySet().stream();
        }

        @Override
        public Stream<JsonValue> getValueStream() {
            if (currentEvent == null) {
                return Stream.of(getValue());
            }
            throw new IllegalStateException("Current parser event is not before a document value");
        }

        @Override
        public void skipArray() {
            if (currentEvent == null) {
                next();
            }
            if (currentEvent == Event.KEY_NAME) {
                advanceValue();
            }
            if (currentEvent != Event.START_ARRAY) {
                return;
            }
            try {
                parser.skipChildren();
            } catch (JacksonException e) {
                throw parsing("Cannot skip JSON array", e);
            }
        }

        @Override
        public void skipObject() {
            if (currentEvent == null) {
                next();
            }
            if (currentEvent == Event.KEY_NAME) {
                advanceValue();
            }
            if (currentEvent != Event.START_OBJECT) {
                return;
            }
            try {
                parser.skipChildren();
            } catch (JacksonException e) {
                throw parsing("Cannot skip JSON object", e);
            }
        }

        @Override
        public void close() {
            try {
                parser.close();
            } catch (JacksonException e) {
                throw parsing("Cannot close JSON parser", e);
            }
        }

        private void advanceValue() {
            try {
                JsonToken token = parser.nextToken();
                if (token == null) {
                    throw new IllegalStateException("No JSON value follows the current key");
                }
                currentEvent = toEvent(token);
            } catch (JacksonException e) {
                throw parsing("Cannot read JSON event", e);
            }
        }

        private static Event toEvent(JsonToken token) {
            return switch (token) {
                case START_OBJECT -> Event.START_OBJECT;
                case END_OBJECT -> Event.END_OBJECT;
                case START_ARRAY -> Event.START_ARRAY;
                case END_ARRAY -> Event.END_ARRAY;
                case PROPERTY_NAME -> Event.KEY_NAME;
                case VALUE_STRING -> Event.VALUE_STRING;
                case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> Event.VALUE_NUMBER;
                case VALUE_TRUE -> Event.VALUE_TRUE;
                case VALUE_FALSE -> Event.VALUE_FALSE;
                case VALUE_NULL -> Event.VALUE_NULL;
                default -> throw new JsonException("Unexpected JSON token: " + token);
            };
        }
    }

    @SuppressWarnings("resource")
    private static final class JacksonJsonGenerator implements JsonGenerator {
        private final tools.jackson.core.JsonGenerator generator;
        private final List<Boolean> objectStack = new ArrayList<>();
        private boolean closed;
        private boolean rootWritten;

        JacksonJsonGenerator(tools.jackson.core.JsonGenerator generator) {
            this.generator = generator;
        }

        @Override
        public JsonGenerator writeStartObject() {
            checkOpen();
            checkRootValue();
            try {
                generator.writeStartObject();
                objectStack.add(true);
                return this;
            } catch (JacksonException e) {
                throw generation("Cannot write object", e);
            }
        }

        @Override
        public JsonGenerator writeStartObject(String name) {
            return writeKey(name).writeStartObject();
        }

        @Override
        public JsonGenerator writeKey(String name) {
            checkOpen();
            try {
                generator.writeName(name);
                return this;
            } catch (JacksonException e) {
                throw generation("Cannot write key", e);
            }
        }

        @Override
        public JsonGenerator writeStartArray() {
            checkOpen();
            checkRootValue();
            try {
                generator.writeStartArray();
                objectStack.add(false);
                return this;
            } catch (JacksonException e) {
                throw generation("Cannot write array", e);
            }
        }

        @Override
        public JsonGenerator writeStartArray(String name) {
            return writeKey(name).writeStartArray();
        }

        @Override
        public JsonGenerator write(String name, JsonValue value) {
            return writeKey(name).write(value);
        }

        @Override
        public JsonGenerator write(String name, String value) {
            return writeKey(name).write(value);
        }

        @Override
        public JsonGenerator write(String name, BigInteger value) {
            return writeKey(name).write(value);
        }

        @Override
        public JsonGenerator write(String name, BigDecimal value) {
            return writeKey(name).write(value);
        }

        @Override
        public JsonGenerator write(String name, int value) {
            return writeKey(name).write(value);
        }

        @Override
        public JsonGenerator write(String name, long value) {
            return writeKey(name).write(value);
        }

        @Override
        public JsonGenerator write(String name, double value) {
            return writeKey(name).write(value);
        }

        @Override
        public JsonGenerator write(String name, boolean value) {
            return writeKey(name).write(value);
        }

        @Override
        public JsonGenerator writeNull(String name) {
            return writeKey(name).writeNull();
        }

        @Override
        public JsonGenerator writeEnd() {
            checkOpen();
            try {
                if (objectStack.isEmpty()) {
                    throw new JsonGenerationException("No JSON object or array is currently open");
                }
                if (objectStack.removeLast()) {
                    generator.writeEndObject();
                } else {
                    generator.writeEndArray();
                }
                if (objectStack.isEmpty()) {
                    rootWritten = true;
                }
                return this;
            } catch (JacksonException e) {
                throw generation("Cannot write end token", e);
            }
        }

        @Override
        public JsonGenerator write(JsonValue value) {
            checkOpen();
            checkRootValue();
            try {
                writeJsonValue(generator, value);
                if (objectStack.isEmpty()) {
                    rootWritten = true;
                }
                return this;
            } catch (JacksonException e) {
                throw generation("Cannot write JSON value", e);
            }
        }

        @Override
        public JsonGenerator write(String value) {
            checkOpen();
            checkRootValue();
            try {
                generator.writeString(value);
                if (objectStack.isEmpty()) {
                    rootWritten = true;
                }
                return this;
            } catch (JacksonException e) {
                throw generation("Cannot write string", e);
            }
        }

        @Override
        public JsonGenerator write(BigDecimal value) {
            checkOpen();
            checkRootValue();
            try {
                generator.writeNumber(value);
                if (objectStack.isEmpty()) {
                    rootWritten = true;
                }
                return this;
            } catch (JacksonException e) {
                throw generation("Cannot write number", e);
            }
        }

        @Override
        public JsonGenerator write(BigInteger value) {
            checkOpen();
            checkRootValue();
            try {
                generator.writeNumber(value);
                if (objectStack.isEmpty()) {
                    rootWritten = true;
                }
                return this;
            } catch (JacksonException e) {
                throw generation("Cannot write number", e);
            }
        }

        @Override
        public JsonGenerator write(int value) {
            checkOpen();
            checkRootValue();
            try {
                generator.writeNumber(value);
                if (objectStack.isEmpty()) {
                    rootWritten = true;
                }
                return this;
            } catch (JacksonException e) {
                throw generation("Cannot write number", e);
            }
        }

        @Override
        public JsonGenerator write(long value) {
            checkOpen();
            checkRootValue();
            try {
                generator.writeNumber(value);
                if (objectStack.isEmpty()) {
                    rootWritten = true;
                }
                return this;
            } catch (JacksonException e) {
                throw generation("Cannot write number", e);
            }
        }

        @Override
        public JsonGenerator write(double value) {
            checkOpen();
            checkRootValue();
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("JSON numbers cannot be NaN or infinity");
            }
            try {
                generator.writeNumber(value);
                if (objectStack.isEmpty()) {
                    rootWritten = true;
                }
                return this;
            } catch (JacksonException e) {
                throw generation("Cannot write number", e);
            }
        }

        @Override
        public JsonGenerator write(boolean value) {
            checkOpen();
            checkRootValue();
            try {
                generator.writeBoolean(value);
                if (objectStack.isEmpty()) {
                    rootWritten = true;
                }
                return this;
            } catch (JacksonException e) {
                throw generation("Cannot write boolean", e);
            }
        }

        @Override
        public JsonGenerator writeNull() {
            checkOpen();
            if (objectStack.isEmpty()) {
                throw new JsonGenerationException("No JSON object or array is currently open");
            }
            try {
                generator.writeNull();
                return this;
            } catch (JacksonException e) {
                throw generation("Cannot write null", e);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (!objectStack.isEmpty()) {
                throw new JsonGenerationException("JSON object or array is not closed");
            }
            closed = true;
            try {
                generator.close();
            } catch (JacksonException e) {
                throw generation("Cannot close JSON generator", e);
            }
        }

        @Override
        public void flush() {
            checkOpen();
            try {
                generator.flush();
            } catch (JacksonException e) {
                throw generation("Cannot flush JSON generator", e);
            }
        }

        private void checkOpen() {
            if (closed) {
                throw new JsonGenerationException("JsonGenerator is closed");
            }
        }

        private void checkRootValue() {
            if (objectStack.isEmpty() && rootWritten) {
                throw new JsonGenerationException("JSON document already has a root value");
            }
        }
    }

    private static final class ObjectBuilder implements JsonObjectBuilder {
        private final Map<String, JsonValue> values = new LinkedHashMap<>();

        @Override
        public JsonObjectBuilder add(String name, JsonValue value) {
            values.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        @Override
        public JsonObjectBuilder add(String name, String value) {
            return add(name, new JsonStringValue(Objects.requireNonNull(value, "value")));
        }

        @Override
        public JsonObjectBuilder add(String name, BigInteger value) {
            return add(name, new JsonNumberValue(new BigDecimal(value)));
        }

        @Override
        public JsonObjectBuilder add(String name, BigDecimal value) {
            return add(name, new JsonNumberValue(Objects.requireNonNull(value, "value")));
        }

        @Override
        public JsonObjectBuilder add(String name, int value) {
            return add(name, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        @Override
        public JsonObjectBuilder add(String name, long value) {
            return add(name, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        @Override
        public JsonObjectBuilder add(String name, double value) {
            return add(name, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        @Override
        public JsonObjectBuilder add(String name, boolean value) {
            return add(name, value ? JsonValue.TRUE : JsonValue.FALSE);
        }

        @Override
        public JsonObjectBuilder addNull(String name) {
            return add(name, JsonValue.NULL);
        }

        @Override
        public JsonObjectBuilder add(String name, JsonObjectBuilder builder) {
            return add(name, builder.build());
        }

        @Override
        public JsonObjectBuilder add(String name, JsonArrayBuilder builder) {
            return add(name, builder.build());
        }

        @Override
        public JsonObjectBuilder addAll(JsonObjectBuilder builder) {
            values.putAll(builder.build());
            return this;
        }

        @Override
        public JsonObjectBuilder remove(String name) {
            values.remove(Objects.requireNonNull(name, "name"));
            return this;
        }

        @Override
        public JsonObject build() {
            JsonObjectValue built = new JsonObjectValue(values);
            values.clear();
            return built;
        }
    }

    private static final class ArrayBuilder implements JsonArrayBuilder {
        private final List<JsonValue> values = new ArrayList<>();

        @Override
        public JsonArrayBuilder add(JsonValue value) {
            values.add(Objects.requireNonNull(value, "value"));
            return this;
        }

        @Override
        public JsonArrayBuilder add(String value) {
            return add(new JsonStringValue(Objects.requireNonNull(value, "value")));
        }

        @Override
        public JsonArrayBuilder add(BigDecimal value) {
            return add(new JsonNumberValue(Objects.requireNonNull(value, "value")));
        }

        @Override
        public JsonArrayBuilder add(BigInteger value) {
            return add(new JsonNumberValue(new BigDecimal(value)));
        }

        @Override
        public JsonArrayBuilder add(int value) {
            return add(new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        @Override
        public JsonArrayBuilder add(long value) {
            return add(new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        @Override
        public JsonArrayBuilder add(double value) {
            return add(new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        @Override
        public JsonArrayBuilder add(boolean value) {
            return add(value ? JsonValue.TRUE : JsonValue.FALSE);
        }

        @Override
        public JsonArrayBuilder addNull() {
            return add(JsonValue.NULL);
        }

        @Override
        public JsonArrayBuilder add(JsonObjectBuilder builder) {
            return add(builder.build());
        }

        @Override
        public JsonArrayBuilder add(JsonArrayBuilder builder) {
            return add(builder.build());
        }

        @Override
        public JsonArrayBuilder addAll(JsonArrayBuilder builder) {
            values.addAll(builder.build());
            return this;
        }

        @Override
        public JsonArrayBuilder add(int index, JsonValue value) {
            values.add(index, Objects.requireNonNull(value, AnnotationMetadata.VALUE_MEMBER));
            return this;
        }

        @Override
        public JsonArrayBuilder add(int index, String value) {
            return add(index, new JsonStringValue(Objects.requireNonNull(value, AnnotationMetadata.VALUE_MEMBER)));
        }

        @Override
        public JsonArrayBuilder add(int index, BigDecimal value) {
            return add(index, new JsonNumberValue(Objects.requireNonNull(value, AnnotationMetadata.VALUE_MEMBER)));
        }

        @Override
        public JsonArrayBuilder add(int index, BigInteger value) {
            return add(index, new JsonNumberValue(new BigDecimal(value)));
        }

        @Override
        public JsonArrayBuilder add(int index, int value) {
            return add(index, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        @Override
        public JsonArrayBuilder add(int index, long value) {
            return add(index, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        @Override
        public JsonArrayBuilder add(int index, double value) {
            return add(index, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        @Override
        public JsonArrayBuilder add(int index, boolean value) {
            return add(index, value ? JsonValue.TRUE : JsonValue.FALSE);
        }

        @Override
        public JsonArrayBuilder addNull(int index) {
            return add(index, JsonValue.NULL);
        }

        @Override
        public JsonArrayBuilder add(int index, JsonObjectBuilder builder) {
            return add(index, builder.build());
        }

        @Override
        public JsonArrayBuilder add(int index, JsonArrayBuilder builder) {
            return add(index, builder.build());
        }

        @Override
        public JsonArrayBuilder set(int index, JsonValue value) {
            values.set(index, Objects.requireNonNull(value, AnnotationMetadata.VALUE_MEMBER));
            return this;
        }

        @Override
        public JsonArrayBuilder set(int index, String value) {
            return set(index, new JsonStringValue(Objects.requireNonNull(value, AnnotationMetadata.VALUE_MEMBER)));
        }

        @Override
        public JsonArrayBuilder set(int index, BigDecimal value) {
            return set(index, new JsonNumberValue(Objects.requireNonNull(value, AnnotationMetadata.VALUE_MEMBER)));
        }

        @Override
        public JsonArrayBuilder set(int index, BigInteger value) {
            return set(index, new JsonNumberValue(new BigDecimal(value)));
        }

        @Override
        public JsonArrayBuilder set(int index, int value) {
            return set(index, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        @Override
        public JsonArrayBuilder set(int index, long value) {
            return set(index, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        @Override
        public JsonArrayBuilder set(int index, double value) {
            return set(index, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        @Override
        public JsonArrayBuilder set(int index, boolean value) {
            return set(index, value ? JsonValue.TRUE : JsonValue.FALSE);
        }

        @Override
        public JsonArrayBuilder setNull(int index) {
            return set(index, JsonValue.NULL);
        }

        @Override
        public JsonArrayBuilder set(int index, JsonObjectBuilder builder) {
            return set(index, builder.build());
        }

        @Override
        public JsonArrayBuilder set(int index, JsonArrayBuilder builder) {
            return set(index, builder.build());
        }

        @Override
        public JsonArrayBuilder remove(int index) {
            values.remove(index);
            return this;
        }

        @Override
        public JsonArray build() {
            JsonArrayValue built = new JsonArrayValue(values);
            values.clear();
            return built;
        }
    }

    private static final class JsonObjectValue extends AbstractMap<String, JsonValue> implements JsonObject {
        private final Map<String, JsonValue> values;

        JsonObjectValue(Map<String, JsonValue> values) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        @Override
        public Set<Entry<String, JsonValue>> entrySet() {
            return values.entrySet();
        }

        @Override
        public @Nullable JsonValue get(Object key) {
            return values.get(key);
        }

        @Override
        public boolean containsKey(Object key) {
            return values.containsKey(key);
        }

        @Override
        public @Nullable JsonArray getJsonArray(String name) {
            return (JsonArray) values.get(name);
        }

        @Override
        public @Nullable JsonObject getJsonObject(String name) {
            return (JsonObject) values.get(name);
        }

        @Override
        public @Nullable JsonNumber getJsonNumber(String name) {
            return (JsonNumber) values.get(name);
        }

        @Override
        public @Nullable JsonString getJsonString(String name) {
            return (JsonString) values.get(name);
        }

        @Override
        public String getString(String name) {
            JsonString value = getJsonString(name);
            if (value == null) {
                throw new NullPointerException("No string value for name: " + name);
            }
            return value.getString();
        }

        @Override
        public String getString(String name, String defaultValue) {
            JsonValue value = values.get(name);
            return value instanceof JsonString string ? string.getString() : defaultValue;
        }

        @Override
        public int getInt(String name) {
            JsonNumber value = getJsonNumber(name);
            if (value == null) {
                throw new NullPointerException("No number value for name: " + name);
            }
            return value.intValue();
        }

        @Override
        public int getInt(String name, int defaultValue) {
            JsonValue value = values.get(name);
            return value instanceof JsonNumber number ? number.intValue() : defaultValue;
        }

        @Override
        public boolean getBoolean(String name) {
            JsonValue value = values.get(name);
            if (value == null) {
                throw new NullPointerException("No boolean value for name: " + name);
            }
            if (value == JsonValue.TRUE) {
                return true;
            }
            if (value == JsonValue.FALSE) {
                return false;
            }
            throw new ClassCastException("Value is not a boolean");
        }

        @Override
        public boolean getBoolean(String name, boolean defaultValue) {
            JsonValue value = values.get(name);
            if (value == JsonValue.TRUE) {
                return true;
            }
            if (value == JsonValue.FALSE) {
                return false;
            }
            return defaultValue;
        }

        @Override
        public boolean isNull(String name) {
            if (!values.containsKey(name)) {
                throw new NullPointerException("No value for name: " + name);
            }
            return values.get(name) == JsonValue.NULL;
        }

        @Override
        public ValueType getValueType() {
            return ValueType.OBJECT;
        }

        @Override
        public String toString() {
            StringWriter writer = new StringWriter();
            new MicronautJsonProvider().createWriter(writer).writeObject(this);
            return writer.toString();
        }
    }

    private static final class JsonArrayValue extends AbstractList<JsonValue> implements JsonArray {
        private final List<JsonValue> values;

        JsonArrayValue(List<JsonValue> values) {
            this.values = List.copyOf(values);
        }

        @Override
        public JsonValue get(int index) {
            return values.get(index);
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public JsonObject getJsonObject(int index) {
            return (JsonObject) values.get(index);
        }

        @Override
        public JsonArray getJsonArray(int index) {
            return (JsonArray) values.get(index);
        }

        @Override
        public JsonNumber getJsonNumber(int index) {
            return (JsonNumber) values.get(index);
        }

        @Override
        public JsonString getJsonString(int index) {
            return (JsonString) values.get(index);
        }

        @Override
        public <T extends JsonValue> List<T> getValuesAs(Class<T> clazz) {
            Objects.requireNonNull(clazz, "clazz");
            @SuppressWarnings("unchecked")
            List<T> result = (List<T>) values;
            return result;
        }

        @Override
        public String getString(int index) {
            return getJsonString(index).getString();
        }

        @Override
        public String getString(int index, String defaultValue) {
            JsonValue value = index >= 0 && index < values.size() ? values.get(index) : null;
            return value instanceof JsonString string ? string.getString() : defaultValue;
        }

        @Override
        public int getInt(int index) {
            return getJsonNumber(index).intValue();
        }

        @Override
        public int getInt(int index, int defaultValue) {
            JsonValue value = index >= 0 && index < values.size() ? values.get(index) : null;
            return value instanceof JsonNumber number ? number.intValue() : defaultValue;
        }

        @Override
        public boolean getBoolean(int index) {
            JsonValue value = values.get(index);
            if (value == JsonValue.TRUE) {
                return true;
            }
            if (value == JsonValue.FALSE) {
                return false;
            }
            throw new ClassCastException("Value is not a boolean");
        }

        @Override
        public boolean getBoolean(int index, boolean defaultValue) {
            JsonValue value = index >= 0 && index < values.size() ? values.get(index) : null;
            if (value == JsonValue.TRUE) {
                return true;
            }
            if (value == JsonValue.FALSE) {
                return false;
            }
            return defaultValue;
        }

        @Override
        public boolean isNull(int index) {
            return values.get(index) == JsonValue.NULL;
        }

        @Override
        public ValueType getValueType() {
            return ValueType.ARRAY;
        }

        @Override
        public String toString() {
            StringWriter writer = new StringWriter();
            new MicronautJsonProvider().createWriter(writer).writeArray(this);
            return writer.toString();
        }
    }

    private record JsonStringValue(String getString) implements JsonString {
        @Override
        public CharSequence getChars() {
            return getString;
        }

        @Override
        public ValueType getValueType() {
            return ValueType.STRING;
        }

        @Override
        public String toString() {
            StringWriter writer = new StringWriter();
            new MicronautJsonProvider().createGenerator(writer).write(getString).close();
            return writer.toString();
        }

        @Override
        public boolean equals(@Nullable Object object) {
            return object instanceof JsonString jsonString && getString.equals(jsonString.getString());
        }

        @Override
        public int hashCode() {
            return getString.hashCode();
        }
    }

    private record JsonNumberValue(BigDecimal bigDecimalValue) implements JsonNumber {
        @Override
        public boolean isIntegral() {
            return bigDecimalValue.scale() <= 0;
        }

        @Override
        public int intValue() {
            return bigDecimalValue.intValue();
        }

        @Override
        public int intValueExact() {
            return bigDecimalValue.intValueExact();
        }

        @Override
        public long longValue() {
            return bigDecimalValue.longValue();
        }

        @Override
        public long longValueExact() {
            return bigDecimalValue.longValueExact();
        }

        @Override
        public BigInteger bigIntegerValue() {
            return bigDecimalValue.toBigInteger();
        }

        @Override
        public BigInteger bigIntegerValueExact() {
            return bigDecimalValue.toBigIntegerExact();
        }

        @Override
        public double doubleValue() {
            return bigDecimalValue.doubleValue();
        }

        @Override
        public Number numberValue() {
            return isIntegral() ? bigIntegerValue() : bigDecimalValue;
        }

        @Override
        public ValueType getValueType() {
            return ValueType.NUMBER;
        }

        @Override
        public String toString() {
            return bigDecimalValue.toString();
        }

        @Override
        public boolean equals(@Nullable Object object) {
            return object instanceof JsonNumber jsonNumber && bigDecimalValue.compareTo(jsonNumber.bigDecimalValue()) == 0;
        }

        @Override
        public int hashCode() {
            return bigDecimalValue.stripTrailingZeros().hashCode();
        }
    }

    private static final class Values {
        static JsonValue from(@Nullable Object value) {
            switch (value) {
                case null -> {
                    return JsonValue.NULL;
                }
                case JsonValue jsonValue -> {
                    return jsonValue;
                }
                case String string -> {
                    return new JsonStringValue(string);
                }
                case Number number -> {
                    return number(number);
                }
                case Boolean bool -> {
                    return bool ? JsonValue.TRUE : JsonValue.FALSE;
                }
                case Map<?, ?> map -> {
                    ObjectBuilder builder = new ObjectBuilder();
                    map.forEach((key, item) -> builder.add(String.valueOf(key), from(item)));
                    return builder.build();
                }
                case Collection<?> collection -> {
                    ArrayBuilder builder = new ArrayBuilder();
                    collection.forEach(item -> builder.add(from(item)));
                    return builder.build();
                }
                default -> {
                }
            }
            throw new JsonException("Unsupported JSON value type: " + value.getClass().getName());
        }

        static JsonNumber number(Number number) {
            if (number instanceof BigDecimal bigDecimal) {
                return new JsonNumberValue(bigDecimal);
            }
            if (number instanceof BigInteger bigInteger) {
                return new JsonNumberValue(new BigDecimal(bigInteger));
            }
            if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
                return new JsonNumberValue(BigDecimal.valueOf(number.longValue()));
            }
            if (number instanceof Float || number instanceof Double) {
                return new JsonNumberValue(BigDecimal.valueOf(number.doubleValue()));
            }
            return new JsonNumberValue(new BigDecimal(number.toString()));
        }
    }

    private static final class Pointer implements JsonPointer {
        private final String pointer;
        private final List<String> tokens;

        Pointer(String pointer) {
            if (!pointer.isEmpty() && !pointer.startsWith("/")) {
                throw new JsonException("JSON pointer must be empty or start with '/'");
            }
            this.pointer = pointer;
            this.tokens = pointer.isEmpty() ? List.of() : Stream.of(pointer.substring(1).split("/", -1)).map(Pointer::unescape).toList();
        }

        @Override
        public <T extends JsonStructure> T add(T target, JsonValue value) {
            return update(target, value, Operation.ADD);
        }

        @Override
        public <T extends JsonStructure> T remove(T target) {
            return update(target, JsonValue.NULL, Operation.REMOVE);
        }

        @Override
        public <T extends JsonStructure> T replace(T target, JsonValue value) {
            return update(target, value, Operation.REPLACE);
        }

        @Override
        public boolean containsValue(JsonStructure target) {
            try {
                getValue(target);
                return true;
            } catch (JsonException e) {
                return false;
            }
        }

        @Override
        public JsonValue getValue(JsonStructure target) {
            JsonValue current = target;
            for (String token : tokens) {
                if (current instanceof JsonObject object && object.containsKey(token)) {
                    current = object.get(token);
                } else if (current instanceof JsonArray array) {
                    int index = parseIndex(token, array.size());
                    current = array.get(index);
                } else {
                    throw new JsonException("JSON pointer does not resolve: " + pointer);
                }
            }
            return current;
        }

        @Override
        public String toString() {
            return pointer;
        }

        @SuppressWarnings("unchecked")
        private <T extends JsonStructure> T update(T target, JsonValue value, Operation operation) {
            if (tokens.isEmpty()) {
                if (operation == Operation.REMOVE) {
                    throw new JsonException("Cannot remove document root");
                }
                return (T) requireStructure(value);
            }
            return (T) updateValue(target, 0, value, operation);
        }

        private JsonValue updateValue(JsonValue current, int tokenIndex, JsonValue value, Operation operation) {
            List<PathFrame> frames = new ArrayList<>(tokens.size() - tokenIndex - 1);
            for (int i = tokenIndex; i < tokens.size() - 1; i++) {
                String token = tokens.get(i);
                if (current instanceof JsonObject object) {
                    if (!object.containsKey(token)) {
                        throw new JsonException("JSON pointer does not resolve: " + pointer);
                    }
                    frames.add(new ObjectFrame(object, token));
                    current = object.get(token);
                } else if (current instanceof JsonArray array) {
                    int index = parseIndex(token, array.size());
                    frames.add(new ArrayFrame(array, index));
                    current = array.get(index);
                } else {
                    throw new JsonException("JSON pointer does not resolve: " + pointer);
                }
            }

            JsonValue updated = updateLeaf(current, tokens.get(tokens.size() - 1), value, operation);
            for (int i = frames.size() - 1; i >= 0; i--) {
                updated = frames.get(i).withUpdatedChild(updated);
            }
            return updated;
        }

        private JsonValue updateLeaf(JsonValue current, String token, JsonValue value, Operation operation) {
            if (current instanceof JsonObject object) {
                Map<String, JsonValue> copy = new LinkedHashMap<>(object);
                if (operation == Operation.REMOVE) {
                    if (copy.remove(token) == null) {
                        throw new JsonException("JSON pointer does not resolve: " + pointer);
                    }
                } else {
                    if (operation == Operation.REPLACE && !copy.containsKey(token)) {
                        throw new JsonException("JSON pointer does not resolve: " + pointer);
                    }
                    copy.put(token, value);
                }
                return new JsonObjectValue(copy);
            }
            if (current instanceof JsonArray array) {
                List<JsonValue> copy = new ArrayList<>(array);
                int index = "-".equals(token) ? copy.size() : parseIndex(token, copy.size(), operation == Operation.ADD);
                try {
                    switch (operation) {
                        case ADD -> copy.add(index, value);
                        case REPLACE -> copy.set(index, value);
                        case REMOVE -> copy.remove(index);
                        default -> throw new JsonException("Unsupported JSON pointer operation: " + operation);
                    }
                } catch (IndexOutOfBoundsException e) {
                    throw new JsonException("JSON pointer array index is out of bounds: " + token, e);
                }
                return new JsonArrayValue(copy);
            }
            throw new JsonException("JSON pointer does not resolve: " + pointer);
        }

        private static JsonStructure requireStructure(JsonValue value) {
            if (value instanceof JsonStructure structure) {
                return structure;
            }
            throw new JsonException("JSON pointer root replacement must be an object or array");
        }

        private static int parseIndex(String token, int size) {
            return parseIndex(token, size, false);
        }

        private static int parseIndex(String token, int size, boolean allowEnd) {
            try {
                int index = Integer.parseInt(token);
                if (index < 0 || index > size || (!allowEnd && index == size)) {
                    throw new JsonException("JSON pointer array index is out of bounds: " + token);
                }
                return index;
            } catch (NumberFormatException e) {
                throw new JsonException("JSON pointer token is not an array index: " + token, e);
            }
        }

        private static String unescape(String token) {
            return token.replace("~1", "/").replace("~0", "~");
        }

        private sealed interface PathFrame permits ObjectFrame, ArrayFrame {
            JsonValue withUpdatedChild(JsonValue child);
        }

        private record ObjectFrame(JsonObject object, String token) implements PathFrame {
            @Override
            public JsonValue withUpdatedChild(JsonValue child) {
                Map<String, JsonValue> copy = new LinkedHashMap<>(object);
                copy.put(token, child);
                return new JsonObjectValue(copy);
            }
        }

        private record ArrayFrame(JsonArray array, int index) implements PathFrame {
            @Override
            public JsonValue withUpdatedChild(JsonValue child) {
                List<JsonValue> copy = new ArrayList<>(array);
                copy.set(index, child);
                return new JsonArrayValue(copy);
            }
        }

        private enum Operation {
            ADD,
            REPLACE,
            REMOVE
        }
    }

    static final class PatchBuilder implements JsonPatchBuilder {
        public static final String MOVE = "move";
        public static final String COPY = "copy";
        public static final String TEST = "test";
        public static final String FROM = "from";
        public static final String OP = "op";
        public static final String PATH = "path";
        public static final String ADD = "add";
        public static final String REMOVE = "remove";
        public static final String REPLACE = "replace";
        private final ArrayBuilder operations = new ArrayBuilder();

        PatchBuilder() {
        }

        PatchBuilder(JsonArray array) {
            array.forEach(operations::add);
        }

        @Override
        public JsonPatchBuilder add(String path, JsonValue value) {
            return operation(ADD, path, null, value);
        }

        @Override
        public JsonPatchBuilder add(String path, String value) {
            return add(path, new JsonStringValue(value));
        }

        @Override
        public JsonPatchBuilder add(String path, int value) {
            return add(path, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        @Override
        public JsonPatchBuilder add(String path, boolean value) {
            return add(path, value ? JsonValue.TRUE : JsonValue.FALSE);
        }

        @Override
        public JsonPatchBuilder remove(String path) {
            return operation(REMOVE, path, null, null);
        }

        @Override
        public JsonPatchBuilder replace(String path, JsonValue value) {
            return operation(REPLACE, path, null, value);
        }

        @Override
        public JsonPatchBuilder replace(String path, String value) {
            return replace(path, new JsonStringValue(value));
        }

        @Override
        public JsonPatchBuilder replace(String path, int value) {
            return replace(path, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        @Override
        public JsonPatchBuilder replace(String path, boolean value) {
            return replace(path, value ? JsonValue.TRUE : JsonValue.FALSE);
        }

        @Override
        public JsonPatchBuilder move(String path, String from) {
            return operation(MOVE, path, from, null);
        }

        @Override
        public JsonPatchBuilder copy(String path, String from) {
            return operation(COPY, path, from, null);
        }

        @Override
        public JsonPatchBuilder test(String path, JsonValue value) {
            return operation(TEST, path, null, value);
        }

        @Override
        public JsonPatchBuilder test(String path, String value) {
            return test(path, new JsonStringValue(value));
        }

        @Override
        public JsonPatchBuilder test(String path, int value) {
            return test(path, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        @Override
        public JsonPatchBuilder test(String path, boolean value) {
            return test(path, value ? JsonValue.TRUE : JsonValue.FALSE);
        }

        @Override
        public JsonPatch build() {
            return new Patch(operations.build());
        }

        private JsonPatchBuilder operation(String op, String path, @Nullable String from, @Nullable JsonValue value) {
            ObjectBuilder builder = new ObjectBuilder();
            builder.add(OP, op).add(PATH, path);
            if (from != null) {
                builder.add(FROM, from);
            }
            if (value != null) {
                builder.add(AnnotationMetadata.VALUE_MEMBER, value);
            }
            operations.add(builder);
            return this;
        }
    }

    private record Patch(JsonArray operations) implements JsonPatch {

        public static final String ADD = "add";
        public static final String REMOVE = "remove";
        public static final String REPLACE = "replace";
        public static final String TEST = "test";

        @SuppressWarnings("unchecked")
            @Override
            public <T extends JsonStructure> T apply(T target) {
                JsonStructure current = target;
                for (JsonValue item : operations) {
                    JsonObject operation = item.asJsonObject();
                    String op = operation.getString("op");
                    String path = operation.getString("path");
                    Pointer pointer = new Pointer(path);
                    current = switch (op) {
                        case ADD -> pointer.add(current, required(operation));
                        case REMOVE -> pointer.remove(current);
                        case REPLACE -> pointer.replace(current, required(operation));
                        case COPY -> pointer.add(current, new Pointer(operation.getString("from")).getValue(current));
                        case MOVE -> {
                            Pointer from = new Pointer(operation.getString("from"));
                            JsonValue value = from.getValue(current);
                            current = from.remove(current);
                            yield pointer.add(current, value);
                        }
                        case TEST -> {
                            if (!Objects.equals(pointer.getValue(current), operation.get(AnnotationMetadata.VALUE_MEMBER))) {
                                throw new JsonException("JSON patch test operation failed for path " + path);
                            }
                            yield current;
                        }
                        default -> throw new JsonException("Unsupported JSON patch operation: " + op);
                    };
                }
                return (T) current;
            }

            private static JsonValue required(JsonObject object) {
                JsonValue value = object.get(AnnotationMetadata.VALUE_MEMBER);
                if (value == null) {
                    throw new JsonException("JSON patch operation is missing '" + AnnotationMetadata.VALUE_MEMBER + "'");
                }
                return value;
            }

            @Override
            public JsonArray toJsonArray() {
                return operations;
            }
        }

    private record MergePatch(JsonValue patch) implements JsonMergePatch {

        @Override
            public JsonValue apply(JsonValue target) {
                if (!(patch instanceof JsonObject patchObject)) {
                    return patch;
                }
                Map<String, JsonValue> result = target instanceof JsonObject object ? new LinkedHashMap<>(object) : new LinkedHashMap<>();
                for (Map.Entry<String, JsonValue> entry : patchObject.entrySet()) {
                    if (entry.getValue() == JsonValue.NULL) {
                        result.remove(entry.getKey());
                    } else {
                        result.compute(entry.getKey(), (_, old) -> applyMerge(old == null ? JsonValue.NULL : old, entry.getValue()));
                    }
                }
                return new JsonObjectValue(result);
            }

            @Override
            public JsonValue toJsonValue() {
                return patch;
            }

            private JsonValue applyMerge(JsonValue target, JsonValue patchValue) {
                if (patchValue instanceof JsonObject) {
                    return new MergePatch(patchValue).apply(target);
                }
                return patchValue;
            }
        }
}
