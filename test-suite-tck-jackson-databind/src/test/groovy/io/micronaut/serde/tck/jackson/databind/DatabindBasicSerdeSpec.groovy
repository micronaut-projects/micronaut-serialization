package io.micronaut.serde.tck.jackson.databind

import groovy.transform.Memoized
import io.micronaut.context.ApplicationContext
import io.micronaut.jackson.databind.JacksonDatabindMapper
import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractBasicSerdeSpec

class DatabindBasicSerdeSpec extends AbstractBasicSerdeSpec {

    ApplicationContext context = ApplicationContext.run([
            'jackson.deserialization-features.fail-on-null-for-primitives': 'false'
    ])

    @Memoized
    @Override
    JsonMapper getJsonMapper() {
        return context.getBean(JacksonDatabindMapper)
    }

    @Override
    boolean jsonMatches(String result, String expected) {
        jsonMapper.readValue(result, Map) == jsonMapper.readValue(expected, Map)
    }
}
