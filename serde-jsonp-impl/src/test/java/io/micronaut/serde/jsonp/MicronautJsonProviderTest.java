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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static JsonObject nestedObject(int depth, String value) {
        JsonValue current = Json.createObjectBuilder().add("leaf", value).build();
        for (int i = 0; i < depth; i++) {
            current = Json.createObjectBuilder().add("a", current).build();
        }
        return current.asJsonObject();
    }
}
