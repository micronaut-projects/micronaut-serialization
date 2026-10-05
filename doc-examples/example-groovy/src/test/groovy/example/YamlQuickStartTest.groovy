package example

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.yaml.YamlObjectMapper
import spock.lang.Specification

class YamlQuickStartTest extends Specification {

    void "test write and read yaml"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        ObjectMapper yamlMapper = context.getBean(YamlObjectMapper)
        YamlLibrary library = new YamlLibrary("City Library", List.of("The Stand", "VALIS"))

        when:
        String yaml = yamlMapper.writeValueAsString(library)

        then:
        yaml == "name: City Library\nbooks:\n- The Stand\n- VALIS\n"
        yamlMapper.readValue(yaml, YamlLibrary) == library

        cleanup:
        context.close()
    }

    void "test read features"() {
        expect:
        read([:], "key: yes\n", Argument.mapOf(String, Object)).get("key") == "yes"
        read(
            ["micronaut.serde.format.yaml.read-features.boolean-as-strings": false],
            "key: yes\n",
            Argument.mapOf(String, Object)
        ).get("key") == true

        read([:], "key:\n", Argument.mapOf(String, Object)).get("key") == null
        read(
            ["micronaut.serde.format.yaml.read-features.empty-string-as-null": false],
            "key:\n",
            Argument.mapOf(String, Object)
        ).get("key") == ""
    }

    void "test scalar write features"() {
        expect:
        write(
            ["micronaut.serde.format.yaml.write-features.minimize-quotes": false],
            [text: "hello world"]
        ) == "text: \"hello world\"\n"
        write([
            "micronaut.serde.format.yaml.write-features.minimize-quotes": false,
            "micronaut.serde.format.yaml.write-features.literal-block-style": true
        ], [text: "Hello\nWorld"]) == "text: |-\n  Hello\n  World\n"

        String longText = "1234567890 ".repeat(9).trim()
        write([
            "micronaut.serde.format.yaml.write-features.minimize-quotes": false,
            "micronaut.serde.format.yaml.write-features.split-lines": false
        ], List.of(longText)) == "- \"" + longText + "\"\n"

        write(
            ["micronaut.serde.format.yaml.write-features.use-yaml-nonfinite-notation": false],
            [value: Double.NaN]
        ) == "value: NaN\n"
    }

    void "test layout write features"() {
        expect:
        write(
            ["micronaut.serde.format.yaml.write-features.write-style": "FLOW"],
            [values: List.of("A", "B")]
        ) == "{values: [A, B]}\n"
        write(
            ["micronaut.serde.format.yaml.write-features.indent": 4],
            [outer: [key: "value"]]
        ) == "outer:\n    key: value\n"
        write([
            "micronaut.serde.format.yaml.write-features.explicit-start": true,
            "micronaut.serde.format.yaml.write-features.explicit-end": true
        ], [key: "value"]) == "---\nkey: value\n...\n"
        write(
            ["micronaut.serde.format.yaml.write-features.canonical-output": true],
            [key: "value"]
        ) == "---\n{\n  ? \"key\"\n  : \"value\",\n}\n"
        write(
            ["micronaut.serde.format.yaml.write-features.indent-arrays": true],
            [values: List.of("A", "B")]
        ) == "values:\n - A\n - B\n"
        write(
            ["micronaut.serde.format.yaml.write-features.indent-arrays-with-indicator": true],
            [values: List.of("A", "B")]
        ) == "values:\n  - A\n  - B\n"

        String longKey = "a".repeat(129)
        write(
            ["micronaut.serde.format.yaml.write-features.allow-long-keys": true],
            [(longKey): "value"]
        ) == longKey + ": value\n"
    }

    private static String write(Map<String, Object> properties, Object value) throws IOException {
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            return context.getBean(YamlObjectMapper).writeValueAsString(value)
        }
    }

    private static <T> T read(Map<String, Object> properties, String yaml, Argument<T> type) throws IOException {
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            return context.getBean(YamlObjectMapper).readValue(yaml, type)
        }
    }
}
