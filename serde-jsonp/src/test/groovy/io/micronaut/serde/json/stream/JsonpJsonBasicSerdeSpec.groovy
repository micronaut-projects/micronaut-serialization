package io.micronaut.serde.json.stream

import io.micronaut.context.annotation.Property
import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractBasicSerdeSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject


@Property(name = "micronaut.serde.deserialization.fail-on-null-for-primitives", value = "false")
@MicronautTest
class JsonpJsonBasicSerdeSpec extends AbstractBasicSerdeSpec {

    @Inject
    JsonMapper jsonMapper

}
