package example

import jakarta.json.bind.serializer.JsonbSerializer
import jakarta.json.bind.serializer.SerializationContext
import jakarta.json.stream.JsonGenerator

class ProgrammaticCodeSerializer : JsonbSerializer<ProgrammaticCode> {
    override fun serialize(obj: ProgrammaticCode, generator: JsonGenerator, ctx: SerializationContext) {
        generator.write("code:" + obj.value)
    }
}
