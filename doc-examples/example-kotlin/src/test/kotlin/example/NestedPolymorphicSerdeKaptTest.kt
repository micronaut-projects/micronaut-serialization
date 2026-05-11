package example

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.Optional

@MicronautTest
class NestedPolymorphicSerdeKaptTest {

    @Test
    fun `nested sealed subtype carries JSON type metadata`() {
        assertTypeMetadata(KaptNestedDynamicData.Data.Foo::class.java, "foo")
        assertTypeMetadata(KaptNestedDynamicData.Data.Bar::class.java, "bar")
    }

    @Test
    fun `nested sealed interface serializes with discriminator when declared type is used`(objectMapper: ObjectMapper) {
        val argument = Argument.of(KaptNestedDynamicData.Data::class.java)
        val json = String(
            objectMapper.writeValueAsBytes(argument, KaptNestedDynamicData.Data.Foo(123L)),
            StandardCharsets.UTF_8
        )

        assertEquals("""{"kind":"foo","value":123}""", json)

        val loaded = objectMapper.readValue(json, argument)
        assertInstanceOf(KaptNestedDynamicData.Data.Foo::class.java, loaded)
        assertEquals(123L, (loaded as KaptNestedDynamicData.Data.Foo).value)
    }

    private fun assertTypeMetadata(type: Class<*>, expectedTypeName: String) {
        val introspection = BeanIntrospector.SHARED.findIntrospection(type).get()

        assertEquals(Optional.of(expectedTypeName), introspection.stringValue(SerdeConfig::class.java, SerdeConfig.TYPE_NAME))
        assertEquals(Optional.of("kind"), introspection.stringValue(SerdeConfig::class.java, SerdeConfig.TYPE_PROPERTY))
        assertArrayEquals(
            arrayOf(expectedTypeName),
            introspection.getValue(SerdeConfig::class.java, SerdeConfig.TYPE_NAMES, Array<String>::class.java).get()
        )
    }
}

class KaptNestedDynamicData {
    @Serdeable
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    sealed interface Data {

        @Serdeable
        @JsonTypeName("foo")
        data class Foo(
            val value: Long,
        ) : Data

        @Serdeable
        @JsonTypeName("bar")
        data class Bar(
            val name: String,
        ) : Data
    }
}
