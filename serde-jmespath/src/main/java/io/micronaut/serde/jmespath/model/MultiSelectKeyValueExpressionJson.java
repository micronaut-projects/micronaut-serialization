package io.micronaut.serde.jmespath.model;

import java.util.List;
import java.util.Map;

public record MultiSelectKeyValueExpressionJson(List<Map.Entry<String, JsonPath>> keyValuesExpressions) implements JsonPathExpression {
}
