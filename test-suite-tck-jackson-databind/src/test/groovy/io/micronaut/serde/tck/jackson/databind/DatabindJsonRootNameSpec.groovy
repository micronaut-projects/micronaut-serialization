package io.micronaut.serde.tck.jackson.databind

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.serde.jackson.JsonRootNameSpec
import spock.lang.Ignore

@Ignore // TODO
class DatabindJsonRootNameSpec extends JsonRootNameSpec {

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of(
                        "jackson.serialization.wrapRootValue", "true",
                        "jackson.deserialization.unwrapRootValue", "true"
                )
        ))
    }

}
