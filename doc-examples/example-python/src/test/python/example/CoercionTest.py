from typing import Annotated

from jakarta.inject import Inject
from micronaut.serde import ObjectMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Item import Item


@MicronautTest
class CoercionTest:
    object_mapper: Annotated[ObjectMapper, Inject]

    @Test
    def test_coercion(self):
        item = self.object_mapper.readValue('{"id": "1234", "name": 42, "count": 9.75}', Item)

        assert item.id == 1234
        assert item.name == "42"
        assert item.count == 9
