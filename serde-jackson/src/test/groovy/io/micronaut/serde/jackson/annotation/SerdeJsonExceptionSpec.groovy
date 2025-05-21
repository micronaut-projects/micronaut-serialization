package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.jackson.JsonExceptionSpec

class SerdeJsonExceptionSpec extends JsonExceptionSpec {

    @Override
    String getPath(Exception e) {
        if (e instanceof SerdeException) {
            return e.pathAsString
        }
        return "<unknown>"
    }

}
