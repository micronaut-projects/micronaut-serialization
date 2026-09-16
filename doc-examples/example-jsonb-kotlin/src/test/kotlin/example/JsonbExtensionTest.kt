package example

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.jsonb.JsonbConfiguration
import jakarta.json.bind.Jsonb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JsonbExtensionTest {
    @Test
    fun jsonbExtensionBeansAreRegisteredInPriorityOrder() {
        ApplicationContext.run(mapOf(
            "spec.name" to "jsonb-extension-beans",
            JsonbConfiguration.REFLECTION to JsonbConfiguration.Reflection.AUTO
        )).use { context ->
            val jsonb = context.getBean(Jsonb::class.java)

            assertEquals("\"#ff0000\"", jsonb.toJson(Color("ff0000")))
            assertEquals("00ff00", jsonb.fromJson("\"#00ff00\"", Color::class.java).value)
            assertEquals("\"12 mi\"", jsonb.toJson(Miles(12)))
            assertEquals(15, jsonb.fromJson("\"15 mi\"", Miles::class.java).value)
        }
    }

    @Test
    fun jsonbExtensionsCanBeRegisteredProgrammatically() {
        ApplicationContext.run(mapOf(
            "spec.name" to "jsonb-programmatic-config",
            JsonbConfiguration.REFLECTION to JsonbConfiguration.Reflection.AUTO
        )).use { context ->
            val jsonb = context.getBean(Jsonb::class.java)

            assertEquals("\"code:A1\"", jsonb.toJson(ProgrammaticCode("A1")))
            assertEquals("B2", jsonb.fromJson("\"code:B2\"", ProgrammaticCode::class.java).value)
        }
    }
}
