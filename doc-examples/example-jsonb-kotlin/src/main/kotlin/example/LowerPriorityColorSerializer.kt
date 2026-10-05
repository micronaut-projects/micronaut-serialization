package example

import io.micronaut.context.annotation.Requires
import jakarta.annotation.Priority
import jakarta.inject.Singleton
import jakarta.json.bind.serializer.JsonbSerializer
import jakarta.json.bind.serializer.SerializationContext
import jakarta.json.stream.JsonGenerator

@Singleton
@Requires(property = "spec.name", value = "jsonb-extension-beans")
@Priority(20)
class LowerPriorityColorSerializer : JsonbSerializer<Color> {
    override fun serialize(obj: Color, generator: JsonGenerator, ctx: SerializationContext) {
        generator.write("fallback-" + obj.value)
    }
}
