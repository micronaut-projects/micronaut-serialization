from typing import Annotated

from jakarta.inject import Inject
from micronaut.serde.protobuf import ProtobufMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.LegacyReading import LegacyReading
from example.SensorReading import SensorReading
from example.SensorReadingTest import reading

# Field numbers, not names, are the contract. As long as numbers are never reused, a service can
# add fields without waiting for its readers to catch up.
@MicronautTest(startApplication=False)
class SchemaEvolutionTest:
    protobuf_mapper: Annotated[ProtobufMapper, Inject]

    # tag::forward[]
    @Test
    def an_old_reader_ignores_fields_it_does_not_know(self):
        current = reading()

        payload = self.protobuf_mapper.writeValueAsBytes(current)
        as_old_reader = self.protobuf_mapper.readValue(payload, LegacyReading)   # <1>

        assert as_old_reader.deviceId == "weather-station-7"
        assert as_old_reader.temperatureMilliCelsius == -4250
    # end::forward[]

    # tag::backward[]
    @Test
    def a_new_reader_tolerates_fields_that_are_not_there_yet(self):
        old = LegacyReading("weather-station-7", 1787000000000, -4250)

        payload = self.protobuf_mapper.writeValueAsBytes(old)
        as_new_reader = self.protobuf_mapper.readValue(payload, SensorReading)   # <1>

        assert as_new_reader.temperatureMilliCelsius == -4250
        assert as_new_reader.location is None
        assert as_new_reader.signalStrengths is None
        assert as_new_reader.batteryLow is False
    # end::backward[]

    @Test
    def repeated_values_survive_the_round_trip(self):
        value = reading()
        decoded = self.protobuf_mapper.readValue(
            self.protobuf_mapper.writeValueAsBytes(value), SensorReading)
        assert list(decoded.signalStrengths) == [-71, -68, -74]
