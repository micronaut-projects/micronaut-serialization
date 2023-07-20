package io.micronaut.serde.support.serdes

import io.micronaut.context.ApplicationContext
import io.micronaut.json.JsonMapper
import spock.lang.Specification

class ByteArraySerdeSpec extends Specification {
    def 'test byte array shapes'(Boolean writeLegacyByteArrays, String expectedJson) {
        given:
        def ctx = ApplicationContext.run(['micronaut.serde.write-binary-as-array': writeLegacyByteArrays])
        def mapper = ctx.getBean(JsonMapper)

        when:
        def actual = mapper.writeValueAsString([0, 1] as byte[])
        then:
        actual == expectedJson

        cleanup:
        ctx.close()

        where:
        writeLegacyByteArrays | expectedJson
        null                  | '[0,1]'
        true                  | '[0,1]'
        false                 | '"AAE="'
    }
}
