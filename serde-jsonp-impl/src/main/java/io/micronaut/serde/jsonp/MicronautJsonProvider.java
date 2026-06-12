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

import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonConfig;
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
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.JsonpCharacterEscapes;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.micronaut.serde.jsonp.JsonpValueSupport.ArrayBuilder;
import static io.micronaut.serde.jsonp.JsonpValueSupport.JsonArrayValue;
import static io.micronaut.serde.jsonp.JsonpValueSupport.JsonNumberValue;
import static io.micronaut.serde.jsonp.JsonpValueSupport.JsonObjectValue;
import static io.micronaut.serde.jsonp.JsonpValueSupport.JsonStringValue;
import static io.micronaut.serde.jsonp.JsonpValueSupport.ObjectBuilder;
import static io.micronaut.serde.jsonp.JsonpValueSupport.Pointer;
import static io.micronaut.serde.jsonp.JsonpValueSupport.Values;
import static io.micronaut.serde.jsonp.JsonpProviderSupport.copyConfig;
import static io.micronaut.serde.jsonp.JsonpProviderSupport.parsing;

/**
 * Micronaut-native Jakarta JSON-P provider.
 *
 * @since 3.1.0
 */
public final class MicronautJsonProvider extends JsonProvider {
    static final String VALUE = "value";
    static final String CANNOT_READ_JSON_EVENT = "Cannot read JSON event";
    static final String CANNOT_READ_JSON_NUMBER = "Cannot read JSON number";
    static final String CANNOT_WRITE_NUMBER = "Cannot write number";
    static final JsonFactory JSON_FACTORY = JsonFactory.builder()
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
    @SuppressWarnings("java:S2095")
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
    @SuppressWarnings("java:S2095")
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

}
