package io.micronaut.serde.jmespath;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.json.tree.JsonArray;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.json.tree.JsonObject;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.LookaheadDecoder;
import io.micronaut.serde.jmespath.model.ArrayFlattenExpressionJson;
import io.micronaut.serde.jmespath.model.ArrayItemAtExpressionJson;
import io.micronaut.serde.jmespath.model.ArraySliceExpressionJson;
import io.micronaut.serde.jmespath.model.ArrayWildcardExpressionJson;
import io.micronaut.serde.jmespath.model.JsonPath;
import io.micronaut.serde.jmespath.model.JsonPathExpression;
import io.micronaut.serde.jmespath.model.KeyExpressionJson;
import io.micronaut.serde.jmespath.model.MultiSelectKeyValueExpressionJson;
import io.micronaut.serde.jmespath.model.MultiSelectListExpressionJson;
import io.micronaut.serde.jmespath.model.WildcardJsonPathExpression;
import io.micronaut.serde.support.deserializers.buffer.BufferedDecoder;
import io.micronaut.serde.support.deserializers.buffer.BufferedLookaheadDecoder;
import io.micronaut.serde.support.util.JsonNodeDecoder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SerdeJmesPathDecoder {

    public static JsonNode decode(Decoder decoder, String input) {
        JsonPath jsonPath = SerdeJmesPathParser.parse(input);
        List<JsonPathExpression> expressions = jsonPath.expressions();
        try {
            return process((LookaheadDecoder) decoder, expressions);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode process(LookaheadDecoder decoder, List<JsonPathExpression> expressions) throws IOException {
        for (int i = 0; i < expressions.size(); i++) {
            JsonPathExpression expression = expressions.get(i);
            if (expression instanceof ArrayFlattenExpressionJson) {
                List<JsonPathExpression> toFlattenPath = expressions.subList(0, i);
                JsonNode result = process(decoder, toFlattenPath, 0);
                if (result == null) {
                    return null;
                }
                List<JsonPathExpression> next = new ArrayList<>();
                next.add(new ArrayWildcardExpressionJson());
                next.addAll(expressions.subList(i + 1, expressions.size()));
                List<JsonNode> flattened = new ArrayList<>();
                if (result.isArray()) {
                    // Flatten the array
                    for (JsonNode value : result.values()) {
                        if (value.isArray()) {
                            value.values().forEach(flattened::add);
                        } else {
                            flattened.add(value);
                        }
                    }
                } else {
                    flattened.add(result);
                }
                JsonNodeDecoder jsonNodeDecoder = JsonNodeDecoder.create(
                    JsonNode.createArrayNode(flattened),
                    LimitingStream.DEFAULT_LIMITS
                );
                return process(jsonNodeDecoder, next);
            }
        }
        return process(decoder, expressions, 0);
    }

    @Nullable
    private static JsonNode process(LookaheadDecoder decoder, List<JsonPathExpression> jsonPathExpressions, int pathIndex) throws IOException {
        if (jsonPathExpressions.size() <= pathIndex) {
            return decoder.decodeNode();
        }
        JsonPathExpression jsonPathExpression = jsonPathExpressions.get(pathIndex);
        if (jsonPathExpression instanceof MultiSelectListExpressionJson multiSelectListExpression) {
            if (decoder.lookahead() == LookaheadDecoder.TokenType.START_OBJECT) {
                List<JsonPath> paths = multiSelectListExpression.paths();
                List<JsonNode> selection = new ArrayList<>(paths.size());
                try (LookaheadDecoder bufferedDecoder = BufferedDecoder.of(decoder).decodeObject()) {
                    for (JsonPath path : paths) {
                        JsonNode result = process(bufferedDecoder, path.expressions(), 0);
                        if (result == null) {
                            selection.add(JsonNode.nullNode());
                        } else {
                            selection.add(result);
                        }
                        bufferedDecoder.finishStructure(true);
                        bufferedDecoder.close();
                    }
                    return JsonArray.createArrayNode(selection);
                }
            }
        }
        if (jsonPathExpression instanceof MultiSelectKeyValueExpressionJson multiSelectKeyValueExpression) {
            if (decoder.lookahead() == LookaheadDecoder.TokenType.START_OBJECT) {
                List<Map.Entry<String, JsonPath>> entries = multiSelectKeyValueExpression.keyValuesExpressions();
                LinkedHashMap<String, JsonNode> selection = CollectionUtils.newLinkedHashMap(entries.size());
                try (BufferedLookaheadDecoder bufferedDecoder = BufferedDecoder.of(decoder, false)) {
                    for (Map.Entry<String, JsonPath> pathEntry : entries) {
                        JsonNode result = process(bufferedDecoder, pathEntry.getValue().expressions(), 0);
                        if (result == null) {
                            result = JsonNode.nullNode();
                        }
                        selection.put(pathEntry.getKey(), result);
                        bufferedDecoder.close();
                    }
                }
                return JsonObject.createObjectNode(selection);
            } else {
                throw new IllegalStateException();
            }
        }
        if (jsonPathExpression instanceof KeyExpressionJson keyExpression) {
            LookaheadDecoder.TokenType lookahead = decoder.lookahead();
            if (lookahead == LookaheadDecoder.TokenType.START_OBJECT) {
                LookaheadDecoder objectDecoder = decoder.decodeObject();
                try {
                    for (String key = objectDecoder.decodeKey(); key != null; key = objectDecoder.decodeKey()) {
                        if (key.equals(keyExpression.key())) {
                            return process(objectDecoder, jsonPathExpressions, pathIndex + 1);
                        } else {
                            objectDecoder.skipValue();
                        }
                    }
                } finally {
                    objectDecoder.finishStructure(true);
                }
                return null;
            }
            if (lookahead == LookaheadDecoder.TokenType.KEY) {
                for (String key = decoder.decodeKey(); key != null; key = decoder.decodeKey()) {
                    if (key.equals(keyExpression.key())) {
                        return process(decoder, jsonPathExpressions, pathIndex + 1);
                    } else {
                        decoder.skipValue();
                    }
                }
                return null;
            }
            decoder.skipValue();
            return null;
        }
        if (jsonPathExpression instanceof ArrayItemAtExpressionJson arrayItemAtExpression) {
            if (decoder.lookahead() == LookaheadDecoder.TokenType.START_ARRAY) {
                int requiredIndex = arrayItemAtExpression.index();
                if (requiredIndex < 0) {
                    try (LookaheadDecoder bufferedDecoder = BufferedDecoder.of(decoder)) {
                        long count = countItems(bufferedDecoder);
                        long newIndex = count + requiredIndex;
                        return findArrayItem(bufferedDecoder, newIndex, jsonPathExpressions, pathIndex + 1);
                    }
                }
                return findArrayItem(decoder, requiredIndex, jsonPathExpressions, pathIndex + 1);
            }
            decoder.skipValue();
            return null;
        }
        if (jsonPathExpression instanceof ArrayWildcardExpressionJson) {
            if (decoder.lookahead() == LookaheadDecoder.TokenType.START_ARRAY) {
                List<JsonNode> array = new ArrayList<>();
                LookaheadDecoder arrayDecoder = decoder.decodeArray();
                try {
                    while (arrayDecoder.hasNextArrayValue()) {
                        JsonNode node = process(arrayDecoder, jsonPathExpressions, pathIndex + 1);
                        if (node != null) {
                            array.add(node);
                        }
                    }
                } finally {
                    arrayDecoder.finishStructure(true);
                }
                return JsonArray.createArrayNode(array);
            }
            decoder.skipValue();
            return null;
        }
        if (jsonPathExpression instanceof WildcardJsonPathExpression) {
            if (decoder.lookahead() == LookaheadDecoder.TokenType.START_OBJECT) {
                List<JsonNode> array = new ArrayList<>();
                LookaheadDecoder objectDecoder = decoder.decodeObject();
                try {
                    while (objectDecoder.decodeKey() != null) {
                        JsonNode node = process(objectDecoder, jsonPathExpressions, pathIndex + 1);
                        if (node != null) {
                            array.add(node);
                        }
                    }
                } finally {
                    objectDecoder.finishStructure(true);
                }
                return JsonArray.createArrayNode(array);
            }
            decoder.skipValue();
            return null;
        }
        if (jsonPathExpression instanceof ArraySliceExpressionJson slice) {
            if (decoder.lookahead() == LookaheadDecoder.TokenType.START_ARRAY) {
                Long from = slice.from();
                Long to = slice.to();
                if (from != null && from < 0 || to != null && to < 0) {
                    try (LookaheadDecoder bufferedDecoder = BufferedDecoder.of(decoder)) {
                        long count = countItems(bufferedDecoder);
                        if (from != null && from < 0) {
                            from = from + count;
                        }
                        if (to != null && to < 0) {
                            to = to + count;
                        }
                        return sliceArray(
                            bufferedDecoder,
                            new ArraySliceExpressionJson(from, to, slice.step()),
                            jsonPathExpressions,
                            pathIndex
                        );
                    }
                }
                return sliceArray(decoder, slice, jsonPathExpressions, pathIndex);
            }
            decoder.skipValue();
            return null;
        }
        throw new IllegalArgumentException("Unsupported path expression: " + jsonPathExpression);
    }

    private static JsonNode sliceArray(LookaheadDecoder decoder,
                                       ArraySliceExpressionJson slice,
                                       List<JsonPathExpression> jsonPathExpressions, int pathIndex) throws IOException {
        long from = slice.from() == null ? 0 : slice.from();
        long to = slice.to() == null ? Long.MAX_VALUE : slice.to();
        long step = slice.step() == null ? 1 : slice.step();
        List<JsonNode> array = new ArrayList<>();
        LookaheadDecoder arrayDecoder = decoder.decodeArray();
        try {
            for (long index = from; arrayDecoder.hasNextArrayValue() && to < index; index += step) {
                JsonNode node = process(arrayDecoder, jsonPathExpressions, pathIndex + 1);
                if (node != null) {
                    array.add(node);
                }
            }
        } finally {
            arrayDecoder.finishStructure(true);
        }
        return JsonArray.createArrayNode(array);
    }

    private static JsonNode findArrayItem(LookaheadDecoder decoder,
                                          long requiredIndex,
                                          List<JsonPathExpression> jsonPathExpressions,
                                          int pathIndex) throws IOException {
        LookaheadDecoder arrayDecoder = decoder.decodeArray();
        try {
            long index = 0;
            while (arrayDecoder.hasNextArrayValue()) {
                if (index++ == requiredIndex) {
                    return process(arrayDecoder, jsonPathExpressions, pathIndex);
                }
                arrayDecoder.skipValue();
            }
        } finally {
            arrayDecoder.finishStructure(true);
        }
        return null;
    }

    private static long countItems(LookaheadDecoder buffered) throws IOException {
        LookaheadDecoder arrayDecoder = buffered.decodeArray();
        long count = 0;
        while (arrayDecoder.hasNextArrayValue()) {
            arrayDecoder.skipValue();
            count++;
        }
        arrayDecoder.finishStructure(true);
        arrayDecoder.close();
        return count;
    }

}
