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

import io.micronaut.serde.config.CoercionPolicy;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.AbstractStreamDecoder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.events.CollectionEndEvent;
import org.snakeyaml.engine.v2.events.CollectionStartEvent;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.resolver.ScalarResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Set;

/**
 * YAML implementation of the {@link io.micronaut.serde.Decoder} interface.
 *
 * <p>The decoder reads exactly one YAML document. The root node may be a mapping, a sequence or a
 * scalar. Anchors, aliases and merge keys are resolved while reading, quoted scalars are always
 * strings, and plain scalars are typed with the YAML 1.2 core schema. Additional documents in the
 * input are rejected.</p>
 *
 * @since 3.2.0
 */
public final class YamlDecoder extends AbstractStreamDecoder {

    private static final Set<String> LEGACY_BOOLEANS = Set.of(
        "yes", "Yes", "YES", "no", "No", "NO",
        "on", "On", "ON", "off", "Off", "OFF"
    );

    private final YAMLAnchorReplayingParser eventReader;
    private final ScalarResolver resolver;
    private final boolean booleanAsStrings;
    private final boolean emptyStringAsNull;
    private final Deque<CollectionContext> contextStack = new ArrayDeque<>();
    @Nullable
    private Event currentEvent;
    @Nullable
    private TokenType currentToken;
    @Nullable
    private Tag currentScalarTag;
    private boolean documentFinished;

    /**
     * Creates a YAML decoder with the default read settings and every coercion allowed.
     *
     * @param inputStream The YAML input stream
     * @param remainingLimits The remaining stream limits
     * @throws IOException if the input cannot be read or holds no YAML document
     */
    public YamlDecoder(@NonNull InputStream inputStream, @NonNull RemainingLimits remainingLimits) throws IOException {
        this(inputStream, remainingLimits, CoercionPolicy.LENIENT, YamlReadSettings.DEFAULT);
    }

    /**
     * Creates a YAML decoder.
     *
     * @param inputStream The YAML input stream
     * @param remainingLimits The remaining stream limits
     * @param coercionPolicy The coercions this decoder may perform
     * @param readSettings The read settings
     * @throws IOException if the input cannot be read or holds no YAML document
     */
    YamlDecoder(@NonNull InputStream inputStream,
                @NonNull RemainingLimits remainingLimits,
                @NonNull CoercionPolicy coercionPolicy,
                @NonNull YamlReadSettings readSettings) throws IOException {
        super(remainingLimits, coercionPolicy);
        this.booleanAsStrings = readSettings.booleanAsStrings();
        this.emptyStringAsNull = readSettings.emptyStringAsNull();
        this.resolver = readSettings.loadSettings().getSchema().getScalarResolver();
        Iterator<Event> events;
        try {
            events = new Parse(readSettings.loadSettings())
                .parseReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .iterator();
        } catch (YamlEngineException e) {
            throw new SerdeException("Invalid YAML input: " + e.getMessage(), e);
        }
        this.eventReader = new YAMLAnchorReplayingParser(events);
        Event first = eventReader.getEvent();
        while (first != null && (first.getEventId() == Event.ID.StreamStart || first.getEventId() == Event.ID.DocumentStart)) {
            first = eventReader.getEvent();
        }
        if (first == null || first.getEventId() == Event.ID.StreamEnd) {
            throw new SerdeException("No YAML document found in the input");
        }
        setCurrent(first);
    }

    @Override
    protected @Nullable TokenType currentToken() {
        return currentToken;
    }

    @Override
    protected void nextToken() throws IOException {
        if (documentFinished) {
            return;
        }
        Event event = eventReader.getEvent();
        if (event == null) {
            finishDocument();
            return;
        }
        switch (event.getEventId()) {
            case DocumentEnd, StreamEnd -> {
                rejectFurtherDocuments();
                finishDocument();
            }
            case DocumentStart, StreamStart -> throw multipleDocuments();
            default -> setCurrent(event);
        }
    }

    private void finishDocument() {
        documentFinished = true;
        currentEvent = null;
        currentToken = null;
        currentScalarTag = null;
    }

    private void rejectFurtherDocuments() throws IOException {
        Event event;
        while ((event = eventReader.getEvent()) != null) {
            if (event.getEventId() != Event.ID.DocumentEnd && event.getEventId() != Event.ID.StreamEnd) {
                throw multipleDocuments();
            }
        }
    }

    private SerdeException multipleDocuments() {
        return new SerdeException("Multiple YAML documents were found in the input but only one document is supported");
    }

    private void setCurrent(Event event) throws IOException {
        currentEvent = event;
        currentScalarTag = null;
        switch (event.getEventId()) {
            case MappingStart -> {
                requireValuePosition(event);
                contextStack.push(new CollectionContext(true));
                currentToken = TokenType.START_OBJECT;
            }
            case SequenceStart -> {
                requireValuePosition(event);
                contextStack.push(new CollectionContext(false));
                currentToken = TokenType.START_ARRAY;
            }
            case MappingEnd, SequenceEnd -> {
                CollectionContext closed = contextStack.pollFirst();
                if (closed == null) {
                    throw new SerdeException("Unexpected end of a YAML collection" + location(event));
                }
                valueConsumed();
                currentToken = closed.mapping ? TokenType.END_OBJECT : TokenType.END_ARRAY;
            }
            case Scalar -> {
                ScalarEvent scalar = (ScalarEvent) event;
                CollectionContext context = contextStack.peekFirst();
                if (context != null && context.mapping && context.expectingKey) {
                    context.expectingKey = false;
                    currentToken = TokenType.KEY;
                } else {
                    currentToken = resolveScalarType(scalar);
                    valueConsumed();
                }
            }
            case Alias -> throw new SerdeException("Unresolved YAML alias" + location(event));
            default -> throw new SerdeException("Unexpected YAML event " + event.getEventId() + location(event));
        }
    }

