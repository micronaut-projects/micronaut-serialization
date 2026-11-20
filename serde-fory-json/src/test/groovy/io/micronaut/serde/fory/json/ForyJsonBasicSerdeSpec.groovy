package io.micronaut.serde.fory.json

import io.micronaut.context.annotation.Property
import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractBasicSerdeSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@Property(name = "micronaut.serde.deserialization.fail-on-null-for-primitives", value = "false")
@MicronautTest
class ForyJsonBasicSerdeSpec extends AbstractBasicSerdeSpec {
    @Inject
    JsonMapper jsonMapper

    void "fory mapper is used"() {
        expect:
        jsonMapper instanceof ForyJsonMapper
    }
}
