package io.micronaut.serde.toml.jackson.databind

import io.micronaut.serde.toml.AbstractJacksonDatabindTomlSpec
import tools.jackson.dataformat.toml.TomlMapper

class DatabindTomlBasicSerdeSpec extends AbstractJacksonDatabindTomlSpec {

    private final TomlMapper jacksonTomlMapper = new TomlMapper()

    @Override
    TomlMapper getDatabindTomlMapper() {
        jacksonTomlMapper
    }
}
