package io.micronaut.serde.jmespath;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.tree.JsonArray;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.LookaheadDecoder;
import io.micronaut.serde.jmespath.model.ArrayAllExpression;
import io.micronaut.serde.jmespath.model.ArrayFlattenExpression;
import io.micronaut.serde.jmespath.model.ArrayItemAtExpression;
import io.micronaut.serde.jmespath.model.KeySelectionExpression;
import io.micronaut.serde.jmespath.model.PathExpression;
import io.micronaut.serde.support.deserializers.buffer.BufferedDecoder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

public class SerdeJmesPathDecoder {

    public static JsonNode decode(Decoder decoder, String input) {
        List<PathExpression> pathExpressions = SerdeJmesPathParser.parse(input);
        PathResult pathResult = process((LookaheadDecoder) decoder, pathExpressions, 0);
        return convertNode(pathResult);
    }

    private static JsonNode convertNode(PathResult pathResult) {
        if (pathResult instanceof Multiple multiple) {
            return multiple.node;
        }
        if (pathResult instanceof Array array) {
            return JsonArray.createArrayNode(array.values().stream().map(SerdeJmesPathDecoder::convertNode).toList());
        }
        return null;
    }

    @Nullable
    private static SerdeJmesPathDecoder.PathResult process(LookaheadDecoder decoder, List<PathExpression> pathExpressions, int pathIndex) {
        try {
            if (pathExpressions.size() > pathIndex) {
                PathExpression pathExpression = pathExpressions.get(pathIndex);
                if (pathExpression instanceof KeySelectionExpression keySelectionExpression) {
                    if (decoder.lookahead() == LookaheadDecoder.TokenType.START_OBJECT) {
                        LookaheadDecoder objectDecoder = decoder.decodeObject();
                        try {
                            for (String key = objectDecoder.decodeKey(); key != null; key = objectDecoder.decodeKey()) {
                                if (key.equals(keySelectionExpression.propertyName())) {
                                    return process(objectDecoder, pathExpressions, pathIndex + 1);
                                } else {
                                    objectDecoder.skipValue();
                                }
                            }
                        } finally {
                            objectDecoder.finishStructure(true);
                        }
                        return null;
                    }
                    decoder.skipValue();
                    return null;
                }
                if (pathExpression instanceof ArrayItemAtExpression arrayItemAtExpression) {
                    if (decoder.lookahead() == LookaheadDecoder.TokenType.START_ARRAY) {
                        int requiredIndex = arrayItemAtExpression.index();
                        if (requiredIndex < 0) {
                            try (LookaheadDecoder bufferedDecoder = BufferedDecoder.of(decoder)) {
                                int count = countItems(bufferedDecoder);
                                int newIndex = count + requiredIndex;
                                return findArrayItem(bufferedDecoder, newIndex, pathExpressions, pathIndex + 1);
                            }
                        }
                        return findArrayItem(decoder, requiredIndex, pathExpressions, pathIndex + 1);
                    }
                    decoder.skipValue();
                    return null;
                }
                if (pathExpression instanceof ArrayFlattenExpression) {
                    if (decoder.lookahead() == LookaheadDecoder.TokenType.START_ARRAY) {
                        List<PathResult> array = new ArrayList<>();
                        LookaheadDecoder arrayDecoder = decoder.decodeArray();
                        try {
                            while (arrayDecoder.hasNextArrayValue()) {
                                boolean isArray = arrayDecoder.lookahead() == LookaheadDecoder.TokenType.START_ARRAY;
                                if (isArray) {
                                    try (LookaheadDecoder flattenedArray = arrayDecoder.decodeArray()) {
                                        while (flattenedArray.hasNextArrayValue()) {
                                            PathResult pathResult = process(flattenedArray, pathExpressions, pathIndex + 1);
                                            if (pathResult != null) {
                                                array.add(pathResult);
                                            }
                                        }
                                    }
                                } else {
                                    PathResult pathResult = process(arrayDecoder, pathExpressions, pathIndex + 1);
                                    if (pathResult != null) {
                                        if (pathResult instanceof Array ar) {
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
                        return new Array(array);
                    }
                    decoder.skipValue();
                    return null;
                }
                if (pathExpression instanceof ArrayAllExpression) {
                    if (decoder.lookahead() == LookaheadDecoder.TokenType.START_ARRAY) {
                        List<PathResult> array = new ArrayList<>();
                        LookaheadDecoder arrayDecoder = decoder.decodeArray();
                        try {
                            while (arrayDecoder.hasNextArrayValue()) {
                                PathResult pathResult = process(arrayDecoder, pathExpressions, pathIndex + 1);
                                if (pathResult != null) {
                                    array.add(pathResult);
                                }
                            }
                        } finally {
                            arrayDecoder.finishStructure(true);
                        }
                        return new Array(array);
                    }
                    decoder.skipValue();
                    return null;
                }
                throw new IllegalArgumentException("Unsupported path expression: " + pathExpression);
            }

            return new Multiple(decoder.decodeNode());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    interface PathResult {
    }

    record Array(List<PathResult> values) implements PathResult {
    }

    record Multiple(JsonNode node) implements PathResult {
    }

    private static PathResult findArrayItem(LookaheadDecoder decoder,
                                            int requiredIndex,
                                            List<PathExpression> pathExpressions,
                                            int pathIndex) throws IOException {
        LookaheadDecoder arrayDecoder = decoder.decodeArray();
        try {
            int index = 0;
            while (arrayDecoder.hasNextArrayValue()) {
                if (index++ == requiredIndex) {
                    return process(arrayDecoder, pathExpressions, pathIndex);
                }
                arrayDecoder.skipValue();
            }
        } finally {
            arrayDecoder.finishStructure(true);
        }
        return null;
    }

    private static int countItems(Decoder primed) throws IOException {
        LookaheadDecoder arrayDecoder = (LookaheadDecoder) primed.decodeArray();
        int count = 0;
        while (arrayDecoder.hasNextArrayValue()) {
            arrayDecoder.skipValue();
            count++;
        }
        arrayDecoder.finishStructure(true);
        return count;
    }

}
