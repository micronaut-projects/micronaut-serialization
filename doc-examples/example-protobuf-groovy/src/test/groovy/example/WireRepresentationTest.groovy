package example

import io.micronaut.serde.protobuf.ProtobufMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Why {@code type} on {@code @ProtoField} is worth setting: {@code int32} sign-extends negative
 * values to a full ten bytes, while {@code sint32} zig-zags them back down to the size of the
 * magnitude.
 */
@MicronautTest(startApplication = false)
class WireRepresentationTest extends Specification {

    @Inject
    ProtobufMapper protobufMapper

    // tag::negative[]
    void "sint32 is far cheaper for negative values"() {
        given:
        int belowFreezing = -4250

        when:
        int int32Bytes = protobufMapper.writeValueAsBytes(new Temperature.AsInt32(belowFreezing)).length
        int sint32Bytes = protobufMapper.writeValueAsBytes(new Temperature.AsSint32(belowFreezing)).length

        then:
        int32Bytes == 11    // <1>
        sint32Bytes == 3    // <2>
    }
    // end::negative[]

    void "sint32 can cost more for positive values"() {
        given:
        int aboveFreezing = 64

        expect:
        protobufMapper.writeValueAsBytes(new Temperature.AsInt32(aboveFreezing)).length == 2
        protobufMapper.writeValueAsBytes(new Temperature.AsSint32(aboveFreezing)).length == 3
    }
}
