package io.micronaut.serde.json.stream

import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractBasicSerdeSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject


@MicronautTest
class JsonpJsonBasicSerdeSpec extends AbstractBasicSerdeSpec {

    @Inject
    JsonMapper jsonMapper

}
