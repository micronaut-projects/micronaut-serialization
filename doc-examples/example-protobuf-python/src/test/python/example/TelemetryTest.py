from typing import Annotated

from jakarta.inject import Inject
from micronaut.serde.protobuf import ProtobufMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.LegacyReading import LegacyReading
from example.Telemetry import Telemetry

# A message that names no positions is numbered by declaration order, and a property can still
# choose its wire representation without choosing its number.
@MicronautTest(startApplication=False)
class TelemetryTest:
    protobuf_mapper: Annotated[ProtobufMapper, Inject]

    @Test
    def is_numbered_by_declaration_order(self):
        telemetry = Telemetry("weather-station-7", 1787000000000, -4250)

        payload = self.protobuf_mapper.writeValueAsBytes(telemetry)

        assert self.protobuf_mapper.readValue(payload, Telemetry).asPolyglotValue() == telemetry
        # the same bytes as declaring 1, 2 and 3 by hand
        assert list(self.protobuf_mapper.writeValueAsBytes(
            LegacyReading("weather-station-7", 1787000000000, -4250))) == list(payload)
