package io.micronaut.serde.jmespath.model;

import java.util.List;

/**
 * The JSON path representation.
 */
public interface JsonPath {

    List<JsonPathExpression> expressions();

    static JsonPath of(List<JsonPathExpression> expressions) {
        return new DefaultJsonPath(expressions);
    }

    record DefaultJsonPath(List<JsonPathExpression> expressions) implements JsonPath {
    }

}
