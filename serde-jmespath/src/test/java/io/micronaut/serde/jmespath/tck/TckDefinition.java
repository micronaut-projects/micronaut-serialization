package io.micronaut.serde.jmespath.tck;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record TckDefinition(JsonNode given, List<TckCase> cases) {

    public List<TckTest> toTests(String suiteName) {
        return cases.stream().map(c -> new TckTest(c.comment == null ? suiteName : suiteName + " - " + c.comment, given, c.expression, c.result, c.error)).toList();
    }

    @Serdeable
    record TckCase(String expression, @Nullable JsonNode result, @Nullable String error, @Nullable String comment) {
    }
}
