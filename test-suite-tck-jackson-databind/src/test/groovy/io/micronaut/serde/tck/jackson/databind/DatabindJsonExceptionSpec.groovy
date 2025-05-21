package io.micronaut.serde.tck.jackson.databind

import com.fasterxml.jackson.databind.JsonMappingException
import io.micronaut.serde.jackson.JsonExceptionSpec

class DatabindJsonExceptionSpec extends JsonExceptionSpec {

    @Override
    String getPath(Exception e) {
        if (e instanceof JsonMappingException) {
            return e.pathReference
        }
        return "<unknown>"
    }

}
