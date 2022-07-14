package io.micronaut.serde.oracle.jdbc.json

import io.micronaut.serde.AbstractBasicSerdeSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class OracleJdbcJsonTextBasicSerdeSpec extends AbstractBasicSerdeSpec {

    @Inject
    OracleJdbcJsonTextObjectMapper jsonMapper


}
