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
package io.micronaut.serde.support.deserializers;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Polymorphic reads with a hidden discriminator, wherever the discriminator is in the object.
 */
class DiscriminatorPositionTest {

    private static ApplicationContext context;
    private static ObjectMapper mapper;

    @BeforeAll
    static void start() {
        context = ApplicationContext.run();
        mapper = context.getBean(ObjectMapper.class);
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @Test
    void discriminatorFirst() throws IOException {
        Circle circle = assertInstanceOf(Circle.class, read("""
            {"type":"circle","id":"c1","radius":2.5}"""));
        assertEquals("c1", circle.id);
        assertEquals(2.5, circle.radius);
    }

    @Test
    void discriminatorLastOrInTheMiddle() throws IOException {
        Square last = assertInstanceOf(Square.class, read("""
            {"id":"s1","side":3,"tags":["a","b"],"meta":{"k":{"n":1}},"type":"square"}"""));
        assertEquals("s1", last.id);
        assertEquals(3, last.side);
        assertEquals(List.of("a", "b"), last.tags);
        assertEquals(Map.of("k", Map.of("n", 1)), last.meta);
        Square middle = assertInstanceOf(Square.class, read("""
            {"id":"s2","type":"square","side":4}"""));
        assertEquals("s2", middle.id);
        assertEquals(4, middle.side);
    }

    @Test
    void discriminatorFirstWithNestedPolymorphicValues() throws IOException {
        Group group = assertInstanceOf(Group.class, read("""
            {"type":"group","id":"g","members":[{"type":"circle","id":"c","radius":1},{"radius":2,"type":"circle","id":"d"}],
            "first":{"id":"s","type":"square","side":5}}"""));
        assertEquals("g", group.id);
        assertEquals(2, group.members.size());
        assertEquals("d", assertInstanceOf(Circle.class, group.members.get(1)).id);
        assertEquals(5, assertInstanceOf(Square.class, group.first).side);
    }

    @Test
    void unknownPropertiesAroundTheDiscriminator() throws IOException {
        Circle circle = assertInstanceOf(Circle.class, read("""
            {"type":"circle","unknown":{"x":[1,2]},"id":"c1","radius":1}"""));
        assertEquals("c1", circle.id);
    }

    @Test
    void missingNullOrUnknownDiscriminatorBehavesAsBefore() throws IOException {
        assertThrows(IOException.class, () -> read("""
            {"id":"x"}"""));
        assertThrows(IOException.class, () -> read("{}"));
        assertThrows(IOException.class, () -> read("""
            {"type":null,"id":"x"}"""));
        assertThrows(IOException.class, () -> read("""
            {"type":"triangle","id":"x"}"""));
    }

    @Test
    void nullShape() throws IOException {
        assertNull(mapper.readValue("null", Argument.of(Shape.class)));
    }

    @Test
    void roundTrip() throws IOException {
        Square square = new Square();
        square.id = "s";
        square.side = 7;
        square.tags = List.of("t");
        Group group = new Group();
        group.id = "g";
        group.members = List.of(square);
        group.first = square;
        Group read = assertInstanceOf(Group.class, read(mapper.writeValueAsString(Argument.of(Shape.class), group)));
        assertEquals(7, assertInstanceOf(Square.class, read.first).side);
        assertEquals(List.of("t"), assertInstanceOf(Square.class, read.members.get(0)).tags);
    }

    private static Shape read(String json) throws IOException {
        return mapper.readValue(json, Argument.of(Shape.class));
    }

    @Serdeable
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Circle.class, name = "circle"),
        @JsonSubTypes.Type(value = Square.class, name = "square"),
        @JsonSubTypes.Type(value = Group.class, name = "group")
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
    public static final class Group extends Shape {
        public List<Shape> members;
        public Shape first;
    }
}