    private void requireValuePosition(Event event) throws SerdeException {
        CollectionContext context = contextStack.peekFirst();
        if (context != null && context.mapping && context.expectingKey) {
            throw new SerdeException("Complex YAML mapping keys are not supported, only scalar keys can be decoded" + location(event));
        }
    }

    private void valueConsumed() {
        CollectionContext context = contextStack.peekFirst();
        if (context != null && context.mapping) {
            context.expectingKey = true;
        }
    }

    private TokenType resolveScalarType(ScalarEvent event) {
        String value = event.getValue();
        Tag tag;
        if (event.getTag().isPresent()) {
            tag = new Tag(event.getTag().get());
        } else if (event.getScalarStyle() != ScalarStyle.PLAIN) {
            tag = Tag.STR;
        } else if (value.isEmpty()) {
            tag = emptyStringAsNull ? Tag.NULL : Tag.STR;
        } else if (!booleanAsStrings && LEGACY_BOOLEANS.contains(value)) {
            tag = Tag.BOOL;
        } else {
            tag = resolver.resolve(value, true);
        }
        currentScalarTag = tag;
        if (Tag.INT.equals(tag) || Tag.FLOAT.equals(tag)) {
            return TokenType.NUMBER;
        } else if (Tag.BOOL.equals(tag)) {
            return TokenType.BOOLEAN;
        } else if (Tag.NULL.equals(tag)) {
            return TokenType.NULL;
        } else {
            return TokenType.STRING;
        }
    }

    @Override
    protected String getCurrentKey() throws IOException {
        return scalarValue();
    }

    @Override
    protected String coerceScalarToString(TokenType currentToken) throws IOException {
        return scalarValue();
    }

    @Override
    protected String getString() throws IOException {
        return scalarValue();
    }

    private String scalarValue() throws IOException {
        if (currentEvent instanceof ScalarEvent scalar) {
            return scalar.getValue();
        }
        throw createDeserializationException("Current token is not a scalar", null);
    }

    @Override
    protected boolean getBoolean() throws IOException {
        String value = scalarValue();
        return "true".equalsIgnoreCase(value)
            || "yes".equalsIgnoreCase(value)
            || "on".equalsIgnoreCase(value);
    }

    @Override
    protected boolean isCurrentNumberFloat() {
        return Tag.FLOAT.equals(currentScalarTag);
    }

    @Override
    protected long getLong() throws IOException {
        String value = scalarValue();
        try {
            Number number = YamlNumbers.parse(value);
            if (number instanceof BigInteger bigInteger) {
                if (bigInteger.bitLength() > 63) {
                    throw createDeserializationException("Numeric value out of range of long", value);
                }
                return bigInteger.longValue();
            }
            return number.longValue();
        } catch (NumberFormatException e) {
            throw invalidNumber(value, e);
        }
    }

    @Override
    protected double getDouble() throws IOException {
        String value = scalarValue();
        try {
            return YamlNumbers.parse(value).doubleValue();
        } catch (NumberFormatException e) {
            throw invalidNumber(value, e);
        }
    }

    @Override
    protected BigInteger getBigInteger() throws IOException {
        String value = scalarValue();
        try {
            return YamlNumbers.parseBigDecimal(value).toBigInteger();
        } catch (NumberFormatException e) {
            throw invalidNumber(value, e);
        }
    }

    @Override
    protected BigDecimal getBigDecimal() throws IOException {
        String value = scalarValue();
        try {
            return YamlNumbers.parseBigDecimal(value);
        } catch (NumberFormatException e) {
            throw invalidNumber(value, e);
        }
    }

    @Override
    protected Number getBestNumber() throws IOException {
        String value = scalarValue();
        try {
            return YamlNumbers.parse(value);
        } catch (NumberFormatException e) {
            throw invalidNumber(value, e);
        }
    }

    private IOException invalidNumber(String value, NumberFormatException cause) {
        return new InvalidFormatException("Unable to parse YAML scalar as a number" + currentLocation(), cause, value);
    }

    @Override
    protected void skipChildren() throws IOException {
        if (currentToken != TokenType.START_OBJECT && currentToken != TokenType.START_ARRAY) {
            return;
        }
        int depth = 1;
        while (true) {
            Event event = eventReader.getEvent();
            if (event == null) {
                throw new SerdeException("Unexpected end of YAML input inside a collection");
            }
            if (event instanceof CollectionStartEvent) {
                depth++;
            } else if (event instanceof CollectionEndEvent && --depth == 0) {
                // the matching end event becomes the current token, skipValue() then moves past it
                setCurrent(event);
                return;
            }
        }
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        super.finishStructure(consumeLeftElements);
        nextToken();
    }

    @Override
    public @NonNull IOException createDeserializationException(@NonNull String message, @Nullable Object invalidValue) {
        if (invalidValue != null) {
            return new InvalidFormatException(message + currentLocation(), null, invalidValue);
        }
        return new SerdeException(message + currentLocation());
    }

    private String currentLocation() {
        return currentEvent == null ? "" : location(currentEvent);
    }

    private static String location(Event event) {
        return event.getStartMark()
            .map(mark -> " at line " + (mark.getLine() + 1) + ", column " + (mark.getColumn() + 1))
            .orElse("");
    }

    private static final class CollectionContext {
        private final boolean mapping;
        private boolean expectingKey;

        CollectionContext(boolean mapping) {
            this.mapping = mapping;
            this.expectingKey = mapping;
        }
    }
}
