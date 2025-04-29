package io.micronaut.serde.jmespath;

import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.LookaheadDecoder;
import io.micronaut.serde.jmespath.model.ArrayItemAtExpression;
import io.micronaut.serde.jmespath.model.KeySelectionExpression;
import io.micronaut.serde.jmespath.model.PathExpression;
import io.micronaut.serde.support.deserializers.buffer.BufferedDecoder;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class SerdeJmesPathDecoder {

    public static JsonNode decode(Decoder decoder, String input) throws IOException {
        List<PathExpression> pathExpressions = SerdeJmesPathParser.parse(input);
        return process((LookaheadDecoder) decoder, pathExpressions.iterator());
    }

    private static JsonNode process(LookaheadDecoder decoder, Iterator<PathExpression> pathExpressionsIterator) throws IOException {
        if (pathExpressionsIterator.hasNext()) {
            PathExpression pathExpression = pathExpressionsIterator.next();
            if (pathExpression instanceof KeySelectionExpression keySelectionExpression) {
                if (decoder.lookahead() == LookaheadDecoder.TokenType.START_OBJECT) {
                    LookaheadDecoder objectDecoder = decoder.decodeObject();
                    try {
                        for (String key = objectDecoder.decodeKey(); key != null; key = objectDecoder.decodeKey()) {
                            if (key.equals(keySelectionExpression.propertyName())) {
                                return process(objectDecoder, pathExpressionsIterator);
                            }
                        }
                    } finally {
                        objectDecoder.finishStructure(true);
                    }
                }
                return null;
            }
            if (pathExpression instanceof ArrayItemAtExpression arrayItemAtExpression) {
                if (decoder.lookahead() == LookaheadDecoder.TokenType.START_ARRAY) {
                    int requiredIndex = arrayItemAtExpression.index();
                    if (requiredIndex < 0) {
                        try (Decoder bufferedDecoder = BufferedDecoder.of(decoder)) {
                            int count = countItems(bufferedDecoder);
                            int newIndex = count + requiredIndex;
                            return findArrayItem(bufferedDecoder, newIndex, pathExpressionsIterator);
                        }
                    }
                    return findArrayItem(decoder, requiredIndex, pathExpressionsIterator);
                }
                return null;
            }
            throw new IllegalArgumentException("Unsupported path expression: " + pathExpression);
        }

        return decoder.decodeNode();
    }

    private static JsonNode findArrayItem(Decoder decoder, int requiredIndex, Iterator<PathExpression> pathExpressionsIterator) throws IOException {
        Decoder arrayDecoder = decoder.decodeArray();
        try {
            int index = 0;
            while (arrayDecoder.hasNextArrayValue()) {
                if (index++ == requiredIndex) {
                    return process((LookaheadDecoder) arrayDecoder, pathExpressionsIterator);
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
