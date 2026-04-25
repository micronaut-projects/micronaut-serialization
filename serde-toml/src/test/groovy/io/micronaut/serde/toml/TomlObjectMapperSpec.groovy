package io.micronaut.serde.toml

import io.micronaut.serde.ObjectMapper
import jakarta.inject.Inject
import jakarta.inject.Named
import io.micronaut.test.extensions.spock.annotation.MicronautTest

@MicronautTest
class TomlObjectMapperSpec extends AbstractMicronautTomlSerdeSpec {

    @Inject
    @Named("toml")
    ObjectMapper tomlMapper
}
