from typing import Annotated

from jakarta.inject import Inject
from micronaut.serde.protobuf import ProtobufMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Temperature import AsInt32, AsSint32


# Why `type` on `@ProtoField` is worth setting: `int32` sign-extends negative values to a full ten
# bytes, while `sint32` zig-zags them back down to the size of the magnitude.
@MicronautTest(startApplication=False)
class WireRepresentationTest:
    protobuf_mapper: Annotated[ProtobufMapper, Inject]

    # tag::negative[]
    @Test
    def sint32_is_far_cheaper_for_negative_values(self):
        below_freezing = -4250

        int32_bytes = len(self.protobuf_mapper.writeValueAsBytes(AsInt32(below_freezing)))
        sint32_bytes = len(self.protobuf_mapper.writeValueAsBytes(AsSint32(below_freezing)))

        assert int32_bytes == 11   # <1>
        assert sint32_bytes == 3   # <2>
    # end::negative[]

    @Test
    def sint32_can_cost_more_for_positive_values(self):
        above_freezing = 64

        assert len(self.protobuf_mapper.writeValueAsBytes(AsInt32(above_freezing))) == 2
        assert len(self.protobuf_mapper.writeValueAsBytes(AsSint32(above_freezing))) == 3
