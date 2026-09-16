package example

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.yaml.YamlObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class YamlQuickStartTest {

    @Test
    fun testWriteAndReadYaml() {
        ApplicationContext.run().use { context ->
            val yamlMapper: ObjectMapper = context.getBean(YamlObjectMapper::class.java)
            val library = YamlLibrary("City Library", listOf("The Stand", "VALIS"))

            val yaml = yamlMapper.writeValueAsString(library)

            assertEquals("name: City Library\nbooks:\n- The Stand\n- VALIS\n", yaml)
            assertEquals(library, yamlMapper.readValue(yaml, YamlLibrary::class.java))
        }
    }

    @Test
    fun testReadFeatures() {
        assertEquals("yes", read(mapOf(), "key: yes\n", Argument.mapOf(String::class.java, Any::class.java))["key"])
        assertEquals(true, read(
            mapOf("micronaut.serde.format.yaml.read-features.boolean-as-strings" to false),
            "key: yes\n",
            Argument.mapOf(String::class.java, Any::class.java)
        )["key"])

        assertNull(read(mapOf(), "key:\n", Argument.mapOf(String::class.java, Any::class.java))["key"])
        assertEquals("", read(
            mapOf("micronaut.serde.format.yaml.read-features.empty-string-as-null" to false),
            "key:\n",
            Argument.mapOf(String::class.java, Any::class.java)
        )["key"])
    }

    @Test
    fun testScalarWriteFeatures() {
        assertEquals("text: \"hello world\"\n", write(
            mapOf("micronaut.serde.format.yaml.write-features.minimize-quotes" to false),
            mapOf("text" to "hello world")
        ))
        assertEquals("text: |-\n  Hello\n  World\n", write(mapOf(
            "micronaut.serde.format.yaml.write-features.minimize-quotes" to false,
            "micronaut.serde.format.yaml.write-features.literal-block-style" to true
        ), mapOf("text" to "Hello\nWorld")))

        val longText = "1234567890 ".repeat(9).trim()
        assertEquals("- \"$longText\"\n", write(mapOf(
            "micronaut.serde.format.yaml.write-features.minimize-quotes" to false,
            "micronaut.serde.format.yaml.write-features.split-lines" to false
        ), listOf(longText)))

        assertEquals("value: NaN\n", write(
            mapOf("micronaut.serde.format.yaml.write-features.use-yaml-nonfinite-notation" to false),
            mapOf("value" to Double.NaN)
        ))
    }

    @Test
    fun testLayoutWriteFeatures() {
        assertEquals("{values: [A, B]}\n", write(
            mapOf("micronaut.serde.format.yaml.write-features.write-style" to "FLOW"),
            mapOf("values" to listOf("A", "B"))
        ))
        assertEquals("outer:\n    key: value\n", write(
            mapOf("micronaut.serde.format.yaml.write-features.indent" to 4),
            mapOf("outer" to mapOf("key" to "value"))
        ))
        assertEquals("---\nkey: value\n...\n", write(mapOf(
            "micronaut.serde.format.yaml.write-features.explicit-start" to true,
            "micronaut.serde.format.yaml.write-features.explicit-end" to true
        ), mapOf("key" to "value")))
        assertEquals("---\n{\n  ? \"key\"\n  : \"value\",\n}\n", write(
            mapOf("micronaut.serde.format.yaml.write-features.canonical-output" to true),
            mapOf("key" to "value")
        ))
        assertEquals("values:\n - A\n - B\n", write(
            mapOf("micronaut.serde.format.yaml.write-features.indent-arrays" to true),
            mapOf("values" to listOf("A", "B"))
        ))
        assertEquals("values:\n  - A\n  - B\n", write(
            mapOf("micronaut.serde.format.yaml.write-features.indent-arrays-with-indicator" to true),
            mapOf("values" to listOf("A", "B"))
        ))

        val longKey = "a".repeat(129)
        assertEquals("$longKey: value\n", write(
            mapOf("micronaut.serde.format.yaml.write-features.allow-long-keys" to true),
            mapOf(longKey to "value")
        ))
    }

    private fun write(properties: Map<String, Any>, value: Any): String =
        ApplicationContext.run(properties).use { context ->
            context.getBean(YamlObjectMapper::class.java).writeValueAsString(value)
        }

    private fun <T : Any> read(properties: Map<String, Any>, yaml: String, type: Argument<T>): T =
        ApplicationContext.run(properties).use { context ->
            context.getBean(YamlObjectMapper::class.java).readValue(yaml, type)!!
        }
}
