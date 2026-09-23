from typing import Annotated

from jakarta.inject import Inject
from micronaut.serde import ObjectMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Feature import Feature
from example.Location import Location
from example.Point import Point


@MicronautTest
class LocationTest:
    object_mapper: Annotated[ObjectMapper, Inject]

    @Test
    def test_location(self):
        features = {Feature("Tree"): Point.value_of(100, 50)}
        result = self.object_mapper.writeValueAsString(Location(features))
        location = self.object_mapper.readValue(result, Location)
        assert location is not None
        assert len(location.features) == 1
        name = next(iter(location.features)).name()
        assert name == "Tree"
