package io.micronaut.serde.tck.jackson.databind

import io.micronaut.serde.jackson.JsonTypeInfoSpec

class DatabindJsonTypeInfoSpec extends JsonTypeInfoSpec {

    @Override
    protected boolean jacksonCustomOrder() {
        return true
    }
}
