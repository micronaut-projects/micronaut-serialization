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

import io.micronaut.serde.patch.JsonPatch;
import io.micronaut.serde.patch.JsonPatchOptions;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Compares token patching with a materialized document for equivalent modifications. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
public class JsonPatchBenchmark {
    @Param({"1000", "100000"})
    public int elements;

    @Param({"streaming", "validated", "materialized"})
    public String strategy;

    @Param({"replace", "move", "many"})
    public String shape;

    private ObjectMapper mapper;
    private byte[] source;
    private JsonPatch patch;
    private JsonPatchOptions options;
    private Path directory;
    private final CountingOutput output = new CountingOutput();

    /** @throws IOException If fixture setup fails */
    @Setup
    public void setup() throws IOException {
        mapper = ObjectMapper.getDefault();
        directory = Files.createTempDirectory("json-patch-jmh-");
        options = JsonPatchOptions.DEFAULT.withSpillDirectory(directory)
            .withValidationBeforeWrite(strategy.equals("validated"));
        List<Integer> values = new ArrayList<>(elements);
        for (int i = 0; i < elements; i++) {
            values.add(i);
        }
        source = mapper.writeValueAsBytes(Map.of("values", values, "changed", 0));
        String patchText;
        if (shape.equals("replace")) {
            patchText = "[{\"op\":\"replace\",\"path\":\"/changed\",\"value\":1}]";
        } else if (shape.equals("move")) {
            patchText = "[{\"op\":\"move\",\"from\":\"/values/" + (elements - 1) + "\",\"path\":\"/values/0\"}]";
        } else {
            List<Map<String, Object>> operations = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                operations.add(Map.of("op", "replace", "path", "/values/" + i, "value", -i));
            }
            patchText = mapper.writeValueAsString(operations);
        }
        patch = mapper.readJsonPatch(new ByteArrayInputStream(patchText.getBytes(StandardCharsets.UTF_8)));
    }

    /** @return Output byte count
     * @throws IOException If patch application fails
     */
    @Benchmark
    @SuppressWarnings("unchecked")
    public long patch() throws IOException {
        output.bytes = 0;
        if (strategy.equals("materialized")) {
            Map<String, Object> document = (Map<String, Object>) mapper.readValue(source, Object.class);
            List<Integer> values = (List<Integer>) document.get("values");
            if (shape.equals("replace")) {
                document.put("changed", 1);
            } else if (shape.equals("move")) {
                values.add(0, values.remove(elements - 1));
            } else {
                for (int i = 0; i < 20; i++) {
                    values.set(i, -i);
                }
            }
            mapper.writeValue(output, document);
        } else {
            mapper.writePatchedValue(new ByteArrayInputStream(source), patch, output, options);
        }
        return output.bytes;
    }

    /** @throws IOException If temporary storage was not cleaned up */
    @TearDown
    public void cleanup() throws IOException {
        Files.delete(directory);
    }

    private static final class CountingOutput extends OutputStream {
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
