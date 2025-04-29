package io.micronaut.serde.jmespath.tck;

import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record TckDefinition(JsonNode given, List<TckCase> cases) {

    public List<TckTest> toTests(String suiteName) {
        return cases.stream().map(c -> new TckTest(suiteName, given, c.expression, c.result)).toList();
    }

    @Serdeable
    record TckCase(String expression, JsonNode result) {
    }
}
