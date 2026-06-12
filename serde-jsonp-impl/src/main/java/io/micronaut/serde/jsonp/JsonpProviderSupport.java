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

import io.micronaut.core.annotation.Internal;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonConfig;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
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
import tools.jackson.core.util.DefaultPrettyPrinter;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import static io.micronaut.serde.jsonp.MicronautJsonProvider.CANNOT_READ_JSON_EVENT;
import static io.micronaut.serde.jsonp.MicronautJsonProvider.CANNOT_READ_JSON_NUMBER;
import static io.micronaut.serde.jsonp.MicronautJsonProvider.CANNOT_WRITE_NUMBER;
import static io.micronaut.serde.jsonp.MicronautJsonProvider.JSON_FACTORY;
import static io.micronaut.serde.jsonp.JsonpValueSupport.ArrayBuilder;
import static io.micronaut.serde.jsonp.JsonpValueSupport.JsonArrayValue;
import static io.micronaut.serde.jsonp.JsonpValueSupport.JsonNumberValue;
import static io.micronaut.serde.jsonp.JsonpValueSupport.JsonObjectValue;
import static io.micronaut.serde.jsonp.JsonpValueSupport.JsonStringValue;
import static io.micronaut.serde.jsonp.JsonpValueSupport.ObjectBuilder;
import static io.micronaut.serde.jsonp.JsonpProviderSupport.generation;
import static io.micronaut.serde.jsonp.JsonpProviderSupport.parsing;
import static io.micronaut.serde.jsonp.JsonpProviderSupport.readJsonValue;
import static io.micronaut.serde.jsonp.JsonpProviderSupport.toJsonLocation;
import static io.micronaut.serde.jsonp.JsonpProviderSupport.writeJsonValue;

/**
 * Implementation classes used by {@link MicronautJsonProvider}.
 */
final class JsonpProviderSupport {
    /**
     * Prevents instantiation of this support holder.
     */
    private JsonpProviderSupport() {
    }

