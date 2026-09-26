package example

import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.protobuf.annotation.ProtoField
import io.micronaut.serde.protobuf.annotation.ProtoType

/**
 * The same value under the two representations protobuf offers for a signed 32-bit integer.
 */
class Temperature private constructor() {

    // tag::clazz[]
    @Serdeable
    data class AsInt32(@field:ProtoField(1) val milliCelsius: Int)

    @Serdeable
    data class AsSint32(@field:ProtoField(value = 1, type = ProtoType.SINT32) val milliCelsius: Int)
    // end::clazz[]
}
