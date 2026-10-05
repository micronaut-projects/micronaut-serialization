package example

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.protobuf.annotation.ProtoField
import io.micronaut.serde.protobuf.annotation.ProtoType

// tag::clazz[]
@Serdeable
@EqualsAndHashCode
@ToString
class Coordinates {
    @ProtoField(value = 1, type = ProtoType.SFIXED32) final int latitudeMicros   // <1>
    @ProtoField(value = 2, type = ProtoType.SFIXED32) final int longitudeMicros

    Coordinates(int latitudeMicros, int longitudeMicros) {
        this.latitudeMicros = latitudeMicros
        this.longitudeMicros = longitudeMicros
    }
}
// end::clazz[]
