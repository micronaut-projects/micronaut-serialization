package io.micronaut.serde.jackson.annotation

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.jackson.JsonExceptionSpec

class SerdeJsonExceptionSpec extends JsonExceptionSpec {

    @Override
    String getPath(Exception e) {
        if (e instanceof SerdeException) {
            return e.pathAsString
        }
        return "<serde-unknown>"
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of("micronaut.serde.deserialization.ignore-unknown", "false")
        ))
    }

}
