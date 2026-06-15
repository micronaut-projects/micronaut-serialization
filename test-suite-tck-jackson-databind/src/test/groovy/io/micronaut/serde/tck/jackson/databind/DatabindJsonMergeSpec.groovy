package io.micronaut.serde.tck.jackson.databind

import io.micronaut.core.type.Argument
import io.micronaut.jackson.databind.JacksonDatabindMapper
import io.micronaut.serde.jackson.JsonMergeSpec

import java.nio.charset.StandardCharsets

class DatabindJsonMergeSpec extends JsonMergeSpec {
    @Override
    protected boolean expectsRecordLikeUpdateUnsupported() {
        false
    }

    @Override
    protected boolean appliesJsonPropertyDefaultValueOnRead() {
        false
    }

    @Override
    protected boolean failsOnMissingRequiredPropertyOnRead() {
        false
    }

    @Override
    protected boolean preservesAbsentUnwrappedValueOnUpdate() {
        false
    }

    @Override
    protected Object update(Object value, Argument type, Object overrides) {
        ((JacksonDatabindMapper) jsonMapper).objectMapper.updateValue(value, overrides)
    }

    @Override
    protected Object updateBytes(Object value, Argument type, String json) {
        ((JacksonDatabindMapper) jsonMapper).objectMapper.readerForUpdating(value).readValue(json.getBytes(StandardCharsets.UTF_8))
    }

    @Override
    protected Object updateStream(Object value, Argument type, String json) {
        ((JacksonDatabindMapper) jsonMapper).objectMapper.readerForUpdating(value)
                .readValue(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
    }

    @Override
    protected Object updateBuffer(Object value, Argument type, String json) {
        updateBytes(value, type, json)
    }
}
