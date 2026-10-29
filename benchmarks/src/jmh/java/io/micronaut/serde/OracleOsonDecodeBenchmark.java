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

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonBinaryObjectMapper;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonParser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Decoding one OSON document into a generated deserializer, through the {@code OracleJsonParser} path and through
 * the native decoder. Both go through the public mapper API and look up the deserializer per call, as a repository
 * reading one JSON column per row does. The parser is created over the same bytes, which is what
 * {@code ResultSet.getObject(column, OracleJsonParser.class)} does with the bytes it fetched.
 * <p>
 * This measures decoding only. Through JDBC the fetch dominates, so the end-to-end gain is much smaller than the
 * gain measured here.
 * <ul>
 *     <li>{@code flat}: twelve properties, eight strings and four numbers, the document of the
 *     micronaut-oracledb spike</li>
 *     <li>{@code nested}: an object with a nested object, a list of objects and a list of strings</li>
 * </ul>
 */
@State(Scope.Benchmark)
public class OracleOsonDecodeBenchmark {

    private static final Argument<WideBook> FLAT = Argument.of(WideBook.class);
    private static final Argument<Library> NESTED = Argument.of(Library.class);

    @Param({"flat", "nested"})
    String shape;

    private ApplicationContext context;
    private OracleJdbcJsonBinaryObjectMapper mapper;
    private OracleJsonFactory factory;
    private Argument<?> type;
    private byte[] oson;

    @Setup
    public void setup() throws IOException {
        context = ApplicationContext.run();
        mapper = context.getBean(OracleJdbcJsonBinaryObjectMapper.class);
        factory = new OracleJsonFactory();
        if ("flat".equals(shape)) {
            type = FLAT;
            oson = mapper.writeValueAsBytes(FLAT, new WideBook(7L, "title-7", "author-7", "publisher-7", "isbn-7",
                "language-7", "genre-7", "summary-7", "coverUrl-7", 7, 7, 7));
        } else {
            type = NESTED;
            oson = mapper.writeValueAsBytes(NESTED, new Library("Central", new Address("1 Main St", "Springfield", "12345"),
                List.of(new Shelf("A", 120, List.of("fiction", "classics")), new Shelf("B", 80, List.of("science")),
                    new Shelf("C", 45, List.of("history", "travel", "maps"))),
                List.of("wifi", "cafe", "archive")));
        }
    }

    @TearDown
    public void tearDown() {
        context.close();
    }

    @Benchmark
    public Object parser() throws IOException {
        try (OracleJsonParser parser = factory.createJsonBinaryParser(ByteBuffer.wrap(oson))) {
            return mapper.readValue(parser, type);
        }
    }

    @Benchmark
    public Object nativeDecoder() throws IOException {
        return mapper.readValue(oson, type);
    }

    /**
     * The flat document.
     */
    @Serdeable
    public record WideBook(Long id, String title, String author, String publisher, String isbn, String language,
                           String genre, String summary, String coverUrl, int pages, int edition, int rating) {
    }

    /**
     * The nested document.
     */
    @Serdeable
    public record Library(String name, Address address, List<Shelf> shelves, List<String> services) {
    }

    /**
     * A nested object.
     */
    @Serdeable
    public record Address(String street, String city, String postcode) {
    }

    /**
     * An element of a nested list.
     */
    @Serdeable
    public record Shelf(String label, int capacity, List<String> topics) {
    }
}