    /**
     * Copies only supported Jakarta JSON-P configuration keys into an immutable map used by factory instances.
     */
    @SuppressWarnings("java:S1452")
    static Map<String, Object> copyConfig(@Nullable Map<String, ?> config, String... supportedKeys) {
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

    /**
     * Wraps Jackson read failures in the JSON-P parsing exception type while preserving stream location metadata.
     */
    static JsonParsingException parsing(String message, Throwable e) {
        return new JsonParsingException(message, e, location(e));
    }

    /**
     * Wraps Jackson write failures in the JSON-P generation exception type expected by Jakarta JSON-P callers.
     */
    static JsonGenerationException generation(String message, Throwable e) {
        return new JsonGenerationException(message, e);
    }

    /**
     * Extracts a JSON-P location from Jackson exceptions, falling back to the shared unknown location sentinel.
     */
    static JsonLocation location(Throwable e) {
        if (e instanceof StreamReadException streamReadException && streamReadException.getLocation() != null) {
            return toJsonLocation(streamReadException.getLocation());
        }
        return Location.UNKNOWN;
    }

    /**
     * Converts Jackson token locations into the Jakarta JSON-P location contract used by parser exceptions.
     */
    static JsonLocation toJsonLocation(TokenStreamLocation location) {
        long offset = location.getCharOffset() >= 0 ? location.getCharOffset() : location.getByteOffset();
        return new Location(location.getLineNr(), location.getColumnNr(), offset);
    }

    /**
     * Writes an immutable JSON-P value tree to the backing Jackson generator without changing provider state.
     */
    static void writeJsonValue(tools.jackson.core.JsonGenerator generator, JsonValue value) {
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

    /**
     * Reads the current Jackson token as an immutable JSON-P value, applying the configured duplicate-key strategy.
     */
    static JsonValue readJsonValue(tools.jackson.core.JsonParser parser, JsonConfig.KeyStrategy keyStrategy) {
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

    /**
     * Reads an object value from the current Jackson parser and applies duplicate-key handling deterministically.
     */
    static JsonObject readObject(tools.jackson.core.JsonParser parser, JsonConfig.KeyStrategy keyStrategy) {
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

    /**
     * Reads an array value from the current Jackson parser into this provider's immutable JSON-P array model.
     */
    static JsonArray readArray(tools.jackson.core.JsonParser parser, JsonConfig.KeyStrategy keyStrategy) {
        List<JsonValue> values = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            values.add(readJsonValue(parser, keyStrategy));
        }
        return new JsonArrayValue(values);
    }

}

@Internal
record Location(long getLineNumber, long getColumnNumber, long getStreamOffset) implements JsonLocation {
    static final Location UNKNOWN = new Location(-1, -1, -1);
}

@Internal
record ParserFactory(Map<String, ?> config) implements JsonParserFactory {

    /**
     * Creates a parser view backed by the provider's Jackson parser implementation.
     */
    @Override
    public JsonParser createParser(Reader reader) {
        return new MicronautJsonProvider().createParser(reader);
    }

    /**
     * Creates a parser view backed by the provider's Jackson parser implementation.
     */
    @Override
    public JsonParser createParser(InputStream in) {
        return new MicronautJsonProvider().createParser(in);
    }

    /**
     * Creates a parser view backed by the provider's Jackson parser implementation.
     */
    @Override
    public JsonParser createParser(InputStream in, Charset charset) {
        return createParser(new InputStreamReader(in, charset));
    }

    /**
     * Creates a parser view backed by the provider's Jackson parser implementation.
     */
    @Override
    public JsonParser createParser(JsonObject object) {
        return createParser(new StringReader(object.toString()));
    }

    /**
     * Creates a parser view backed by the provider's Jackson parser implementation.
     */
    @Override
    public JsonParser createParser(JsonArray array) {
        return createParser(new StringReader(array.toString()));
    }

    /**
     * Returns the immutable configuration snapshot captured when the factory was created.
     */
    @Override
    public Map<String, ?> getConfigInUse() {
        return config;
    }
}

@Internal
final class GeneratorFactory implements JsonGeneratorFactory {
    private final Map<String, ?> config;
    private final boolean prettyPrint;

    /**
     * Initializes this JSON-P support component with the state it owns.
     */
    GeneratorFactory(Map<String, ?> config) {
        this.config = config;
        this.prettyPrint = Boolean.TRUE.equals(config.get(JsonGenerator.PRETTY_PRINTING));
    }

    /**
     * Creates a generator view backed by the provider's Jackson generator implementation.
     */
    @Override
    public JsonGenerator createGenerator(Writer writer) {
        try {
            tools.jackson.core.JsonGenerator generator = JSON_FACTORY.createGenerator(writeContext(), writer);
            return new JacksonJsonGenerator(generator);
        } catch (JacksonException e) {
            throw generation("Cannot create JSON generator", e);
        }
    }

    /**
     * Creates a generator view backed by the provider's Jackson generator implementation.
     */
    @Override
    public JsonGenerator createGenerator(OutputStream out) {
        try {
            tools.jackson.core.JsonGenerator generator = JSON_FACTORY.createGenerator(writeContext(), out);
            return new JacksonJsonGenerator(generator);
        } catch (JacksonException e) {
            throw generation("Cannot create JSON generator", e);
        }
    }

    /**
     * Creates a generator view backed by the provider's Jackson generator implementation.
     */
    @Override
    public JsonGenerator createGenerator(OutputStream out, Charset charset) {
        return createGenerator(new OutputStreamWriter(out, charset));
    }

    /**
     * Returns the immutable configuration snapshot captured when the factory was created.
     */
    @Override
    public Map<String, ?> getConfigInUse() {
        return config;
    }

    /**
     * Selects the Jackson write context that carries pretty-printing state for generated output.
     */
    private ObjectWriteContext writeContext() {
        return prettyPrint ? PrettyWriteContext.INSTANCE : ObjectWriteContext.empty();
    }
}

@Internal
final class PrettyWriteContext extends ObjectWriteContext.Base {
    static final PrettyWriteContext INSTANCE = new PrettyWriteContext();

    /**
     * Supplies the pretty printer used when JSON-P pretty-printing is enabled.
     */
    @Override
    public PrettyPrinter getPrettyPrinter() {
        return new DefaultPrettyPrinter();
    }

    /**
     * Signals that this write context always supplies a pretty printer.
     */
    @Override
    public boolean hasPrettyPrinter() {
        return true;
    }

    /**
     * Returns the provider-wide Jackson factory associated with this write context.
     */
    @Override
    public TokenStreamFactory tokenStreamFactory() {
        return JSON_FACTORY;
    }

    /**
     * Reports that JSON-P output does not use a Jackson format schema.
     */
    @Override
    public @Nullable FormatSchema getSchema() {
        return null;
    }

    /**
     * Rejects Jackson tree/value shortcuts because this provider writes through explicit JSON-P token methods.
     */
    @Override
    public void writeTree(tools.jackson.core.JsonGenerator generator, TreeNode tree) {
        throw new UnsupportedOperationException();
    }

    /**
     * Rejects Jackson tree/value shortcuts because this provider writes through explicit JSON-P token methods.
     */
    @Override
    public void writeValue(tools.jackson.core.JsonGenerator generator, Object value) {
        throw new UnsupportedOperationException();
    }
}

@Internal
record ReaderFactory(Map<String, ?> config) implements JsonReaderFactory {

    /**
     * Creates a single-use JSON-P reader with the factory's duplicate-key strategy.
     */
    @Override
    public JsonReader createReader(Reader reader) {
        return new ReaderImpl(reader, keyStrategy());
    }

    /**
     * Creates a single-use JSON-P reader with the factory's duplicate-key strategy.
     */
    @Override
    public JsonReader createReader(InputStream in) {
        return new ReaderImpl(in, keyStrategy());
    }

    /**
     * Creates a single-use JSON-P reader with the factory's duplicate-key strategy.
     */
    @Override
    public JsonReader createReader(InputStream in, Charset charset) {
        return createReader(new InputStreamReader(in, charset));
    }

    /**
     * Returns the immutable configuration snapshot captured when the factory was created.
     */
    @Override
    public Map<String, ?> getConfigInUse() {
        return config;
    }

    /**
     * Resolves the duplicate-key strategy for readers, defaulting to the Jakarta JSON-P last-key behavior.
     */
    private JsonConfig.KeyStrategy keyStrategy() {
        Object configured = config.get(JsonConfig.KEY_STRATEGY);
        return configured instanceof JsonConfig.KeyStrategy strategy ? strategy : JsonConfig.KeyStrategy.LAST;
    }
}

@Internal
record WriterFactory(Map<String, ?> config) implements JsonWriterFactory {

    /**
     * Creates a single-use JSON-P writer over the configured generator factory.
     */
    @Override
    public JsonWriter createWriter(Writer writer) {
        return new WriterImpl(new GeneratorFactory(config).createGenerator(writer));
    }

    /**
     * Creates a single-use JSON-P writer over the configured generator factory.
     */
    @Override
    public JsonWriter createWriter(OutputStream out) {
        return createWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    /**
     * Creates a single-use JSON-P writer over the configured generator factory.
     */
    @Override
    public JsonWriter createWriter(OutputStream out, Charset charset) {
        return createWriter(new OutputStreamWriter(out, charset));
    }

    /**
     * Returns the immutable configuration snapshot captured when the factory was created.
     */
    @Override
    public Map<String, ?> getConfigInUse() {
        return config;
    }
}

@Internal
record BuilderFactory(Map<String, ?> config) implements JsonBuilderFactory {

    /**
     * Creates or adapts an object builder while keeping builder state isolated from callers.
     */
    @Override
    public JsonObjectBuilder createObjectBuilder() {
        return new ObjectBuilder();
    }

    /**
     * Creates or adapts an object builder while keeping builder state isolated from callers.
     */
    @Override
    public JsonObjectBuilder createObjectBuilder(JsonObject object) {
        return new MicronautJsonProvider().createObjectBuilder(object);
    }

    /**
     * Creates or adapts an object builder while keeping builder state isolated from callers.
     */
    @Override
    public JsonObjectBuilder createObjectBuilder(Map<String, Object> map) {
        return new MicronautJsonProvider().createObjectBuilder(map);
    }

    /**
     * Creates or adapts an array builder while keeping builder state isolated from callers.
     */
    @Override
    public JsonArrayBuilder createArrayBuilder() {
        return new ArrayBuilder();
    }

    /**
     * Creates or adapts an array builder while keeping builder state isolated from callers.
     */
    @Override
    public JsonArrayBuilder createArrayBuilder(JsonArray array) {
        return new MicronautJsonProvider().createArrayBuilder(array);
    }

    /**
     * Creates or adapts an array builder while keeping builder state isolated from callers.
     */
    @Override
    public JsonArrayBuilder createArrayBuilder(Collection<?> collection) {
        return new MicronautJsonProvider().createArrayBuilder(collection);
    }

    /**
     * Returns the immutable configuration snapshot captured when the factory was created.
     */
    @Override
    public Map<String, ?> getConfigInUse() {
        return config;
    }
}

@Internal
final class ReaderImpl implements JsonReader {
    private final tools.jackson.core.JsonParser parser;
    private final JsonConfig.KeyStrategy keyStrategy;
    private boolean closed;
    private boolean read;

    /**
     * Initializes this JSON-P support component with the state it owns.
     */
    ReaderImpl(Reader reader, JsonConfig.KeyStrategy keyStrategy) {
        try {
            this.parser = JSON_FACTORY.createParser(ObjectReadContext.empty(), reader);
        } catch (JacksonException e) {
            throw parsing("Cannot create JSON reader", e);
        }
        this.keyStrategy = keyStrategy;
    }

    /**
     * Initializes this JSON-P support component with the state it owns.
     */
    ReaderImpl(InputStream inputStream, JsonConfig.KeyStrategy keyStrategy) {
        try {
            this.parser = JSON_FACTORY.createParser(ObjectReadContext.empty(), inputStream);
        } catch (JacksonException e) {
            throw parsing("Cannot create JSON reader", e);
        }
        this.keyStrategy = keyStrategy;
    }

    /**
     * Reads the single document value and verifies it is a JSON structure.
     */
    @Override
    public JsonStructure read() {
        JsonValue value = readValue();
        if (value instanceof JsonStructure structure) {
            return structure;
        }
        throw new JsonException("JSON document is not an object or array");
    }

    /**
     * Reads an object value from the current Jackson parser and applies duplicate-key handling deterministically.
     */
    @Override
    public JsonObject readObject() {
        JsonStructure structure = read();
        if (structure instanceof JsonObject object) {
            return object;
        }
        throw new JsonException("JSON document is not an object");
    }

    /**
     * Reads an array value from the current Jackson parser into this provider's immutable JSON-P array model.
     */
    @Override
    public JsonArray readArray() {
        JsonStructure structure = read();
        if (structure instanceof JsonArray array) {
            return array;
        }
        throw new JsonException("JSON document is not an array");
    }

    /**
     * Reads the only allowed value from this reader and enforces the single-use reader contract.
     */
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

    /**
     * Closes the underlying JSON-P resource and translates provider-specific failures.
     */
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

@Internal
final class WriterImpl implements JsonWriter {
    private final JsonGenerator generator;
    private boolean closed;
    private boolean written;

    /**
     * Initializes this JSON-P support component with the state it owns.
     */
    WriterImpl(JsonGenerator generator) {
        this.generator = generator;
    }

    /**
     * Writes one array document and enforces the single-use writer contract.
     */
    @Override
    public void writeArray(JsonArray array) {
        checkWritable();
        generator.write(array);
        generator.flush();
        written = true;
    }

    /**
     * Writes one object document and enforces the single-use writer contract.
     */
    @Override
    public void writeObject(JsonObject object) {
        checkWritable();
        generator.write(object);
        generator.flush();
        written = true;
    }

    /**
     * Writes a document value while enforcing the generator root-value and open-state invariants.
     */
    @Override
    public void write(JsonStructure value) {
        checkWritable();
        generator.write(value);
        generator.flush();
        written = true;
    }

    /**
     * Writes a document value while enforcing the generator root-value and open-state invariants.
     */
    @Override
    public void write(JsonValue value) {
        checkWritable();
        generator.write(value);
        generator.flush();
        written = true;
    }

    /**
     * Closes the underlying JSON-P resource and translates provider-specific failures.
     */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            generator.close();
        }
    }

    /**
     * Validates that the writer is open and has not already written its single document value.
     */
    private void checkWritable() {
        if (closed) {
            throw new IllegalStateException("JsonWriter is closed");
        }
        if (written) {
            throw new IllegalStateException("JsonWriter can only write one value");
        }
    }
}

@Internal
final class JacksonJsonParser implements JsonParser {
    private final tools.jackson.core.JsonParser parser;
    private @Nullable Event currentEvent;
    private @Nullable JsonToken nextToken;

    /**
     * Initializes this JSON-P support component with the state it owns.
     */
    JacksonJsonParser(tools.jackson.core.JsonParser parser, boolean prefetch) {
        this.parser = parser;
        if (prefetch) {
            try {
                nextToken = parser.nextToken();
            } catch (JacksonException e) {
                throw parsing(CANNOT_READ_JSON_EVENT, e);
            }
        }
    }

    /**
     * Checks whether another parser event is available, using the prefetched token when present.
     */
    @Override
    public boolean hasNext() {
        if (nextToken != null) {
            return true;
        }
        try {
            nextToken = parser.nextToken();
            return nextToken != null;
        } catch (JacksonException e) {
            throw parsing(CANNOT_READ_JSON_EVENT, e);
        }
    }

    /**
     * Advances to the next parser event and maps Jackson tokens to Jakarta JSON-P events.
     */
    @Override
    public Event next() {
        JsonToken token = nextToken;
        nextToken = null;
        if (token == null) {
            try {
                token = parser.nextToken();
            } catch (JacksonException e) {
                throw parsing(CANNOT_READ_JSON_EVENT, e);
            }
        }
        if (token == null) {
            throw new NoSuchElementException();
        }
        currentEvent = toEvent(token);
        return currentEvent;
    }

    /**
     * Returns the last JSON-P event produced by this parser without advancing the Jackson parser.
     */
    @Override
    public @Nullable Event currentEvent() {
        return currentEvent;
    }

    /**
     * Returns the current string, key, or numeric token text according to the JSON-P parser contract.
     */
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

    /**
     * Reports whether the current numeric event came from an integral Jackson number token.
     */
    @Override
    public boolean isIntegralNumber() {
        if (currentEvent != Event.VALUE_NUMBER) {
            throw new IllegalStateException("Current parser event is not a number");
        }
        return parser.currentToken() == JsonToken.VALUE_NUMBER_INT;
    }

    /**
     * Reads the current numeric event as an int through Jackson's checked numeric accessor.
     */
    @Override
    public int getInt() {
        if (currentEvent != Event.VALUE_NUMBER) {
            throw new IllegalStateException("Current parser event is not a number");
        }
        try {
            return parser.getIntValue();
        } catch (JacksonException e) {
            throw parsing(CANNOT_READ_JSON_NUMBER, e);
        }
    }

    /**
     * Reads the current numeric event as a long through Jackson's checked numeric accessor.
     */
    @Override
    public long getLong() {
        if (currentEvent != Event.VALUE_NUMBER) {
            throw new IllegalStateException("Current parser event is not a number");
        }
        try {
            return parser.getLongValue();
        } catch (JacksonException e) {
            throw parsing(CANNOT_READ_JSON_NUMBER, e);
        }
    }

    /**
     * Reads the current numeric event as BigDecimal to preserve JSON-P number precision.
     */
    @Override
    public BigDecimal getBigDecimal() {
        if (currentEvent != Event.VALUE_NUMBER) {
            throw new IllegalStateException("Current parser event is not a number");
        }
        try {
            return parser.getDecimalValue();
        } catch (JacksonException e) {
            throw parsing(CANNOT_READ_JSON_NUMBER, e);
        }
    }

    /**
     * Returns the current parser location converted to the Jakarta JSON-P location model.
     */
    @Override
    public JsonLocation getLocation() {
        return toJsonLocation(parser.currentLocation());
    }

    /**
     * Materializes the current value and verifies it is an object.
     */
    @Override
    public JsonObject getObject() {
        JsonValue value = getValue();
        if (value instanceof JsonObject object) {
            return object;
        }
        throw new IllegalStateException("Current parser event is not an object");
    }

    /**
     * Materializes the current value, advancing past a key event when necessary.
     */
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

    /**
     * Materializes the current value and verifies it is an array.
     */
    @Override
    public JsonArray getArray() {
        JsonValue value = getValue();
        if (value instanceof JsonArray array) {
            return array;
        }
        throw new IllegalStateException("Current parser event is not an array");
    }

    /**
     * Returns a stream over the materialized array value for JSON-P stream accessors.
     */
    @Override
    public Stream<JsonValue> getArrayStream() {
        return getArray().stream();
    }

    /**
     * Returns a stream over the materialized object entries for JSON-P stream accessors.
     */
    @Override
    public Stream<Map.Entry<String, JsonValue>> getObjectStream() {
        return getObject().entrySet().stream();
    }

    /**
     * Returns a single-value stream only when the parser is still before the document value.
     */
    @Override
    public Stream<JsonValue> getValueStream() {
        if (currentEvent == null) {
            return Stream.of(getValue());
        }
        throw new IllegalStateException("Current parser event is not before a document value");
    }

    /**
     * Skips the current array value while preserving parser event state for subsequent reads.
     */
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

    /**
     * Skips the current object value while preserving parser event state for subsequent reads.
     */
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

    /**
     * Closes the underlying JSON-P resource and translates provider-specific failures.
     */
    @Override
    public void close() {
        try {
            parser.close();
        } catch (JacksonException e) {
            throw parsing("Cannot close JSON parser", e);
        }
    }

    /**
     * Moves from a key event to its value event, preserving JSON-P current-event semantics.
     */
    private void advanceValue() {
        try {
            JsonToken token = parser.nextToken();
            if (token == null) {
                throw new IllegalStateException("No JSON value follows the current key");
            }
            currentEvent = toEvent(token);
        } catch (JacksonException e) {
            throw parsing(CANNOT_READ_JSON_EVENT, e);
        }
    }

    /**
     * Maps Jackson token types to the Jakarta JSON-P parser event enum.
     */
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
@Internal
final class JacksonJsonGenerator implements JsonGenerator {
    private final tools.jackson.core.JsonGenerator generator;
    private final List<Boolean> objectStack = new ArrayList<>();
    private boolean closed;
    private boolean rootWritten;

    /**
     * Initializes this JSON-P support component with the state it owns.
     */
    JacksonJsonGenerator(tools.jackson.core.JsonGenerator generator) {
        this.generator = generator;
    }

    /**
     * Starts an object, recording nesting state so root and close invariants can be checked.
     */
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

    /**
     * Starts an object, recording nesting state so root and close invariants can be checked.
     */
    @Override
    public JsonGenerator writeStartObject(String name) {
        return writeKey(name).writeStartObject();
    }

    /**
     * Writes an object member name after validating that the generator is still open.
     */
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

    /**
     * Starts an array, recording nesting state so root and close invariants can be checked.
     */
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

    /**
     * Starts an array, recording nesting state so root and close invariants can be checked.
     */
    @Override
    public JsonGenerator writeStartArray(String name) {
        return writeKey(name).writeStartArray();
    }

    /**
     * Writes a named object member by emitting the key before the scalar or JSON-P value.
     */
    @Override
    public JsonGenerator write(String name, JsonValue value) {
        return writeKey(name).write(value);
    }

    /**
     * Writes a named object member by emitting the key before the scalar or JSON-P value.
     */
    @Override
    public JsonGenerator write(String name, String value) {
        return writeKey(name).write(value);
    }

    /**
     * Writes a named object member by emitting the key before the scalar or JSON-P value.
     */
    @Override
    public JsonGenerator write(String name, BigInteger value) {
        return writeKey(name).write(value);
    }

    /**
     * Writes a named object member by emitting the key before the scalar or JSON-P value.
     */
    @Override
    public JsonGenerator write(String name, BigDecimal value) {
        return writeKey(name).write(value);
    }

    /**
     * Writes a named object member by emitting the key before the scalar or JSON-P value.
     */
    @Override
    public JsonGenerator write(String name, int value) {
        return writeKey(name).write(value);
    }

    /**
     * Writes a named object member by emitting the key before the scalar or JSON-P value.
     */
    @Override
    public JsonGenerator write(String name, long value) {
        return writeKey(name).write(value);
    }

    /**
     * Writes a named object member by emitting the key before the scalar or JSON-P value.
     */
    @Override
    public JsonGenerator write(String name, double value) {
        return writeKey(name).write(value);
    }

    /**
     * Writes a named object member by emitting the key before the scalar or JSON-P value.
     */
    @Override
    public JsonGenerator write(String name, boolean value) {
        return writeKey(name).write(value);
    }

    /**
     * Writes a null value only when nested inside an object or array, matching JSON-P generator rules.
     */
    @Override
    public JsonGenerator writeNull(String name) {
        return writeKey(name).writeNull();
    }

    /**
     * Closes the most recent object or array and updates root-completion state.
     */
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

    /**
     * Writes a document value while enforcing the generator root-value and open-state invariants.
     */
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

    /**
     * Writes a document value while enforcing the generator root-value and open-state invariants.
     */
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

    /**
     * Writes a document value while enforcing the generator root-value and open-state invariants.
     */
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
            throw generation(CANNOT_WRITE_NUMBER, e);
        }
    }

    /**
     * Writes a document value while enforcing the generator root-value and open-state invariants.
     */
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
            throw generation(CANNOT_WRITE_NUMBER, e);
        }
    }

