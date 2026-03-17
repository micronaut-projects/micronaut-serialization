package io.micronaut.serde.bson;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

/**
 * A library class for testing additional properties.
 *
 * @param name The library name
 * @param numBooks The number of books
 * @param extra Additional properties
 */
@Serdeable
public record Library(
    String name,
    int numBooks,
    @JsonAnyGetter
    @JsonAnySetter
    Map<String, Object> extra
) {
}
