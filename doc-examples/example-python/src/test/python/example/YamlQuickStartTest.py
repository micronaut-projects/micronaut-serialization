from java.lang import Double, Object, String
from java.util import List, Map
from micronaut.context import ApplicationContext
from micronaut.core.type import Argument
from micronaut.serde.yaml import YamlObjectMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.YamlLibrary import YamlLibrary


@MicronautTest
class YamlQuickStartTest:

    @Test
    def test_write_and_read_yaml(self):
        context = ApplicationContext.run()
        try:
            yaml_mapper = context.getBean(YamlObjectMapper)
            library = YamlLibrary("City Library", ["The Stand", "VALIS"])

            yaml = yaml_mapper.writeValueAsString(library)

            assert yaml == "name: City Library\nbooks:\n- The Stand\n- VALIS\n"
            read = yaml_mapper.readValue(yaml, YamlLibrary)
            assert read.name == library.name
            assert list(read.books) == library.books
        finally:
            context.close()

    @Test
    def test_read_features(self):
        assert self.read({}, "key: yes\n", Argument.mapOf(String, Object)).get("key") == "yes"
        assert self.read(
            {"micronaut.serde.format.yaml.read-features.boolean-as-strings": False},
            "key: yes\n",
            Argument.mapOf(String, Object)
        ).get("key") is True

        assert self.read({}, "key:\n", Argument.mapOf(String, Object)).get("key") is None
        assert self.read(
            {"micronaut.serde.format.yaml.read-features.empty-string-as-null": False},
            "key:\n",
            Argument.mapOf(String, Object)
        ).get("key") == ""

    @Test
    def test_scalar_write_features(self):
        assert self.write(
            {"micronaut.serde.format.yaml.write-features.minimize-quotes": False},
            Map.of("text", "hello world")
        ) == "text: \"hello world\"\n"
        assert self.write({
            "micronaut.serde.format.yaml.write-features.minimize-quotes": False,
            "micronaut.serde.format.yaml.write-features.literal-block-style": True
        }, Map.of("text", "Hello\nWorld")) == "text: |-\n  Hello\n  World\n"

        long_text = ("1234567890 " * 9).strip()
        assert self.write({
            "micronaut.serde.format.yaml.write-features.minimize-quotes": False,
            "micronaut.serde.format.yaml.write-features.split-lines": False
        }, List.of(long_text)) == "- \"" + long_text + "\"\n"

        assert self.write(
            {"micronaut.serde.format.yaml.write-features.use-yaml-nonfinite-notation": False},
            Map.of("value", Double.NaN)
        ) == "value: NaN\n"

    @Test
    def test_layout_write_features(self):
        assert self.write(
            {"micronaut.serde.format.yaml.write-features.write-style": "FLOW"},
            Map.of("values", List.of("A", "B"))
        ) == "{values: [A, B]}\n"
        assert self.write(
            {"micronaut.serde.format.yaml.write-features.indent": 4},
            Map.of("outer", Map.of("key", "value"))
        ) == "outer:\n    key: value\n"
        assert self.write({
            "micronaut.serde.format.yaml.write-features.explicit-start": True,
            "micronaut.serde.format.yaml.write-features.explicit-end": True
        }, Map.of("key", "value")) == "---\nkey: value\n...\n"
        assert self.write(
            {"micronaut.serde.format.yaml.write-features.canonical-output": True},
            Map.of("key", "value")
        ) == "---\n{\n  ? \"key\"\n  : \"value\",\n}\n"
        assert self.write(
            {"micronaut.serde.format.yaml.write-features.indent-arrays": True},
            Map.of("values", List.of("A", "B"))
        ) == "values:\n - A\n - B\n"
        assert self.write(
            {"micronaut.serde.format.yaml.write-features.indent-arrays-with-indicator": True},
            Map.of("values", List.of("A", "B"))
        ) == "values:\n  - A\n  - B\n"

        long_key = "a" * 129
        assert self.write(
            {"micronaut.serde.format.yaml.write-features.allow-long-keys": True},
            Map.of(long_key, "value")
        ) == long_key + ": value\n"

    @staticmethod
    def write(properties: dict, value) -> str:
        context = ApplicationContext.run(properties)
        try:
            return context.getBean(YamlObjectMapper).writeValueAsString(value)
        finally:
            context.close()

    @staticmethod
    def read(properties: dict, yaml: str, type: Argument):
        context = ApplicationContext.run(properties)
        try:
            return context.getBean(YamlObjectMapper).readValue(yaml, type)
        finally:
            context.close()
