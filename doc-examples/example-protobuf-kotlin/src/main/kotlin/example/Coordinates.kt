package example

import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.protobuf.annotation.ProtoField
import io.micronaut.serde.protobuf.annotation.ProtoType

// tag::clazz[]
@Serdeable
data class Coordinates(
    @field:ProtoField(value = 1, type = ProtoType.SFIXED32) val latitudeMicros: Int,   // <1>
    @field:ProtoField(value = 2, type = ProtoType.SFIXED32) val longitudeMicros: Int
)
// end::clazz[]
