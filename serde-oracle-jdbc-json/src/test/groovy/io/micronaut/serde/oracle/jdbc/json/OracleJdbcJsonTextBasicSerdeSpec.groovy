package io.micronaut.serde.oracle.jdbc.json

import io.micronaut.context.annotation.Property
import io.micronaut.serde.AbstractBasicSerdeSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@Property(name = "micronaut.serde.deserialization.fail-on-null-for-primitives", value = "false")
@MicronautTest
class OracleJdbcJsonTextBasicSerdeSpec extends AbstractBasicSerdeSpec {

    @Inject
    OracleJdbcJsonTextObjectMapper jsonMapper


}
