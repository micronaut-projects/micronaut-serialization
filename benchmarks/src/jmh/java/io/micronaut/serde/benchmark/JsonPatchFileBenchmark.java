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
package io.micronaut.serde.benchmark;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.patch.JsonPatch;
import io.micronaut.serde.patch.JsonPatchOptions;
import org.jspecify.annotations.Nullable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Large-file workloads: real input streams, bounded replay, file output, and typed results. */
@Internal
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
public class JsonPatchFileBenchmark {
    private static final Argument<Document> DOCUMENT = Argument.of(Document.class);
    static final List<String> SHAPES = List.of("flat", "nested", "strings");
    static final List<String> OPERATIONS = List.of("addFirst", "addLast", "removeMiddle", "removeRecords",
        "replaceEarly", "replaceLate", "moveForward", "moveBackward", "copyRecords", "test", "independent", "dependent");

    /**
     * Patches a file to a counting sink, including input open/close and any replay spill I/O.
     * @param input File fixture
     * @param policy Output validation policy
     * @return Output byte count
     * @throws IOException If patching fails
     */
    @Benchmark
    public long writePatchedJson(Input input, OutputPolicy policy) throws IOException {
        input.sink.bytes = 0;
        try (var source = Files.newInputStream(input.source)) {
            input.mapper.writePatchedValue(source, input.patch, input.sink, input.options(policy));
        }
        return input.sink.bytes;
    }

    /**
     * Includes creating/truncating, writing and closing a buffered output file; does not fsync.
     * @param input File fixture
     * @param policy Output validation policy
     * @return Output byte count
     * @throws IOException If patching or file I/O fails
     */
    @Benchmark
    public long writePatchedFile(Input input, OutputPolicy policy) throws IOException {
        try (var source = Files.newInputStream(input.source);
             var output = new BufferedOutputStream(Files.newOutputStream(input.destination))) {
            input.mapper.writePatchedValue(source, input.patch, output, input.options(policy));
        }
        return Files.size(input.destination);
    }

    /**
     * Materializes only the patched result, including validation and replay spill I/O.
     * @param input File fixture
     * @return New typed document
     * @throws IOException If patching or deserialization fails
     */
    @Benchmark
    public Document readPatchedObject(Input input) throws IOException {
        try (var source = Files.newInputStream(input.source)) {
            return Objects.requireNonNull(input.mapper.readPatchedValue(source, input.patch, DOCUMENT, input.validated));
        }
    }

    /**
     * Reference baseline: deserialize the entire input, then perform the equivalent known edits.
     * @param input File fixture
     * @return New typed document
     * @throws IOException If deserialization fails
     */
    @Benchmark
    public Document materializeThenPatch(Input input) throws IOException {
        try (var source = Files.newInputStream(input.source)) {
            return input.applyReference(Objects.requireNonNull(input.mapper.readValue(source, DOCUMENT)));
        }
    }

    /** Output policy is a separate state so object benchmarks do not repeat irrelevant parameters. */
    @Internal
    @State(Scope.Thread)
    public static class OutputPolicy {
        @Param({"streaming", "validated"})
        public String strategy = "streaming";
    }

    /** Per-thread fixture; no complete source byte array or source object is retained. */
    @Internal
    @State(Scope.Thread)
    public static class Input {
        @Param({"16"})
        public int sizeMiB = 16;

        @Param({"flat", "nested", "strings"})
        public String shape = "flat";

        @Param({"replaceLate", "independent", "moveBackward", "copyRecords", "dependent"})
        public String operation = "replaceLate";

        ObjectMapper.CloseableObjectMapper mapper;
        private Path directory;
        Path source;
        Path destination;
        private Path spill;
        JsonPatch patch;
        byte[] patchDocument;
        JsonPatchOptions streaming;
        JsonPatchOptions validated;
        private String payload;
        private @Nullable Details details;
        private int records;
        private final CountingOutput sink = new CountingOutput();

