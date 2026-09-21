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

import com.flipkart.zjsonpatch.Jackson3JsonPatch;
import com.github.fge.jsonpatch.JsonPatch;
import io.micronaut.core.annotation.Internal;
import org.eclipse.parsson.JsonProviderImpl;
import org.eclipse.parsson.api.JsonConfig;
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
import org.openjdk.jmh.annotations.Warmup;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Compares file-to-JSON patching, including source parsing, patch application, and serialization. */
@Internal
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
public class JsonPatchComparisonBenchmark {
    private static final List<String> STACKS = List.of("serde-streaming", "serde-validated", "zjsonpatch",
        "zjsonpatch-inplace", "java-json-tools", "parsson");

    /**
     * Reads a fresh file stream and writes the patched JSON to the same counting sink for every stack.
     * @param input Shared large-file fixture
     * @param implementation Patch library and pre-parsed patch
     * @return Serialized output size
     * @throws Exception If parsing, patching, or serialization fails
     */
    @Benchmark
    public long writePatchedJson(JsonPatchFileBenchmark.Input input, Implementation implementation) throws Exception {
        implementation.output.bytes = 0;
        try (var source = Files.newInputStream(input.source)) {
            implementation.writer.write(source, implementation.output);
        }
        return implementation.output.bytes;
    }

    /** Per-thread library state. Source documents are always parsed inside the timed operation. */
    @Internal
    @State(Scope.Thread)
    public static class Implementation {
        @Param({"serde-streaming", "serde-validated", "zjsonpatch", "zjsonpatch-inplace", "java-json-tools", "parsson"})
        public String stack = "serde-streaming";

        private PatchWriter writer;
        private final JsonPatchFileBenchmark.CountingOutput output = new JsonPatchFileBenchmark.CountingOutput();

        /**
         * Caches each library's patch representation and reusable reader/writer factories.
         * @param input Shared fixture; JMH initializes this dependency first
         * @throws Exception If patch parsing fails
         */
        @Setup
        public void setup(JsonPatchFileBenchmark.Input input) throws Exception {
            switch (stack) {
                case "serde-streaming", "serde-validated" -> {
                    var options = stack.equals("serde-streaming") ? input.streaming : input.validated;
                    writer = (source, destination) -> input.mapper.writePatchedValue(source, input.patch, destination, options);
                }
                case "zjsonpatch", "zjsonpatch-inplace" -> {
                    var mapper = new JsonMapper();
                    JsonNode patch = mapper.readTree(input.patchDocument);
                    if (stack.equals("zjsonpatch-inplace")) {
                        // Safe here: each invocation owns a fresh source tree and the fixture root is an object.
                        writer = (source, destination) -> {
                            JsonNode document = mapper.readTree(source);
                            Jackson3JsonPatch.applyInPlace(patch, document);
                            mapper.writeValue(destination, document);
                        };
                    } else {
                        writer = (source, destination) -> mapper.writeValue(destination,
                            Jackson3JsonPatch.apply(patch, mapper.readTree(source)));
                    }
                }
                case "java-json-tools" -> {
                    // This library uses Jackson 2, while the other Jackson stacks use Jackson 3.
                    var mapper = new com.fasterxml.jackson.databind.json.JsonMapper();
                    var patch = JsonPatch.fromJson(mapper.readTree(input.patchDocument));
                    writer = (source, destination) -> mapper.writeValue(destination, patch.apply(mapper.readTree(source)));
                }
                case "parsson" -> {
                    var provider = new JsonProviderImpl();
                    // Parsson counts parser-consumed characters; its default 15M limit rejects the large fixtures.
                    var readers = provider.createReaderFactory(Map.of(JsonConfig.MAX_PARSING_LIMIT, 256L << 20));
                    var writers = provider.createWriterFactory(Map.of());
                    try (var reader = readers.createReader(new ByteArrayInputStream(input.patchDocument))) {
                        var patch = provider.createPatch(reader.readArray());
                        writer = (source, destination) -> {
                            try (var documentReader = readers.createReader(source);
                                 var documentWriter = writers.createWriter(destination)) {
                                documentWriter.write(patch.apply(documentReader.readObject()));
                            }
                        };
                    }
                }
                default -> throw new IllegalArgumentException("Unknown stack: " + stack);
            }
        }
    }

    /**
     * Verifies all shape/operation/library combinations against independent known edits.
     * @param args Optional single fixture size in MiB, default 1
     * @throws Exception If any implementation fails or produces a different JSON value
     */
    public static void main(String[] args) throws Exception {
        int size = args.length == 0 ? 1 : Integer.parseInt(args[0]);
        var benchmark = new JsonPatchComparisonBenchmark();
        var reference = new JsonPatchFileBenchmark();
        var comparisonMapper = new JsonMapper();
        int verified = 0;
        for (String shape : JsonPatchFileBenchmark.SHAPES) {
            for (String operation : JsonPatchFileBenchmark.OPERATIONS) {
                var input = new JsonPatchFileBenchmark.Input();
                input.sizeMiB = size;
                input.shape = shape;
                input.operation = operation;
                input.setup();
                try {
                    ObjectNode expected = (ObjectNode) comparisonMapper.readTree(
                        input.mapper.writeValueAsBytes(reference.materializeThenPatch(input)));
                    // The typed oracle uses null for removed members; JSON must omit them, not write null.
                    if (operation.equals("removeRecords")) {
                        expected.remove("records");
                    } else if (operation.equals("independent")) {
                        expected.remove("tail");
                    }
                    for (String stack : STACKS) {
                        var implementation = new Implementation();
                        implementation.stack = stack;
                        implementation.setup(input);
                        try (var source = Files.newInputStream(input.source);
                             var destination = new BufferedOutputStream(Files.newOutputStream(input.destination))) {
                            implementation.writer.write(source, destination);
                        }
                        JsonNode actual;
                        try (var source = Files.newInputStream(input.destination)) {
                            actual = comparisonMapper.readTree(source);
                        }
                        if (!expected.equals(actual)) {
                            throw new AssertionError("Different JSON result: " + shape + "/" + operation + "/" + stack);
                        }
                        if (Files.size(input.destination) != benchmark.writePatchedJson(input, implementation)) {
                            throw new AssertionError("Different output size: " + shape + "/" + operation + "/" + stack);
                        }
                        verified++;
                    }
                    System.out.println("Verified all stacks: " + shape + "/" + operation + ", " + Files.size(input.source) + " bytes");
                } finally {
                    input.cleanup();
                }
            }
        }
        System.out.println("Verified " + verified + " JSON Patch implementation cases");
    }

    @FunctionalInterface
    private interface PatchWriter {
        void write(InputStream source, OutputStream destination) throws Exception;
    }
}
