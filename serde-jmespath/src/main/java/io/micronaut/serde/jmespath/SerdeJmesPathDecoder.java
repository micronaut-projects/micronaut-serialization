package io.micronaut.serde.jmespath;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SerdeJmesPathDecoder {

    public static JsonNode decode(Decoder decoder, String input) {
        JsonPath jsonPath = SerdeJmesPathParser.parse(input);
        PathResult pathResult = process((LookaheadDecoder) decoder, jsonPath.expressions(), 0);
        return pathResult == null ? null : pathResult.asNode();
    }

    @Nullable
    private static SerdeJmesPathDecoder.PathResult process(LookaheadDecoder decoder, List<JsonPathExpression> jsonPathExpressions, int pathIndex) {
        try {
            if (jsonPathExpressions.size() <= pathIndex) {
                return new NodePathResult(decoder.decodeNode());
            }
            JsonPathExpression jsonPathExpression = jsonPathExpressions.get(pathIndex);
            if (jsonPathExpression instanceof MultiSelectListExpressionJson multiSelectListExpression) {
                if (decoder.lookahead() == LookaheadDecoder.TokenType.START_OBJECT) {
                    List<JsonPath> paths = multiSelectListExpression.paths();
                    List<PathResult> selection = new ArrayList<>(paths.size());
                    try (LookaheadDecoder bufferedDecoder = BufferedDecoder.of(decoder).decodeObject()) {
                        for (JsonPath path : paths) {
                            PathResult result = process(bufferedDecoder, path.expressions(), 0);
                            if (result == null) {
                                selection.add(new NodePathResult(JsonNode.nullNode()));
                            } else {
                                selection.add(result);
                            }
                            bufferedDecoder.finishStructure(true);
                            bufferedDecoder.close();
                        }
                        // Prevent ArrayPathResult to be flattened
                        return new NodePathResult(
                            new ArrayPathResult(selection).asNode()
                        );
                    }
                }
            }
            if (jsonPathExpression instanceof MultiSelectKeyValueExpressionJson multiSelectKeyValueExpression) {
                if (decoder.lookahead() == LookaheadDecoder.TokenType.START_OBJECT) {
                    List<Map.Entry<String, JsonPath>> entries = multiSelectKeyValueExpression.keyValuesExpressions();
                    LinkedHashMap<String, PathResult> selection = CollectionUtils.newLinkedHashMap(entries.size());
                    try (BufferedLookaheadDecoder bufferedDecoder = BufferedDecoder.of(decoder, false)) {
                        for (Map.Entry<String, JsonPath> pathEntry : entries) {
                            PathResult result = process(bufferedDecoder, pathEntry.getValue().expressions(), 0);
                            if (result == null) {
                                result = new NodePathResult(JsonNode.nullNode());
                            }
                            selection.put(pathEntry.getKey(), result);
                            bufferedDecoder.close();
                        }
                    }
                    return new ObjectPathResult(selection);
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
            if (jsonPathExpression instanceof ArrayFlattenExpressionJson) {
                if (decoder.lookahead() == LookaheadDecoder.TokenType.START_ARRAY) {
                    List<PathResult> array = new ArrayList<>();
                    LookaheadDecoder arrayDecoder = decoder.decodeArray();
                    try {
                        while (arrayDecoder.hasNextArrayValue()) {
                            boolean isArray = arrayDecoder.lookahead() == LookaheadDecoder.TokenType.START_ARRAY;
                            if (isArray) {
                                try (LookaheadDecoder flattenedArray = arrayDecoder.decodeArray()) {
                                    while (flattenedArray.hasNextArrayValue()) {
                                        PathResult pathResult = process(flattenedArray, jsonPathExpressions, pathIndex + 1);
                                        if (pathResult != null) {
                                            array.add(pathResult);
                                        }
                                    }
                                }
                            } else {
                                PathResult pathResult = process(arrayDecoder, jsonPathExpressions, pathIndex + 1);
                                if (pathResult != null) {
                                    if (pathResult instanceof ArrayPathResult ar) {
                                        array.addAll(ar.values);
                                    } else {
                                        array.add(pathResult);
                                    }
                                }
                            }
                        }
                    } finally {
                        arrayDecoder.finishStructure(true);
                    }
                    return new ArrayPathResult(array);
                }
                decoder.skipValue();
                return null;
            }
            if (jsonPathExpression instanceof ArrayWildcardExpressionJson) {
                if (decoder.lookahead() == LookaheadDecoder.TokenType.START_ARRAY) {
                    List<PathResult> array = new ArrayList<>();
                    LookaheadDecoder arrayDecoder = decoder.decodeArray();
                    try {
                        while (arrayDecoder.hasNextArrayValue()) {
                            PathResult pathResult = process(arrayDecoder, jsonPathExpressions, pathIndex + 1);
                            if (pathResult != null) {
                                array.add(pathResult);
                            }
                        }
                    } finally {
                        arrayDecoder.finishStructure(true);
                    }
                    return new ArrayPathResult(array);
                }
                decoder.skipValue();
                return null;
            }
            if (jsonPathExpression instanceof WildcardJsonPathExpression) {
                if (decoder.lookahead() == LookaheadDecoder.TokenType.START_OBJECT) {
                    List<PathResult> array = new ArrayList<>();
                    LookaheadDecoder objectDecoder = decoder.decodeObject();
                    try {
                        while (objectDecoder.decodeKey() != null) {
                            PathResult pathResult = process(objectDecoder, jsonPathExpressions, pathIndex + 1);
                            if (pathResult != null) {
                                array.add(pathResult);
                            }
                        }
                    } finally {
                        objectDecoder.finishStructure(true);
                    }
                    return new ArrayPathResult(array);
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

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ArrayPathResult sliceArray(LookaheadDecoder decoder,
                                              ArraySliceExpressionJson slice,
                                              List<JsonPathExpression> jsonPathExpressions, int pathIndex) throws IOException {
        long from = slice.from() == null ? 0 : slice.from();
        long to = slice.to() == null ? Long.MAX_VALUE : slice.to();
        long step = slice.step() == null ? 1 : slice.step();
        List<PathResult> array = new ArrayList<>();
        LookaheadDecoder arrayDecoder = decoder.decodeArray();
        try {
            for (long index = from; arrayDecoder.hasNextArrayValue() && to < index; index += step) {
                PathResult pathResult = process(arrayDecoder, jsonPathExpressions, pathIndex + 1);
                if (pathResult != null) {
                    array.add(pathResult);
                }
            }
        } finally {
            arrayDecoder.finishStructure(true);
        }
        return new ArrayPathResult(array);
    }

    private static PathResult findArrayItem(LookaheadDecoder decoder,
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

    private interface PathResult {

        JsonNode asNode();

    }

    private record ArrayPathResult(List<PathResult> values) implements PathResult {

        @Override
        public JsonNode asNode() {
            return JsonNode.createArrayNode(
                values.stream().map(PathResult::asNode).toList()
            );
        }
    }

    private record ObjectPathResult(
        LinkedHashMap<String, PathResult> values) implements PathResult {

        @Override
        public JsonNode asNode() {
            Map<String, JsonNode> map = CollectionUtils.newLinkedHashMap(values.size());
            for (Map.Entry<String, PathResult> e : values.entrySet()) {
                if (map.put(e.getKey(), e.getValue().asNode()) != null) {
                    throw new IllegalStateException("Duplicate key");
                }
            }
            return JsonNode.createObjectNode(map);
        }
    }

    private record NodePathResult(JsonNode node) implements PathResult {

        @Override
        public JsonNode asNode() {
            return node;
        }
    }

}