    /**
     * Writes a document value while enforcing the generator root-value and open-state invariants.
     */
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
            throw generation(CANNOT_WRITE_NUMBER, e);
        }
    }

    /**
     * Writes a document value while enforcing the generator root-value and open-state invariants.
     */
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
            throw generation(CANNOT_WRITE_NUMBER, e);
        }
    }

    /**
     * Writes a document value while enforcing the generator root-value and open-state invariants.
     */
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
            throw generation(CANNOT_WRITE_NUMBER, e);
        }
    }

    /**
     * Writes a document value while enforcing the generator root-value and open-state invariants.
     */
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

    /**
     * Writes a null value only when nested inside an object or array, matching JSON-P generator rules.
     */
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

    /**
     * Closes the underlying JSON-P resource and translates provider-specific failures.
     */
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

    /**
     * Flushes the backing Jackson generator after validating that it is still open.
     */
    @Override
    public void flush() {
        checkOpen();
        try {
            generator.flush();
        } catch (JacksonException e) {
            throw generation("Cannot flush JSON generator", e);
        }
    }

    /**
     * Validates that the generator has not been closed before writing or flushing.
     */
    private void checkOpen() {
        if (closed) {
            throw new JsonGenerationException("JsonGenerator is closed");
        }
    }

    /**
     * Prevents writing more than one root value to the JSON document.
     */
    private void checkRootValue() {
        if (objectStack.isEmpty() && rootWritten) {
            throw new JsonGenerationException("JSON document already has a root value");
        }
    }
}
