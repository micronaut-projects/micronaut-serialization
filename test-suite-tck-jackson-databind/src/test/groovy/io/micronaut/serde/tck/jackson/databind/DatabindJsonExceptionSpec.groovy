package io.micronaut.serde.tck.jackson.databind

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.serde.exceptions.InvalidFormatException
import io.micronaut.serde.jackson.JsonExceptionSpec
import tools.jackson.databind.DatabindException

class DatabindJsonExceptionSpec extends JsonExceptionSpec {

    @Override
    String getPath(Exception e) {
        if (e instanceof DatabindException) {
            return e.pathReference
        }
        return "<unknown>"
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of(
                        "jackson.deserialization-features.fail-on-unknown-properties", "true",
                        "jackson.parser-features.strict-duplicate-detection", "true"
                )
        ))
    }

}