        /**
         * Generates deterministic UTF-8 JSON directly into a file and parses a reusable patch.
         * @throws IOException If fixture creation fails
         */
        @Setup
        public void setup() throws IOException {
            if (sizeMiB < 1 || sizeMiB > 64 || !SHAPES.contains(shape) || !OPERATIONS.contains(operation)) {
                throw new IllegalArgumentException("Use sizeMiB=1..64 and a documented shape and operation");
            }
            mapper = ObjectMapper.create(Map.of("micronaut.serde.serialization.inclusion", "ALWAYS"));
            directory = Files.createTempDirectory("json-patch-file-jmh-");
            source = directory.resolve("source.json");
            destination = directory.resolve("patched.json");
            spill = Files.createDirectory(directory.resolve("spill"));
            try {
                payload = shape.equals("strings") ? "text-雪-\"-\\-\n".repeat(1024) : "payload";
                details = null;
                if (shape.equals("nested")) {
                    for (int depth = 0; depth < 8; depth++) {
                        details = new Details(depth, new BigDecimal("1234.50"), List.of("alpha", "a/b", "~key"), details);
                    }
                }
                writeFixture();
                validated = new JsonPatchOptions(1 << 20, 2L << 30, 1000, 1 << 20, spill, true);
                streaming = validated.withValidationBeforeWrite(false);
                patchDocument = mapper.writeValueAsBytes(operations());
                patch = mapper.readJsonPatch(new ByteArrayInputStream(patchDocument));
            } catch (IOException | RuntimeException | Error e) {
                try {
                    cleanup();
                } catch (IOException cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
                throw e;
            }
        }

        private void writeFixture() throws IOException {
            byte[] prefix = "{\"revision\":0,\"title\":\"Original\",\"enabled\":false,\"records\":[".getBytes(StandardCharsets.UTF_8);
            long bytes = prefix.length;
            records = 0;
            try (var output = new BufferedOutputStream(Files.newOutputStream(source))) {
                output.write(prefix);
                while (bytes < (long) sizeMiB * (1 << 20) || records < 2) {
                    if (records > 0) {
                        output.write(',');
                        bytes++;
                    }
                    byte[] row = mapper.writeValueAsBytes(row(records++));
                    output.write(row);
                    bytes += row.length;
                }
                output.write("],\"archive\":[],\"tail\":\"End\"}".getBytes(StandardCharsets.UTF_8));
            }
        }

        private Row row(int index) {
            return new Row(index, "row-" + index + "-" + payload, (index & 1) == 0, details);
        }

        private List<Map<String, Object>> operations() {
            int last = records - 1;
            return switch (operation) {
                case "addFirst" -> List.of(value("add", "/records/0", row(-1)));
                case "addLast" -> List.of(value("add", "/records/-", row(-1)));
                case "removeMiddle" -> List.of(Map.of("op", "remove", "path", "/records/" + records / 2));
                case "removeRecords" -> List.of(Map.of("op", "remove", "path", "/records"));
                case "replaceEarly" -> List.of(value("replace", "/records/0/name", "Revised"));
                case "replaceLate" -> List.of(value("replace", "/records/" + last + "/name", "Revised"));
                case "moveForward" -> List.of(from("move", "/records/0", "/records/" + last));
                case "moveBackward" -> List.of(from("move", "/records/" + last, "/records/0"));
                case "copyRecords" -> List.of(from("copy", "/records", "/archive"));
                case "test" -> List.of(value("test", "/records/" + last, row(last)));
                case "independent" -> List.of(value("add", "/revision", 1), value("replace", "/title", "Revised"),
                    value("replace", "/enabled", true), Map.of("op", "remove", "path", "/tail"));
                case "dependent" -> List.of(value("add", "/records/0", row(-1)),
                    from("move", "/records/" + records, "/records/1"), value("replace", "/records/1/name", "Moved"),
                    from("copy", "/records/0", "/records/-"), Map.of("op", "remove", "path", "/records/2"),
                    value("test", "/records/0/id", -1));
                default -> throw new IllegalStateException("Unknown operation: " + operation);
            };
        }

        private static Map<String, Object> value(String op, String path, Object value) {
            return Map.of("op", op, "path", path, "value", value);
        }

        private static Map<String, Object> from(String op, String from, String path) {
            return Map.of("op", op, "from", from, "path", path);
        }

        private JsonPatchOptions options(OutputPolicy policy) {
            return switch (policy.strategy) {
                case "streaming" -> streaming;
                case "validated" -> validated;
                default -> throw new IllegalArgumentException("Unknown strategy: " + policy.strategy);
            };
        }

        /** Known edits form an independent fixture oracle, not another JSON Patch interpreter. */
        private Document applyReference(Document document) {
            List<Row> rows = Objects.requireNonNull(document.records());
            switch (operation) {
                case "addFirst" -> rows.addFirst(row(-1));
                case "addLast" -> rows.add(row(-1));
                case "removeMiddle" -> rows.remove(records / 2);
                case "removeRecords" -> {
                    return new Document(0, "Original", false, null, List.of(), "End");
                }
                case "replaceEarly" -> rows.set(0, renamed(rows.getFirst(), "Revised"));
                case "replaceLate" -> rows.set(records - 1, renamed(rows.getLast(), "Revised"));
                case "moveForward" -> rows.add(rows.removeFirst());
                case "moveBackward" -> rows.addFirst(rows.removeLast());
                case "copyRecords" -> {
                    return new Document(0, "Original", false, rows, new ArrayList<>(rows), "End");
                }
                case "test" -> {
                    if (!rows.getLast().equals(row(records - 1))) {
                        throw new IllegalStateException("Reference test failed");
                    }
                }
                case "independent" -> {
                    return new Document(1, "Revised", true, rows, List.of(), null);
                }
                case "dependent" -> {
                    rows.addFirst(row(-1));
                    rows.add(1, rows.removeLast());
                    rows.set(1, renamed(rows.get(1), "Moved"));
                    rows.add(rows.getFirst());
                    rows.remove(2);
                    if (rows.getFirst().id() != -1) {
                        throw new IllegalStateException("Reference test failed");
                    }
                }
                default -> throw new IllegalStateException("Unknown operation: " + operation);
            }
            return document;
        }

        private static Row renamed(Row row, String name) {
            return new Row(row.id(), name, row.active(), row.details());
        }

        /**
         * Releases fixtures and detects leaked replay files after the trial.
         * @throws IOException If cleanup fails or replay files remain
         */
        @TearDown
        public void cleanup() throws IOException {
            try {
                Files.deleteIfExists(source);
                Files.deleteIfExists(destination);
                // Do not recursively delete this directory: leftover tapes indicate a leak.
                Files.delete(spill);
                Files.delete(directory);
            } finally {
                mapper.close();
            }
        }
    }

