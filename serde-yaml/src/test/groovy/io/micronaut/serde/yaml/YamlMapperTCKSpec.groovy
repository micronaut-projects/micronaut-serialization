package io.micronaut.serde.yaml

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
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

    void "deserialization - accepts reserved YAML values with string parsing disabled"() {
        given:
        def context = ApplicationContext.run(
                ['micronaut.serde.format.yaml.read-features.boolean-as-strings': false]
        )
        initializeMapper(context)

        expect:
        readYaml('key: ' + value + '\n', Argument.mapOf(String, Object)).key == expected

        cleanup:
        context.close()

        where:
        value || expected
        "yes" || true
        "Yes" || true
        "YES" || true
        "no"  || false
        "No"  || false
        "NO"  || false
        "y"   || "y"
        "Y"   || "Y"
        "n"   || "n"
        "N"   || "N"
        "on"  || true
        "On"  || true
        "ON"  || true
        "off" || false
        "Off" || false
        "OFF" || false
    }
}
