/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.serde.yaml;

import io.micronaut.serde.support.AbstractStreamDecoder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.reader.UnicodeReader;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * YAML implementation of the {@link io.micronaut.serde.Decoder} interface.
 *
 * @since 3.1.0
 */
@SuppressWarnings("NullAway")
public class YamlDecoder extends AbstractStreamDecoder {

    private final YAMLAnchorReplayingParser eventReader;
    private final Deque<CollectionContext> mappingContextStack = new ArrayDeque<>();
    private Event currrentEvent;
    private boolean inDocument = false;
    private final Resolver resolver = new Resolver();
    private final boolean booleanAsStrings;
    private final boolean emptyStringAsNull;

    /**
     * Creates a YAML decoder with the supplied input stream and stream limits.
     *
     * @param inputStream The YAML input stream
     * @param remainingLimits The remaining stream limits
     */
    public YamlDecoder(@NonNull InputStream inputStream, @NonNull RemainingLimits remainingLimits) {
        this(inputStream, remainingLimits, new SerdeYamlConfiguration());
    }

    YamlDecoder(@NonNull InputStream inputStream,
                @NonNull RemainingLimits remainingLimits,
                @NonNull SerdeYamlConfiguration yamlConfiguration) {
        super(remainingLimits);
        booleanAsStrings = yamlConfiguration.isBooleanAsStrings();
        emptyStringAsNull = yamlConfiguration.isEmptyStringAsNull();
        Iterator<Event> events = new Yaml().parse(new UnicodeReader(inputStream)).iterator();
        while (events.hasNext()) {
            Event nextEvent = events.next();
            if (nextEvent.getEventId() == Event.ID.DocumentStart) {
                inDocument = true;
            }
            if (nextEvent.getEventId() == Event.ID.MappingStart) {
                eventReader = new YAMLAnchorReplayingParser(events);
                mappingContextStack.push(new CollectionContext(false));
                this.currrentEvent = nextEvent;
                return;
            }
        }
        eventReader = new YAMLAnchorReplayingParser(events);
    }

    @Override
    protected TokenType currentToken() {
        return switch (currrentEvent.getEventId()) {
            case MappingStart -> TokenType.START_OBJECT;
            case MappingEnd -> TokenType.END_OBJECT;
            case SequenceEnd -> TokenType.END_ARRAY;
            case SequenceStart -> TokenType.START_ARRAY;
            case Scalar -> {
                assert !mappingContextStack.isEmpty() : "Empty sequence/mapping context, no sequence/mapping found.";
                CollectionContext ctx = mappingContextStack.peekFirst();
                if (!ctx.isSequence() && ctx.isExpectingKey()) {
                    yield TokenType.KEY;
                }
                yield resolveScalarType((ScalarEvent) currrentEvent);
            }
            case Alias, StreamEnd, StreamStart, Comment, DocumentEnd, DocumentStart -> null;
        };
    }

    @Override
    protected void nextToken() throws IOException {
        Event nextEvent = eventReader.getEvent();
        if (nextEvent == null) {
            this.currrentEvent = null;
            return;
        }
        if (inDocument) {
            Event.ID eventId = nextEvent.getEventId();

            if (eventId == Event.ID.Comment) {
                nextToken();
                return;
            }
            if (eventId == Event.ID.StreamStart || eventId == Event.ID.DocumentStart) {
                throw createDeserializationException("Multiple documents encounter, deserialization failed.", null);
            }
            if (eventId == Event.ID.MappingEnd || eventId == Event.ID.SequenceEnd) {
                mappingContextStack.removeFirst();
                CollectionContext ctx = mappingContextStack.peekFirst();
                if (ctx != null && !ctx.isSequence() && ctx.isExpectingKey()) {
                    ctx.setExpectingKey(false);
                }
            }
            if (eventId == Event.ID.MappingStart) {
                mappingContextStack.push(new CollectionContext(false));
            }
            if (eventId == Event.ID.SequenceStart) {
                mappingContextStack.push(new CollectionContext(true));
            }
            if (eventId == Event.ID.StreamEnd || eventId == Event.ID.DocumentEnd) {
                if (eventId == Event.ID.DocumentEnd) {
                    failIfAnotherDocumentExists();
                }
                inDocument = false;
                finishStructure();
            }
            if (eventId == Event.ID.Scalar) {
                assert !mappingContextStack.isEmpty() : "Mapping context can't be empty while decoding a scalar.";
                CollectionContext ctx = mappingContextStack.peekFirst();
                if (!ctx.isSequence()) {
                    mappingContextStack.peekFirst().setExpectingKey(!mappingContextStack.peekFirst().isExpectingKey());
                }
            }
            this.currrentEvent = nextEvent;
        }

    }

