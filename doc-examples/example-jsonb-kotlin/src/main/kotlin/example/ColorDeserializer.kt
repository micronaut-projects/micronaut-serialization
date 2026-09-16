package example

import io.micronaut.context.annotation.Requires
import jakarta.annotation.Priority
import jakarta.inject.Singleton
import jakarta.json.bind.serializer.DeserializationContext
import jakarta.json.bind.serializer.JsonbDeserializer
import jakarta.json.stream.JsonParser
import java.lang.reflect.Type

@Singleton
@Requires(property = "spec.name", value = "jsonb-extension-beans")
@Priority(10)
class ColorDeserializer : JsonbDeserializer<Color> {
    override fun deserialize(parser: JsonParser, ctx: DeserializationContext, rtType: Type): Color {
        while (parser.hasNext()) {
            if (parser.next() == JsonParser.Event.VALUE_STRING) {
                return Color(parser.string.substring(1))
            }
        }
        throw IllegalStateException("Expected a JSON string")
    }
}
