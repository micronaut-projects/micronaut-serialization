package io.micronaut.serde.yaml

import io.micronaut.json.JsonMapper

/**
 * This one run shared tck tests
 */
class YamlMapperTCKSpec extends AbstractMicronautYamlSpec{

    @Override
    Class<JsonMapper> getJsonMapperClass() {
        YamlObjectMapper
    }

    @Override
    protected Map<String, Object> getContextProperties() {
        [:]
    }
}
