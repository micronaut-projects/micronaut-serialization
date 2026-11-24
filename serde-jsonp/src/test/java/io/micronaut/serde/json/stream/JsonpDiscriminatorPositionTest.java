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
package io.micronaut.serde.json.stream;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The JSON-P decoder doesn't match keys itself, so the subtyped deserializer reads the discriminator through the
 * keys-aware adapter, which keeps a name that isn't the discriminator pending for the demuxing decoder.
 */
@MicronautTest
class JsonpDiscriminatorPositionTest {

    @Test
    void discriminatorFirst(JsonMapper mapper) throws IOException {
        Circle circle = assertInstanceOf(Circle.class, read(mapper, """
            {"type":"circle","id":"c1","radius":2.5}"""));
        assertEquals("c1", circle.id);
        assertEquals(2.5, circle.radius);
    }

    @Test
    void discriminatorLaterReplaysTheKeysBeforeIt(JsonMapper mapper) throws IOException {
        Square last = assertInstanceOf(Square.class, read(mapper, """
            {"id":"s1","side":3,"tags":["a","b"],"meta":{"k":{"n":1}},"type":"square"}"""));
        assertEquals("s1", last.id);
        assertEquals(3, last.side);
        assertEquals(List.of("a", "b"), last.tags);
        assertEquals(Map.of("k", Map.of("n", 1)), last.meta);
        Square middle = assertInstanceOf(Square.class, read(mapper, """
            {"id":"s2","type":"square","side":4}"""));
        assertEquals("s2", middle.id);
        assertEquals(4, middle.side);
    }

    @Test
    void unknownPropertiesAroundTheDiscriminator(JsonMapper mapper) throws IOException {
        Circle first = assertInstanceOf(Circle.class, read(mapper, """
            {"type":"circle","unknown":{"x":[1,2]},"id":"c1","radius":1}"""));
        assertEquals("c1", first.id);
        Circle later = assertInstanceOf(Circle.class, read(mapper, """
            {"unknown":{"x":[1,2]},"id":"c2","type":"circle","radius":1}"""));
        assertEquals("c2", later.id);
    }

    @Test
    void missingNullOrUnknownDiscriminator(JsonMapper mapper) {
        assertThrows(IOException.class, () -> read(mapper, """
            {"id":"x"}"""));
        assertThrows(IOException.class, () -> read(mapper, "{}"));
        assertThrows(IOException.class, () -> read(mapper, """
            {"type":null,"id":"x"}"""));
        assertThrows(IOException.class, () -> read(mapper, """
            {"type":"triangle","id":"x"}"""));
    }

    @Test
    void emptyAndMissingDiscriminatorUseTheDefaultImpl(JsonMapper mapper) throws IOException {
        List<Mark> marks = mapper.readValue("""
            [{},{"type":"dot","x":1},{"x":2},{"x":3,"type":"dot"},{"type":null,"x":4},{"type":"ring","x":5},{"x":6,"type":"ring"}]""",
            Argument.listOf(Mark.class));
        assertEquals(7, marks.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(i, assertInstanceOf(Dot.class, marks.get(i)).x);
        }
        assertEquals(5, assertInstanceOf(Ring.class, marks.get(5)).x);
        assertEquals(6, assertInstanceOf(Ring.class, marks.get(6)).x);
    }

    @Test
    void roundTrip(JsonMapper mapper) throws IOException {
        Square square = new Square();
        square.id = "s";
        square.side = 7;
        square.tags = List.of("t");
        String json = mapper.writeValueAsString(Argument.of(Shape.class), square);
        Square read = assertInstanceOf(Square.class, read(mapper, json));
        assertEquals(7, read.side);
        assertEquals(List.of("t"), read.tags);
    }

    private static Shape read(JsonMapper mapper, String json) throws IOException {
        return mapper.readValue(json, Argument.of(Shape.class));
    }

    @Serdeable
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Circle.class, name = "circle"),
        @JsonSubTypes.Type(value = Square.class, name = "square")
    })
    public abstract static class Shape {
        public String id;
    }

    @Serdeable
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class Circle extends Shape {
        public double radius;
    }

    @Serdeable
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class Square extends Shape {
        public int side;
        public List<String> tags;
        public Map<String, Object> meta;
    }

    @Serdeable
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", defaultImpl = Dot.class)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Dot.class, name = "dot"),
        @JsonSubTypes.Type(value = Ring.class, name = "ring")
    })
    public abstract static class Mark {
    }

    @Serdeable
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class Dot extends Mark {
        public int x;
    }

    @Serdeable
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class Ring extends Mark {
        public int x;
    }
}
