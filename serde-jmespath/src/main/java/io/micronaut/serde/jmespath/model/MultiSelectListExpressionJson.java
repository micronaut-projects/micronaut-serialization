package io.micronaut.serde.jmespath.model;

import java.util.List;

public record MultiSelectListExpressionJson(List<JsonPath> paths) implements JsonPathExpression {
}
