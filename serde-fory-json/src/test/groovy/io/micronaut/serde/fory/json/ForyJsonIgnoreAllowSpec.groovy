package io.micronaut.serde.fory.json

import io.micronaut.core.naming.NameUtils
import io.micronaut.serde.jackson.JsonIgnoreAllowSpec

class ForyJsonIgnoreAllowSpec extends JsonIgnoreAllowSpec {
    @Override
    protected String unknownPropertyMessage(String propertyName, String className) {
        return "Unknown property [$propertyName] encountered during deserialization of type: ${NameUtils.getSimpleName(className)}"
    }
}
