from jakarta.annotation import Priority
from jakarta.inject import Singleton
from jakarta.json.bind.serializer import DeserializationContext, JsonbDeserializer
from jakarta.json.stream import JsonParser
from java.lang.reflect import Type
from micronaut.context.annotation import Requires

from example.Color import Color


@Singleton
@Requires(property="spec.name", value="jsonb-extension-beans")
@Priority(10)
class ColorDeserializer(JsonbDeserializer[Color]):
    def deserialize(self, parser: JsonParser, ctx: DeserializationContext, rt_type: Type) -> Color:
        while parser.hasNext():
            if parser.next() == JsonParser.Event.VALUE_STRING:
                return Color(parser.getString()[1:])
        raise ValueError("Expected a JSON string")
