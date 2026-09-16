from typing import Annotated

from jakarta.inject import Inject
from micronaut.json import JsonMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Coordinates import Coordinates
from example.ReadingCodec import ReadingCodec
from example.SensorReading import SensorReading


def reading() -> SensorReading:
    return SensorReading(
        "weather-station-7",
        1787000000000,
        -4250,                                  # -4.25 °C
        [-71, -68, -74],
        Coordinates(51507400, -127800),         # London, in microdegrees
        False,
    )


@MicronautTest(startApplication=False)
class SensorReadingTest:
    codec: Annotated[ReadingCodec, Inject]
    json_mapper: Annotated[JsonMapper, Inject]

    # tag::roundTrip[]
    @Test
    def reads_back_what_it_wrote(self):
        value = reading()

        payload = self.codec.encode(value)

        assert self.codec.decode(payload).asPolyglotValue() == value
    # end::roundTrip[]

    # tag::size[]
    @Test
    def is_substantially_smaller_than_json(self):
        value = reading()

        protobuf_bytes = len(self.codec.encode(value))
        json_bytes = len(self.json_mapper.writeValueAsBytes(value))

        assert protobuf_bytes == 73                                                        # <1>
        assert protobuf_bytes * 2 < json_bytes, f"protobuf {protobuf_bytes} bytes vs JSON {json_bytes} bytes"  # <2>
    # end::size[]
