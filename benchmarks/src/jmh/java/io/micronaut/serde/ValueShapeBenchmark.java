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
package io.micronaut.serde;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.jackson.JacksonJsonMapper;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Value shapes the user bean benchmarks do not cover: dates, untyped writes as the HTTP server does them,
 * polymorphic reads, non-string map keys and epoch timestamps. Default configuration, generated serdes.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ValueShapeBenchmark {

    private static final int SIZE = 20;
    private static final Argument<List<Dated>> DATED_LIST = Argument.listOf(Dated.class);
    private static final Argument<List<Item>> ITEM_LIST = Argument.listOf(Item.class);
    private static final Argument<List<Shape>> SHAPE_LIST = Argument.listOf(Shape.class);
    private static final Argument<Map<Long, String>> LONG_MAP = Argument.mapOf(Long.class, String.class);
    private static final Argument<List<Epoch>> EPOCH_LIST = Argument.listOf(Epoch.class);

    @Benchmark
    public byte[] writeDates(Holder h) throws IOException {
        return h.mapper.writeValueAsBytes(DATED_LIST, h.dated);
    }

    @Benchmark
    public Object readDates(Holder h) throws IOException {
        return h.mapper.readValue(h.datedJson, DATED_LIST);
    }

    @Benchmark
    public int writeUntypedList(Holder h) throws IOException {
        h.out.reset();
        h.mapper.writeValue(h.out, h.items);
        return h.out.size();
    }

    @Benchmark
    public int writeTypedList(Holder h) throws IOException {
        h.out.reset();
        h.mapper.writeValue(h.out, ITEM_LIST, h.items);
        return h.out.size();
    }

    @Benchmark
    public Object readShapes(Holder h) throws IOException {
        return h.mapper.readValue(h.shapesJson, SHAPE_LIST);
    }

    @Benchmark
    public byte[] writeShapes(Holder h) throws IOException {
        return h.mapper.writeValueAsBytes(SHAPE_LIST, h.shapes);
    }

    @Benchmark
    public byte[] writeLongMap(Holder h) throws IOException {
        return h.mapper.writeValueAsBytes(LONG_MAP, h.longMap);
    }

    @Benchmark
    public Object readLongMap(Holder h) throws IOException {
        return h.mapper.readValue(h.longMapJson, LONG_MAP);
    }

    @Benchmark
    public Object readEpochInstants(Holder h) throws IOException {
        return h.mapper.readValue(h.epochJson, EPOCH_LIST);
    }

    @State(Scope.Thread)
    public static class Holder {
        ApplicationContext context;
        JacksonJsonMapper mapper;
        final ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        List<Dated> dated;
        byte[] datedJson;
        List<Item> items;
        List<Shape> shapes;
        byte[] shapesJson;
        Map<Long, String> longMap;
        byte[] longMapJson;
        byte[] epochJson;

        @Setup
        public void setUp() throws IOException {
            context = ApplicationContext.run();
            mapper = context.getBean(JacksonJsonMapper.class);
            dated = new ArrayList<>();
            items = new ArrayList<>();
            shapes = new ArrayList<>();
            longMap = new LinkedHashMap<>();
            StringBuilder epoch = new StringBuilder("[");
            Instant base = Instant.parse("2026-11-20T11:38:06.123456789Z");
            for (int i = 0; i < SIZE; i++) {
                Dated d = new Dated();
                d.id = "id-" + i;
                d.created = base.plusSeconds(i * 3_611L);
                d.day = LocalDate.of(2026, 1 + i % 12, 1 + i);
                d.at = LocalDateTime.of(2026, 1 + i % 12, 1 + i, i % 24, i, i, i * 1_000_000);
                d.offset = OffsetDateTime.of(d.at, ZoneOffset.ofHours(i % 5));
                dated.add(d);
                Item item = new Item();
                item.id = i;
                item.name = "item-" + i;
                item.price = i * 1.25;
                item.active = i % 2 == 0;
                items.add(item);
                if (i % 2 == 0) {
                    Circle c = new Circle();
                    c.id = "c" + i;
                    c.radius = i;
                    shapes.add(c);
                } else {
                    Square s = new Square();
                    s.id = "s" + i;
                    s.side = i;
                    s.label = "square " + i;
                    shapes.add(s);
                }
                longMap.put(1_000_000L + i * 37L, "value-" + i);
                if (i > 0) {
                    epoch.append(',');
                }
                epoch.append("{\"id\":").append(i).append(",\"at\":").append(base.toEpochMilli() + i * 1000L).append('}');
            }
            epoch.append(']');
            datedJson = mapper.writeValueAsBytes(DATED_LIST, dated);
            shapesJson = mapper.writeValueAsBytes(SHAPE_LIST, shapes);
            longMapJson = mapper.writeValueAsBytes(LONG_MAP, longMap);
            epochJson = epoch.toString().getBytes();
            // sanity checks
            if (mapper.readValue(datedJson, DATED_LIST).size() != SIZE
                || mapper.readValue(shapesJson, SHAPE_LIST).size() != SIZE
                || mapper.readValue(longMapJson, LONG_MAP).size() != SIZE
                || mapper.readValue(epochJson, EPOCH_LIST).size() != SIZE) {
                throw new IllegalStateException("Unexpected payload");
            }
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    @Serdeable
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class Dated {
        public String id;
        public Instant created;
        public LocalDate day;
        public LocalDateTime at;
        public OffsetDateTime offset;
    }

    @Serdeable
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class Epoch {
        public int id;
        public Instant at;
    }

    @Serdeable
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class Item {
        public long id;
        public String name;
        public double price;
        public boolean active;
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
        public double side;
        public String label;
    }
}
