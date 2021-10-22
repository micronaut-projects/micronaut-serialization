package io.micronaut.serde.jackson

import io.micronaut.json.JsonMapper

trait JsonSpec {
    String writeJson(JsonMapper jsonMapper, Object bean) {
        new String(jsonMapper.writeValueAsBytes(bean))
    }
}
