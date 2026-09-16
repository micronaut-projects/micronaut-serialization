package example

import io.micronaut.serde.protobuf.ProtobufMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Why `type` on `@ProtoField` is worth setting: `int32` sign-extends negative
 * values to a full ten bytes, while `sint32` zig-zags them back down to the size of the
 * magnitude.
 */
@MicronautTest(startApplication = false)
class WireRepresentationTest {

    @Inject
    lateinit var protobufMapper: ProtobufMapper

    // tag::negative[]
    @Test
    fun sint32IsFarCheaperForNegativeValues() {
        val belowFreezing = -4250

        val int32Bytes = protobufMapper.writeValueAsBytes(Temperature.AsInt32(belowFreezing)).size
        val sint32Bytes = protobufMapper.writeValueAsBytes(Temperature.AsSint32(belowFreezing)).size

        assertEquals(11, int32Bytes)   // <1>
        assertEquals(3, sint32Bytes)   // <2>
    }
    // end::negative[]

    @Test
    fun sint32CanCostMoreForPositiveValues() {
        val aboveFreezing = 64

        assertEquals(2, protobufMapper.writeValueAsBytes(Temperature.AsInt32(aboveFreezing)).size)
        assertEquals(3, protobufMapper.writeValueAsBytes(Temperature.AsSint32(aboveFreezing)).size)
    }
}
