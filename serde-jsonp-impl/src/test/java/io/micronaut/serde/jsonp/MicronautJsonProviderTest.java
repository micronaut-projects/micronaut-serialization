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

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonConfig;
import jakarta.json.JsonException;
import jakarta.json.JsonMergePatch;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonPatch;
import jakarta.json.JsonPointer;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicronautJsonProviderTest {
    @Test
    void discoversProviderWithoutParsson() {
        JsonProvider provider = JsonProvider.provider();

        assertInstanceOf(MicronautJsonProvider.class, provider);
    }

    @Test
    void streamsParserEventsWithJacksonCore() {
        JsonParser parser = Json.createParser(new StringReader("{\"name\":\"Fred\",\"age\":42,\"tags\":[true,null]}"));
        List<JsonParser.Event> events = new ArrayList<>();
        while (parser.hasNext()) {
            events.add(parser.next());
        }

        assertEquals(List.of(
            JsonParser.Event.START_OBJECT,
            JsonParser.Event.KEY_NAME,
            JsonParser.Event.VALUE_STRING,
            JsonParser.Event.KEY_NAME,
            JsonParser.Event.VALUE_NUMBER,
            JsonParser.Event.KEY_NAME,
            JsonParser.Event.START_ARRAY,
            JsonParser.Event.VALUE_TRUE,
            JsonParser.Event.VALUE_NULL,
            JsonParser.Event.END_ARRAY,
            JsonParser.Event.END_OBJECT
        ), events);
    }

    @Test
    void writesAndReadsJsonValues() {
        JsonObject object = Json.createObjectBuilder()
            .add("name", "Fred")
            .add("amount", new BigDecimal("12.30"))
            .add("nested", Json.createArrayBuilder().add(1).add(false).addNull())
            .build();

        StringWriter writer = new StringWriter();
        Json.createWriter(writer).writeObject(object);

        assertEquals("{\"name\":\"Fred\",\"amount\":12.30,\"nested\":[1,false,null]}", writer.toString());

        JsonObject read = Json.createReader(new StringReader(writer.toString())).readObject();
        assertEquals("Fred", read.getString("name"));
        assertEquals(new BigDecimal("12.30"), read.getJsonNumber("amount").bigDecimalValue());
        assertTrue(read.getJsonArray("nested").isNull(2));
    }

    @Test
    void supportsGeneratorPrettyPrinting() {
        StringWriter writer = new StringWriter();

        Json.createGeneratorFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true))
            .createGenerator(writer)
            .writeStartObject()
            .write("name", "Fred")
            .writeEnd()
            .close();

        assertTrue(writer.toString().contains("\n"));
    }

    @Test
    void supportsDuplicateKeyStrategies() {
        JsonReader firstReader = Json.createReaderFactory(Map.of(JsonConfig.KEY_STRATEGY, JsonConfig.KeyStrategy.FIRST))
            .createReader(new StringReader("{\"name\":\"first\",\"name\":\"last\"}"));
        JsonReader lastReader = Json.createReaderFactory(Map.of(JsonConfig.KEY_STRATEGY, JsonConfig.KeyStrategy.LAST))
            .createReader(new StringReader("{\"name\":\"first\",\"name\":\"last\"}"));
        JsonReader noneReader = Json.createReaderFactory(Map.of(JsonConfig.KEY_STRATEGY, JsonConfig.KeyStrategy.NONE))
            .createReader(new StringReader("{\"name\":\"first\",\"name\":\"last\"}"));

        assertEquals("first", firstReader.readObject().getString("name"));
        assertEquals("last", lastReader.readObject().getString("name"));
        assertThrows(JsonException.class, noneReader::readObject);
    }

    @Test
    void supportsPointersPatchesAndMergePatches() {
        JsonObject source = Json.createObjectBuilder()
            .add("name", "Fred")
            .add("address", Json.createObjectBuilder().add("city", "London"))
            .build();
        JsonPointer pointer = Json.createPointer("/address/city");

        assertEquals("London", ((JsonString) pointer.getValue(source)).getString());

        JsonObject changed = pointer.replace(source, Json.createValue("Madrid"));
        assertEquals("Madrid", changed.getJsonObject("address").getString("city"));

        JsonPatch patch = Json.createPatchBuilder().replace("/name", "Bob").build();
        assertEquals("Bob", patch.apply(source).getString("name"));

        JsonObject merged = Json.createMergePatch(Json.createObjectBuilder()
                .add("address", Json.createObjectBuilder().addNull("city").add("country", "ES"))
                .build())
            .apply(source)
            .asJsonObject();
        assertEquals("ES", merged.getJsonObject("address").getString("country"));
    }

    @Test
    void deepJsonPointerUpdatesAreIterative() {
        int depth = 5_000;
        JsonObject source = nestedObject(depth, "old");
        String parentPath = "/a".repeat(depth);
        JsonPointer leaf = Json.createPointer(parentPath + "/leaf");

        JsonObject replaced = leaf.replace(source, Json.createValue("new"));
        assertEquals("new", ((JsonString) leaf.getValue(replaced)).getString());

        JsonPointer added = Json.createPointer(parentPath + "/added");
        JsonObject withAdded = added.add(replaced, Json.createValue("value"));
        assertEquals("value", ((JsonString) added.getValue(withAdded)).getString());

        JsonObject removed = added.remove(withAdded);
        assertThrows(JsonException.class, () -> added.getValue(removed));
    }

    @Test
    void hugeJsonPointerArrayIndexThrowsJsonException() {
        JsonArray source = Json.createArrayBuilder().add("value").build();

        assertThrows(JsonException.class, () -> Json.createPointer("/999999999999999999999999").getValue(source));
        assertThrows(JsonException.class, () -> Json.createPointer("/999999999999999999999999").replace(source, JsonValue.TRUE));
    }

    @Test
    void escapesStrings() {
        assertEquals("\"a\\\"b\"", Json.createValue("a\"b").toString());
    }

    @Test
    void exercisesObjectArrayBuilderAndValueContracts() {
        JsonObject original = Json.createObjectBuilder()
            .add("name", "Fred")
            .add("count", 2)
            .addNull("missing")
            .build();
        JsonObject copied = Json.createObjectBuilder(original)
            .add("big", new BigInteger("12345678901234567890"))
            .add("decimal", new BigDecimal("1.25"))
            .add("flag", true)
            .remove("missing")
            .build();

        assertTrue(copied.containsKey("name"));
        assertEquals("fallback", copied.getString("missing", "fallback"));
        assertEquals(7, copied.getInt("missing", 7));
        assertTrue(copied.getBoolean("flag"));
        assertTrue(copied.getBoolean("missing", true));
        assertThrows(NullPointerException.class, () -> copied.getString("missing"));
        assertThrows(ClassCastException.class, () -> copied.getBoolean("name"));
        assertEquals(copied, Json.createReader(new StringReader(copied.toString())).readObject());

        JsonArray array = Json.createArrayBuilder(List.of("a", 1, false))
            .add(Json.createObjectBuilder(Map.of("nested", "value")))
            .add(Json.createArrayBuilder(List.of("x", "y")))
            .add(new BigDecimal("2.5"))
            .add(new BigInteger("9"))
            .add(true)
            .addNull()
            .set(0, "b")
            .set(1, 2)
            .set(2, true)
            .set(3, Json.createObjectBuilder().add("nested", "changed"))
            .set(4, Json.createArrayBuilder().add("z"))
            .remove(7)
            .build();

        assertEquals("b", array.getString(0));
        assertEquals("default", array.getString(99, "default"));
        assertEquals(2, array.getInt(1));
        assertEquals(12, array.getInt(99, 12));
        assertTrue(array.getBoolean(2));
        assertFalse(array.getBoolean(99, false));
        assertEquals("changed", array.getJsonObject(3).getString("nested"));
        assertEquals("z", array.getJsonArray(4).getString(0));
        assertEquals(new BigDecimal("2.5"), array.getJsonNumber(5).bigDecimalValue());
        assertEquals("\"b\",2", array.getValuesAs(JsonValue.class).stream().limit(2).map(JsonValue::toString).collect(Collectors.joining(",")));
        assertTrue(array.isNull(array.size() - 1));

        JsonString string = Json.createValue("text");
        assertEquals("text", string.getChars().toString());
        assertEquals(Json.createValue("text"), string);
        assertEquals(Json.createValue("text").hashCode(), string.hashCode());

        JsonNumber number = Json.createValue(new BigDecimal("10.0"));
        assertEquals(10, number.intValue());
        assertEquals(10L, number.longValue());
        assertEquals(BigInteger.TEN, number.bigIntegerValue());
        assertEquals(10.0d, number.doubleValue());
        assertEquals(number, Json.createValue(BigDecimal.TEN));
        assertEquals(number.hashCode(), Json.createValue(BigDecimal.TEN).hashCode());
    }

    @Test
    void exercisesParserStreamsSkipsReadersWritersAndGenerators() {
        JsonParser parser = Json.createParser(new StringReader("[{\"a\":1},[2,3],true]"));
        assertTrue(parser.hasNext());
        assertEquals(JsonParser.Event.START_ARRAY, parser.next());
        assertEquals(JsonParser.Event.START_OBJECT, parser.next());
        assertEquals(Json.createObjectBuilder().add("a", 1).build(), parser.getObject());
        assertEquals(JsonParser.Event.START_ARRAY, parser.next());
        parser.skipArray();
        assertEquals(JsonParser.Event.VALUE_TRUE, parser.next());
        assertEquals(JsonValue.TRUE, parser.getValue());
        assertTrue(parser.getLocation().getLineNumber() >= -1);
        parser.close();

        JsonParser objectParser = Json.createParser(new StringReader("{\"skip\":{\"a\":1},\"keep\":[4]}"));
        assertEquals(Json.createObjectBuilder()
            .add("skip", Json.createObjectBuilder().add("a", 1))
            .add("keep", Json.createArrayBuilder().add(4))
            .build(), objectParser.getValueStream().findFirst().orElseThrow());
        objectParser.close();

        JsonParser arrayParser = Json.createParser(new StringReader("[1,2,3]"));
        assertEquals(List.of("1", "2", "3"), arrayParser.getArrayStream().map(JsonValue::toString).toList());
        arrayParser.close();

        JsonObject streamRead = Json.createReader(new java.io.ByteArrayInputStream("{\"a\":1}".getBytes(StandardCharsets.UTF_8))).readObject();
        assertEquals(1, streamRead.getInt("a"));

        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        Json.createWriter(output).write(streamRead);
        assertEquals("{\"a\":1}", output.toString(StandardCharsets.UTF_8));

        StringWriter writer = new StringWriter();
        Json.createGenerator(writer)
            .writeStartObject()
            .write("string", "value")
            .write("bigInteger", BigInteger.TEN)
            .write("bigDecimal", new BigDecimal("1.5"))
            .write("int", 1)
            .write("long", 2L)
            .write("double", 3.25d)
            .write("boolean", true)
            .writeNull("nothing")
            .writeStartArray("array")
            .write(JsonValue.FALSE)
            .writeEnd()
            .writeStartObject("object")
            .write("nested", JsonValue.TRUE)
            .writeEnd()
            .writeEnd()
            .close();

        JsonObject generated = Json.createReader(new StringReader(writer.toString())).readObject();
        assertEquals(BigInteger.TEN, generated.getJsonNumber("bigInteger").bigIntegerValue());
        assertEquals(new BigDecimal("1.5"), generated.getJsonNumber("bigDecimal").bigDecimalValue());
        assertTrue(generated.getJsonArray("array").getBoolean(0) == false);
        assertTrue(generated.getJsonObject("object").getBoolean("nested"));
        assertThrows(IllegalStateException.class, () -> {
            jakarta.json.JsonWriter jsonWriter = Json.createWriter(new StringWriter());
            jsonWriter.write(JsonValue.TRUE);
            jsonWriter.write(JsonValue.FALSE);
        });
        assertThrows(jakarta.json.stream.JsonGenerationException.class, () -> Json.createGenerator(new StringWriter()).writeNull());
    }

    @Test
    void exercisesPatchAndMergePatchOperations() {
        JsonObject source = Json.createObjectBuilder()
            .add("name", "Fred")
            .add("copyFrom", "source")
            .add("removeMe", true)
            .add("items", Json.createArrayBuilder().add("a").add("b"))
            .build();

        JsonPatch patch = Json.createPatchBuilder()
            .add("/items/-", "c")
            .copy("/copied", "/copyFrom")
            .move("/moved", "/name")
            .replace("/items/0", "z")
            .remove("/removeMe")
            .test("/copied", "source")
            .build();
        JsonObject patched = patch.apply(source);

        assertEquals("source", patched.getString("copied"));
        assertEquals("Fred", patched.getString("moved"));
        assertEquals("z", patched.getJsonArray("items").getString(0));
        assertFalse(patched.containsKey("removeMe"));
        assertThrows(JsonException.class, () -> Json.createPatchBuilder().test("/copied", "wrong").build().apply(patched));

        JsonMergePatch mergePatch = Json.createMergePatch(Json.createObjectBuilder()
            .add("copied", "changed")
            .addNull("moved")
            .add("nested", Json.createObjectBuilder().add("v", 1))
            .build());
        JsonObject merged = mergePatch.apply(patched).asJsonObject();
        assertEquals("changed", merged.getString("copied"));
        assertFalse(merged.containsKey("moved"));
        assertEquals(1, merged.getJsonObject("nested").getInt("v"));

        JsonValue replacement = Json.createMergePatch(Json.createValue("replacement")).apply(merged);
        assertEquals(Json.createValue("replacement"), replacement);
    }

    private static JsonObject nestedObject(int depth, String value) {
        JsonValue current = Json.createObjectBuilder().add("leaf", value).build();
        for (int i = 0; i < depth; i++) {
            current = Json.createObjectBuilder().add("a", current).build();
        }
        return current.asJsonObject();
    }
}
