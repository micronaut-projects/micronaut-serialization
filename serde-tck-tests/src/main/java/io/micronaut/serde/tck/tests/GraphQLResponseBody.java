package io.micronaut.serde.tck.tests;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public class GraphQLResponseBody {
    private final Map<String, Object> specification;

    @JsonCreator
    public GraphQLResponseBody(Map<String, Object> specification) {
        this.specification = specification;
    }

    @JsonAnyGetter
    @JsonInclude
    public Map<String, Object> getSpecification() {
        return specification;
    }
}
