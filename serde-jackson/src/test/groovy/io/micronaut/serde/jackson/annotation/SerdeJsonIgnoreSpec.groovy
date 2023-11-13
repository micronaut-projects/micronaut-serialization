package io.micronaut.serde.jackson.annotation

import io.micronaut.core.naming.NameUtils
import io.micronaut.serde.jackson.JsonIgnoreSpec

class SerdeJsonIgnoreSpec extends JsonIgnoreSpec {
    @Override
    protected String unknownPropertyMessage(String propertyName, String className) {
        return "Unknown property [$propertyName] encountered during deserialization of type: ${NameUtils.getSimpleName(className)}"
    }
}
