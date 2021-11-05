package io.micronaut.serde.bson

import io.micronaut.core.type.Argument

import java.nio.charset.StandardCharsets

trait BsonJsonSpec {

    abstract BsonJsonMapper getBsonJsonMapper()

    String asBsonJsonString(Object obj) {
       return new String(getBsonJsonMapper().writeValueAsBytes(obj), StandardCharsets.UTF_8)
    }

    Object objectFromBsonJson(String bsonJson, Class type) {
        return getBsonJsonMapper().readValue(bsonJson.getBytes(StandardCharsets.UTF_8), Argument.of(type))
    }

}