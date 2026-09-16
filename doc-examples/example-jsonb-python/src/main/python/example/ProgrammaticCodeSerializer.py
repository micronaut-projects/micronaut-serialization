from jakarta.json.bind.serializer import JsonbSerializer, SerializationContext
from jakarta.json.stream import JsonGenerator

from example.ProgrammaticCode import ProgrammaticCode


class ProgrammaticCodeSerializer(JsonbSerializer[ProgrammaticCode]):
    def serialize(self, obj: ProgrammaticCode, generator: JsonGenerator, ctx: SerializationContext) -> None:
        generator.write("code:" + obj.value)
