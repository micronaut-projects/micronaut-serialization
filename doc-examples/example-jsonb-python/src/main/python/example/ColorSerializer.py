from jakarta.annotation import Priority
from jakarta.inject import Singleton
from jakarta.json.bind.serializer import JsonbSerializer, SerializationContext
from jakarta.json.stream import JsonGenerator
from micronaut.context.annotation import Requires

from example.Color import Color


@Singleton
@Requires(property="spec.name", value="jsonb-extension-beans")
@Priority(10)
class ColorSerializer(JsonbSerializer[Color]):
    def serialize(self, obj: Color, generator: JsonGenerator, ctx: SerializationContext) -> None:
        generator.write("#" + obj.value)
