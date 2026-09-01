package example;

import io.micronaut.serde.protobuf.ProtobufMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Why {@code type} on {@code @ProtoField} is worth setting: {@code int32} sign-extends negative
 * values to a full ten bytes, while {@code sint32} zig-zags them back down to the size of the
 * magnitude.
 */
@MicronautTest(startApplication = false)
class WireRepresentationTest {

    @Inject
    ProtobufMapper protobufMapper;

    // tag::negative[]
    @Test
    void sint32IsFarCheaperForNegativeValues() throws Exception {
        int belowFreezing = -4250;

        int int32Bytes = protobufMapper.writeValueAsBytes(new Temperature.AsInt32(belowFreezing)).length;
        int sint32Bytes = protobufMapper.writeValueAsBytes(new Temperature.AsSint32(belowFreezing)).length;

        assertEquals(11, int32Bytes);   // <1>
        assertEquals(3, sint32Bytes);   // <2>
    }
    // end::negative[]

    @Test
    void sint32CanCostMoreForPositiveValues() throws Exception {
        int aboveFreezing = 64;

        assertEquals(2, protobufMapper.writeValueAsBytes(new Temperature.AsInt32(aboveFreezing)).length);
        assertEquals(3, protobufMapper.writeValueAsBytes(new Temperature.AsSint32(aboveFreezing)).length);
    }
}
