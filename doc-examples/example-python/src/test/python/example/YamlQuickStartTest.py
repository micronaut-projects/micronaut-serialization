from typing import Annotated

from jakarta.inject import Inject
from micronaut.serde.yaml import YamlObjectMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.YamlLibrary import YamlLibrary


@MicronautTest
class YamlQuickStartTest:
    yaml_mapper: Annotated[YamlObjectMapper, Inject]

    @Test
    def test_write_and_read_yaml(self):
        library = YamlLibrary("City Library", ["The Stand", "VALIS"])

        yaml = self.yaml_mapper.writeValueAsString(library)

        assert yaml == "name: City Library\nbooks:\n- The Stand\n- VALIS\n"
        read = self.yaml_mapper.readValue(yaml, YamlLibrary)
        assert read.name == library.name
        assert list(read.books) == library.books
