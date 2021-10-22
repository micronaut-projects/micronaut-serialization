package io.micronaut.serde.jackson.object

import io.micronaut.json.JsonMapper
import io.micronaut.serde.jackson.JsonSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class ObjectSerializerSpec extends Specification implements JsonSpec {
    @Inject @Shared JsonMapper jsonMapper
    void "test write simple"() {
        when:
        def bean = new Simple(name: "Test")
        def result = writeJson(jsonMapper, bean)

        then:
        result == '{"name":"Test"}'
    }
}