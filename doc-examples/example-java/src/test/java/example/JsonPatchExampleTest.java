package example;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.patch.JsonPatch;
import io.micronaut.serde.patch.JsonPatchOptions;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class JsonPatchExampleTest {
    @Test
    void patchJsonAndReadNewObject(ObjectMapper mapper) throws IOException {
        // tag::patch[]
        JsonPatch patch = mapper.readJsonPatch(input("""
            [
              {"op":"test","path":"/title","value":"Original"},
              {"op":"replace","path":"/title","value":"Revised"}
            ]
            """));
        String original = "{\"title\":\"Original\",\"pages\":200}";

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mapper.writePatchedValue(input(original), patch, output);

        Book revised = mapper.readPatchedValue(input(original), patch, Argument.of(Book.class));
        // end::patch[]
        assertEquals(new Book("Revised", 200), revised);
        assertEquals("{\"title\":\"Revised\",\"pages\":200}", output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void enableTemporaryStorage(ObjectMapper mapper) throws IOException {
        var directory = Files.createTempDirectory("patch-example-");
        try {
            // tag::spill[]
            JsonPatchOptions options = JsonPatchOptions.DEFAULT.withSpillDirectory(directory);
            JsonPatch patch = mapper.readJsonPatch(input("""
                [{"op":"move","from":"/2","path":"/0"}]
                """));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            mapper.writePatchedValue(input("[1,2,3]"), patch, output, options);
            // end::spill[]
            assertEquals("[3,1,2]", output.toString(StandardCharsets.UTF_8));
        } finally {
            Files.delete(directory);
        }
    }

    private static ByteArrayInputStream input(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    @Serdeable
    record Book(String title, int pages) {
    }
}
