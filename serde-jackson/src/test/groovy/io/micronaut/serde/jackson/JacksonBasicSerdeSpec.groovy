package io.micronaut.serde.jackson

import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractBasicSerdeSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class JacksonBasicSerdeSpec extends AbstractBasicSerdeSpec {

    @Inject
    JsonMapper jsonMapper

}
