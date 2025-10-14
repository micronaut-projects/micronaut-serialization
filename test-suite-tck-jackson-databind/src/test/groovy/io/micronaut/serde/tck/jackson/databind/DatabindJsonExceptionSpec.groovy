package io.micronaut.serde.tck.jackson.databind

import com.fasterxml.jackson.databind.JsonMappingException
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.serde.jackson.JsonExceptionSpec

class DatabindJsonExceptionSpec extends JsonExceptionSpec {

    @Override
    String getPath(Exception e) {
        if (e instanceof JsonMappingException) {
            return e.pathReference
        }
        return "<unknown>"
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of(
                        "jackson.deserialization.fail-on-unknown-properties", "true",
                        "jackson.parser.STRICT_DUPLICATE_DETECTION", "true"
                )
        ))
    }

}
