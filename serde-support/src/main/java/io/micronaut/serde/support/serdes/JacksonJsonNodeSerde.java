/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.support.serdes;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.support.SerdeRegistrar;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

/**
 * Serde for jackson-databind {@link JsonNode}.
 *
 * @since 2.13.0
 * @author Jonas Konrad
 * Recursive implementation. Recursion depth is limited by decoder/encoder constraints ({@link io.micronaut.serde.LimitingStream}) for security.
 */
final class JacksonJsonNodeSerde implements SerdeRegistrar<JsonNode> {
    private static final Argument<JsonNode> JSON_NODE_ARGUMENT = Argument.of(JsonNode.class);

    @Override
    public Argument<JsonNode> getType() {
        return JSON_NODE_ARGUMENT;
    }

    @Override
    public @Nullable JsonNode deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super JsonNode> type) throws IOException {
        return deserialize(decoder, context, type);
    }

    @Override
    public @Nullable JsonNode deserialize(Decoder decoder, DecoderContext context, Argument<? super JsonNode> type) throws IOException {
        return toJacksonNode(JsonNodeFactory.instance, decoder.decodeNode());
    }

    private static JsonNode toJacksonNode(JsonNodeFactory factory, io.micronaut.json.tree.JsonNode mnNode) {
        if (mnNode.isArray()) {
            ArrayNode n = factory.arrayNode(mnNode.size());
            for (io.micronaut.json.tree.JsonNode value : mnNode.values()) {
                n.add(toJacksonNode(factory, value));
            }
            return n;
        } else if (mnNode.isObject()) {
            ObjectNode n = factory.objectNode();
            for (Map.Entry<String, io.micronaut.json.tree.JsonNode> entry : mnNode.entries()) {
                n.set(entry.getKey(), toJacksonNode(factory, entry.getValue()));
            }
            return n;
        } else if (mnNode.isNull()) {
            return factory.nullNode();
        } else if (mnNode.isBoolean()) {
            return factory.booleanNode(mnNode.getBooleanValue());
        } else if (mnNode.isString()) {
            return factory.textNode(mnNode.getStringValue());
        } else {
            Number numberValue = mnNode.getNumberValue();
            if (numberValue instanceof Integer) {
                return factory.numberNode(numberValue.intValue());
            } else if (numberValue instanceof Long) {
                return factory.numberNode(numberValue.longValue());
            } else if (numberValue instanceof Short) {
                return factory.numberNode(numberValue.shortValue());
            } else if (numberValue instanceof Byte) {
                return factory.numberNode(numberValue.byteValue());
            } else if (numberValue instanceof Float) {
                return factory.numberNode(numberValue.floatValue());
            } else if (numberValue instanceof BigInteger bi) {
                return factory.numberNode(bi);
            } else if (numberValue instanceof BigDecimal bd) {
                return factory.numberNode(bd);
            } else {
                // double, other number types
                return factory.numberNode(numberValue.doubleValue());
            }
        }
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends JsonNode> type, JsonNode node) throws IOException {
        switch (node.getNodeType()) {
            case BOOLEAN -> encoder.encodeBoolean(node.booleanValue());
            case NULL -> encoder.encodeNull();
            case NUMBER -> JsonNodeSerde.encodeNumber(encoder, node.numberValue());
            case STRING -> encoder.encodeString(node.asString());
            case BINARY -> encoder.encodeBinary(node.binaryValue());
            case ARRAY -> {
                try (Encoder array = encoder.encodeArray(JSON_NODE_ARGUMENT)) {
                    for (JsonNode member : node) {
                        serialize(array, context, type, member);
                    }
                }
            }
            case OBJECT -> {
                try (Encoder obj = encoder.encodeObject(JSON_NODE_ARGUMENT)) {
                    for (String k : node.propertyNames()) {
                        obj.encodeKey(k);
                        serialize(encoder, context, type, node.get(k));
                    }
                }
            }
            default -> throw new IllegalArgumentException("Cannot serialize jackson-databind JsonNode of type " + node.getNodeType());
        }
    }
}
