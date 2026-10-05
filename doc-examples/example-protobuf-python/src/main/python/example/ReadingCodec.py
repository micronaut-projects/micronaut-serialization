from jakarta.inject import Singleton
from micronaut.serde.protobuf import ProtobufMapper

from example.SensorReading import SensorReading


# tag::clazz[]
@Singleton
class ReadingCodec:

    def __init__(self, protobuf_mapper: ProtobufMapper):   # <1>
        self.protobuf_mapper = protobuf_mapper

    def encode(self, reading: SensorReading) -> bytes:
        return self.protobuf_mapper.writeValueAsBytes(reading)

    def decode(self, payload: bytes) -> SensorReading:
        return self.protobuf_mapper.readValue(payload, SensorReading)
# end::clazz[]
