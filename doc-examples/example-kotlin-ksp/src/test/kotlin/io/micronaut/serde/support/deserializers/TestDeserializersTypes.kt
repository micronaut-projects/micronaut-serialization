package io.micronaut.serde.support.deserializers

import io.micronaut.core.type.Argument
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
class TestDeserializersTypes {

    @Test
    fun testDataClass(serdeRegistry: SerdeRegistry) {
        val argument = Argument.of(NonNullDto::class.java)
        var findDeserializer: Deserializer<Any> = serdeRegistry.findDeserializer(argument)
            .createSpecific(serdeRegistry.newDecoderContext(null), argument) as Deserializer<Any>
        if (findDeserializer is ErrorCatchingDeserializer) {
            findDeserializer = findDeserializer.deserializer
        }
        Assertions.assertTrue(findDeserializer is SimpleRecordLikeObjectDeserializer)
    }

}
