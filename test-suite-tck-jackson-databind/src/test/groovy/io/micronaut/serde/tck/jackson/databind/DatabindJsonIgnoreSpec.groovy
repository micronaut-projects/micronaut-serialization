package io.micronaut.serde.tck.jackson.databind

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.serde.jackson.JsonIgnoreSpec

class DatabindJsonIgnoreSpec extends JsonIgnoreSpec {

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of("jackson.deserialization.failOnUnknownProperties", "true")
        ))
    }

    @Override
    protected String unknownPropertyMessage(String propertyName, String className) {
        return """Unrecognized field "$propertyName" (class $className), not marked as ignorable"""
    }

}
