from typing import Annotated

from jakarta.inject import Inject
from micronaut.serde import ObjectMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Place import Place
from example.Point import Point


@MicronautTest
class PlaceTest:
    object_mapper: Annotated[ObjectMapper, Inject]

    @Test
    def test_place(self):
        result = self.object_mapper.writeValueAsString(
            Place(Point.value_of(50, 100), Point.value_of(1, 2), Point.value_of(3, 4))
        )
        place = self.object_mapper.readValue(result, Place)
        assert place is not None
        assert place.point.coords()[0] == 50
        assert place.point.coords()[1] == 100
        assert place.pointCustomSer.coords()[0] == 2
        assert place.pointCustomSer.coords()[1] == 1
        assert place.pointCustomDes.coords()[0] == 4
        assert place.pointCustomDes.coords()[1] == 3
        assert result == '{"point":[100,50],"pointCustomSer":[2,1],"pointCustomDes":[3,4]}'
