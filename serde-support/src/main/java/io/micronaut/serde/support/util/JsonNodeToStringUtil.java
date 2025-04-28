package io.micronaut.serde.support.util;

import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Encoder;

import java.io.IOException;
import java.util.Map;

public class JsonNodeToStringUtil {

    public static void encode(Encoder encoder, JsonNode node) throws IOException {
        if (node.isContainerNode()) {
            try (Encoder objectEncoder = encoder.encodeObject(Argument.OBJECT_ARGUMENT)) {
                for (Map.Entry<String, JsonNode> e : node.entries()) {
                    objectEncoder.encodeKey(e.getKey());
                    encode(objectEncoder, e.getValue());
                }
            }
            return;
        }
        if (node.isArray()) {
            try (Encoder arrayEncoder = encoder.encodeArray(Argument.OBJECT_ARGUMENT)) {
                for (JsonNode value : node.values()) {
                    encode(arrayEncoder, value);
                }
            }
            return;
        }
        if (node.isString()) {
            encoder.encodeString(node.getStringValue());
            return;
        }
        if (node.isNumber()) {
            Number numberValue = node.getNumberValue();
            if (numberValue instanceof Integer integer) {
                encoder.encodeInt(integer);
                return;
            }
        }
        throw new IllegalStateException("Unsupported node type: " + node.getClass().getSimpleName());
    }

}
