package io.micronaut.serde.tck.jackson.databind


import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.serde.jackson.JsonEnumSpec

class DatabindJsonEnumSpec extends JsonEnumSpec {

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of(
                        "jackson.mapper-features.accept-case-insensitive-enums", "true"
                )
        ))
    }

}
