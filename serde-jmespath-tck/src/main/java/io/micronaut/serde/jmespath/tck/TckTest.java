package io.micronaut.serde.jmespath.tck;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.support.util.JsonNodeToStringUtil;

import java.io.IOException;

public record TckTest(String name, JsonNode given, String expression, @Nullable JsonNode result,
                      @Nullable String error) {

    public String getGivenAsString() throws IOException {
        return JsonNodeToStringUtil.toString(given);
    }

    public String getResultAsString() throws IOException {
        return JsonNodeToStringUtil.toString(result);
    }

    @Override
    public String toString() {
        try {
            return "TckTest{" +
                "name=" + name +
                ", given=" + getGivenAsString() +
                ", expression='" + expression + '\'' +
                ", result=" + getResultAsString() +
                ", error=" + error +
                '}';
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