    private void failIfAnotherDocumentExists() throws IOException {
        Event nextEvent;
        while ((nextEvent = eventReader.getEvent()) != null) {
            if (nextEvent.getEventId() == Event.ID.StreamEnd || nextEvent.getEventId() == Event.ID.Comment) {
                continue;
            }
            throw createDeserializationException("Multiple documents encounter, deserialization failed.", null);
        }
    }

    @Override
    protected String getCurrentKey() throws IOException {
        if (currrentEvent instanceof ScalarEvent scalarEvent) {
            return scalarEvent.getValue();
        }
        throw createDeserializationException("Current token is not a field name.", null);
    }

    @Override
    protected String coerceScalarToString(TokenType currentToken) throws IOException {
        if (currrentEvent instanceof ScalarEvent scalarEvent) {
            return scalarEvent.getValue();
        }
        throw createDeserializationException("Current token is not a scalar.", null);
    }

    @Override
    protected String getString() throws IOException {
        if (currrentEvent instanceof ScalarEvent scalarEvent) {
            return scalarEvent.getValue();
        }
        throw createDeserializationException("Current token is not a scalar.", null);
    }

    @Override
    protected boolean getBoolean() throws IOException {
        String value = getString();
        return "true".equalsIgnoreCase(value)
            || "yes".equalsIgnoreCase(value)
            || "y".equalsIgnoreCase(value)
            || "on".equalsIgnoreCase(value);
    }

    @Override
    protected long getLong() throws IOException {
        return Long.parseLong(getString());
    }

    @Override
    protected double getDouble() throws IOException {
        return Double.parseDouble(getString());
    }

    @Override
    protected BigInteger getBigInteger() throws IOException {
        return BigInteger.valueOf(getLong());
    }

    @Override
    protected BigDecimal getBigDecimal() throws IOException {
        return BigDecimal.valueOf(getDouble());
    }

    @Override
    protected Number getBestNumber() throws IOException {
        return Float.valueOf(getString());
    }

    @Override
    protected void skipChildren() throws IOException {
    }

    @Override
    public @NonNull IOException createDeserializationException(@NonNull String message, @Nullable Object invalidValue) {
        return new IOException(message);
    }

    private TokenType resolveScalarType(ScalarEvent event) {
        String value = event.getValue();
        DumperOptions.ScalarStyle scalarStyle = event.getScalarStyle();
        Tag tag;

        if (!emptyStringAsNull && value.isEmpty()) {
            tag = Tag.STR;
        } else if (event.getTag() != null) {
            tag = new Tag(event.getTag());
        } else if (scalarStyle != DumperOptions.ScalarStyle.PLAIN) {
            tag = Tag.STR;
        } else {
            tag = resolver.resolve(NodeId.scalar, value, event.getImplicit().canOmitTagInPlainScalar());
        }

        if (tag == Tag.FLOAT || tag == Tag.INT) {
            return TokenType.NUMBER;
        // yes —> false and TRUE —> true
        } else if (tag == Tag.BOOL && booleanAsStrings && !isCanonicalBoolean(value)) {
            return TokenType.STRING;
        } else if (tag == Tag.BOOL) {
            return TokenType.BOOLEAN;
        } else if (tag == Tag.NULL) {
            return TokenType.NULL;
        } else {
            return TokenType.STRING;
        }
    }

    private boolean isCanonicalBoolean(String value) {
        // yes —> false and TRUE —> true
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        super.finishStructure(consumeLeftElements);
        nextToken();
    }

    static final class CollectionContext {
        private boolean expectingKey = false;
        private final boolean sequence;

        CollectionContext(boolean sequence) {
            this.sequence = sequence;
        }

        public boolean isExpectingKey() {
            return expectingKey;
        }

        public void setExpectingKey(boolean expectingKey) {
            this.expectingKey = expectingKey;
        }

        public boolean isSequence() {
            return sequence;
        }
    }

}
