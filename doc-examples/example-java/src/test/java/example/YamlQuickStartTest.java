package example;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.yaml.YamlObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class YamlQuickStartTest {

    @Test
    void testWriteAndReadYaml() throws IOException {
        try (ApplicationContext context = ApplicationContext.run()) {
            ObjectMapper yamlMapper = context.getBean(YamlObjectMapper.class);
            YamlLibrary library = new YamlLibrary("City Library", List.of("The Stand", "VALIS"));

            String yaml = yamlMapper.writeValueAsString(library);

            assertEquals("name: City Library\nbooks:\n- The Stand\n- VALIS\n", yaml);
            assertEquals(library, yamlMapper.readValue(yaml, YamlLibrary.class));
        }
    }

    @Test
    void testReadFeatures() throws IOException {
        assertEquals("yes", read(Map.of(), "key: yes\n", Argument.mapOf(String.class, Object.class)).get("key"));
        assertEquals(true, read(
            Map.of("micronaut.serde.format.yaml.read-features.boolean-as-strings", false),
            "key: yes\n",
            Argument.mapOf(String.class, Object.class)
        ).get("key"));

        assertNull(read(Map.of(), "key:\n", Argument.mapOf(String.class, Object.class)).get("key"));
        assertEquals("", read(
            Map.of("micronaut.serde.format.yaml.read-features.empty-string-as-null", false),
            "key:\n",
            Argument.mapOf(String.class, Object.class)
        ).get("key"));

    }

    @Test
    void testScalarWriteFeatures() throws IOException {
        assertEquals("text: \"hello world\"\n", write(
            Map.of("micronaut.serde.format.yaml.write-features.minimize-quotes", false),
            Map.of("text", "hello world")
        ));
        assertEquals("text: |-\n  Hello\n  World\n", write(Map.of(
            "micronaut.serde.format.yaml.write-features.minimize-quotes", false,
            "micronaut.serde.format.yaml.write-features.literal-block-style", true
        ), Map.of("text", "Hello\nWorld")));

        String longText = "1234567890 ".repeat(9).trim();
        assertEquals("- \"" + longText + "\"\n", write(Map.of(
            "micronaut.serde.format.yaml.write-features.minimize-quotes", false,
            "micronaut.serde.format.yaml.write-features.split-lines", false
        ), List.of(longText)));

        assertEquals("value: NaN\n", write(
            Map.of("micronaut.serde.format.yaml.write-features.use-yaml-nonfinite-notation", false),
            Map.of("value", Double.NaN)
        ));
    }

    @Test
    void testLayoutWriteFeatures() throws IOException {
        assertEquals("{values: [A, B]}\n", write(
            Map.of("micronaut.serde.format.yaml.write-features.write-style", "FLOW"),
            Map.of("values", List.of("A", "B"))
        ));
        assertEquals("outer:\n    key: value\n", write(
            Map.of("micronaut.serde.format.yaml.write-features.indent", 4),
            Map.of("outer", Map.of("key", "value"))
        ));
        assertEquals("---\nkey: value\n...\n", write(Map.of(
            "micronaut.serde.format.yaml.write-features.explicit-start", true,
            "micronaut.serde.format.yaml.write-features.explicit-end", true
        ), Map.of("key", "value")));
        assertEquals("---\n{\n  ? \"key\"\n  : \"value\",\n}\n", write(
            Map.of("micronaut.serde.format.yaml.write-features.canonical-output", true),
            Map.of("key", "value")
        ));
        assertEquals("values:\n - A\n - B\n", write(
            Map.of("micronaut.serde.format.yaml.write-features.indent-arrays", true),
            Map.of("values", List.of("A", "B"))
        ));
        assertEquals("values:\n  - A\n  - B\n", write(
            Map.of("micronaut.serde.format.yaml.write-features.indent-arrays-with-indicator", true),
            Map.of("values", List.of("A", "B"))
        ));

        String longKey = "a".repeat(129);
        assertEquals(longKey + ": value\n", write(
            Map.of("micronaut.serde.format.yaml.write-features.allow-long-keys", true),
            Map.of(longKey, "value")
        ));
    }

    private static String write(Map<String, Object> properties, Object value) throws IOException {
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            return context.getBean(YamlObjectMapper.class).writeValueAsString(value);
        }
    }

    private static <T> T read(Map<String, Object> properties, String yaml, Argument<T> type) throws IOException {
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            return context.getBean(YamlObjectMapper.class).readValue(yaml, type);
        }
    }
}
