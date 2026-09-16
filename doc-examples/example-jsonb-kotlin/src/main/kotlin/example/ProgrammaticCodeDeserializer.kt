package example

import jakarta.json.bind.serializer.DeserializationContext
import jakarta.json.bind.serializer.JsonbDeserializer
import jakarta.json.stream.JsonParser
import java.lang.reflect.Type

class ProgrammaticCodeDeserializer : JsonbDeserializer<ProgrammaticCode> {
    override fun deserialize(parser: JsonParser, ctx: DeserializationContext, rtType: Type): ProgrammaticCode {
        while (parser.hasNext()) {
            if (parser.next() == JsonParser.Event.VALUE_STRING) {
                return ProgrammaticCode(parser.string.substring(5))
            }
        }
        throw IllegalStateException("Expected a JSON string")
    }
}
