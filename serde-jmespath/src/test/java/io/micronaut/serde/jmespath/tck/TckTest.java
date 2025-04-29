package io.micronaut.serde.jmespath.tck;

import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.support.util.JsonNodeToStringUtil;

import java.io.IOException;

public record TckTest(String suiteName, JsonNode given, String expression, JsonNode result) {

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
                "given=" + getGivenAsString() +
                ", expression='" + expression + '\'' +
                ", result=" + getResultAsString() +
                '}';
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
