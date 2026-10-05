from typing import Annotated

from jakarta.inject import Inject
from micronaut.serde import ObjectMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Point import Point


@MicronautTest
class PointTest:
    object_mapper: Annotated[ObjectMapper, Inject]

    @Test
    def test_write_read_point(self):
        result = self.object_mapper.writeValueAsString(Point.value_of(50, 100))
        point = self.object_mapper.readValue(result, Point)
        assert point is not None
        coords = point.coords()
        assert coords[0] == 50
        assert coords[1] == 100
