package io.micronaut.serde.tck.jackson.databind

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.serde.jackson.JsonIgnoreAllowSpec

class DatabindJsonIgnoreAllowSpec extends JsonIgnoreAllowSpec {

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of("jackson.deserialization-features.fail-on-unknown-properties", "true")
        ))
    }

    @Override
    protected String unknownPropertyMessage(String propertyName, String className) {
        return """Unrecognized property "$propertyName" (class $className), not marked as ignorable"""
    }
}