    /**
     * Runs the complete fixture matrix without timing, comparing every result with known edits.
     * @param args Optional single fixture size in MiB, default 1
     * @throws IOException If fixture verification fails
     */
    public static void main(String[] args) throws IOException {
        int size = args.length == 0 ? 1 : Integer.parseInt(args[0]);
        var benchmark = new JsonPatchFileBenchmark();
        int verified = 0;
        for (String shape : SHAPES) {
            for (String operation : OPERATIONS) {
                var input = new Input();
                input.sizeMiB = size;
                input.shape = shape;
                input.operation = operation;
                input.setup();
                try {
                    Document expected = benchmark.materializeThenPatch(input);
                    requireEqual(expected, benchmark.readPatchedObject(input), shape, operation);
                    for (String strategy : List.of("streaming", "validated")) {
                        var policy = new OutputPolicy();
                        policy.strategy = strategy;
                        long fileBytes = benchmark.writePatchedFile(input, policy);
                        if (fileBytes != benchmark.writePatchedJson(input, policy)) {
                            throw new AssertionError("Output sizes differ: " + shape + "/" + operation + "/" + strategy);
                        }
                        try (var source = Files.newInputStream(input.destination)) {
                            requireEqual(expected, input.mapper.readValue(source, DOCUMENT), shape, operation);
                        }
                    }
                    verified++;
                    System.out.println("Verified " + shape + "/" + operation + ": " + Files.size(input.source) + " bytes, " + input.records + " records");
                } finally {
                    input.cleanup();
                }
            }
        }
        System.out.println("Verified " + verified + " file fixtures across all output modes");
    }

    private static void requireEqual(Document expected, @Nullable Document actual, String shape, String operation) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Patched result differs from reference: " + shape + "/" + operation);
        }
    }

    /** Typed document shared by all file shapes. */
    @Internal
    @Serdeable
    public record Document(int revision, String title, boolean enabled, @Nullable List<Row> records,
                           List<Row> archive, @Nullable String tail) {
    }

    /** Array entry with scalar values and optional nested details. */
    @Internal
    @Serdeable
    public record Row(int id, String name, boolean active, @Nullable Details details) {
    }

    /** Nested object fixture with decimal numbers and escaped pointer-like strings. */
    @Internal
    @Serdeable
    public record Details(int level, BigDecimal amount, List<String> tags, @Nullable Details child) {
    }

    static final class CountingOutput extends OutputStream {
        long bytes;

        @Override
        public void write(int value) {
            bytes++;
        }

        @Override
        public void write(byte[] value, int offset, int length) {
            bytes += length;
        }
    }
}
