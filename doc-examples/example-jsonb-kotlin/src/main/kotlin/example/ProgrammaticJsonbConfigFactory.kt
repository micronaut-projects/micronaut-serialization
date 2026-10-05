package example

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import jakarta.json.bind.JsonbConfig

@Factory
@Requires(property = "spec.name", value = "jsonb-programmatic-config")
class ProgrammaticJsonbConfigFactory {
    @Singleton
    fun jsonbConfig(): JsonbConfig = JsonbConfig()
        .withSerializers(ProgrammaticCodeSerializer())
        .withDeserializers(ProgrammaticCodeDeserializer())
}
