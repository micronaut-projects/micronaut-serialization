package io.micronaut.serde.tck.jackson.databind

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.serde.jackson.JsonFilterSpec
import spock.lang.Ignore

@Ignore("Fails in MN5")
class DatabindJsonFilterSpec extends JsonFilterSpec {

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of("jackson.serialization-inclusion=always", "ALWAYS")
        ))
    }

}
