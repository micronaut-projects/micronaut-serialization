from jakarta.json.bind.serializer import DeserializationContext, JsonbDeserializer
from jakarta.json.stream import JsonParser
from java.lang.reflect import Type

from example.ProgrammaticCode import ProgrammaticCode


class ProgrammaticCodeDeserializer(JsonbDeserializer[ProgrammaticCode]):
    def deserialize(self, parser: JsonParser, ctx: DeserializationContext, rt_type: Type) -> ProgrammaticCode:
        while parser.hasNext():
            if parser.next() == JsonParser.Event.VALUE_STRING:
                return ProgrammaticCode(parser.getString()[5:])
        raise ValueError("Expected a JSON string")
