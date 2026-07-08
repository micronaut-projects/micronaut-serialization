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

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;
import org.jspecify.annotations.NonNull;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.YamlOutputStreamWriter;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.emitter.Emitter;
import org.snakeyaml.engine.v2.events.DocumentEndEvent;
import org.snakeyaml.engine.v2.events.DocumentStartEvent;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.ImplicitTuple;
import org.snakeyaml.engine.v2.events.MappingEndEvent;
import org.snakeyaml.engine.v2.events.MappingStartEvent;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.events.SequenceEndEvent;
import org.snakeyaml.engine.v2.events.SequenceStartEvent;
import org.snakeyaml.engine.v2.events.StreamEndEvent;
import org.snakeyaml.engine.v2.events.StreamStartEvent;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.resolver.CoreScalarResolver;
import org.snakeyaml.engine.v2.resolver.ScalarResolver;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * YAML implementation of the {@link Encoder} interface. <br/>
 * ImplicitTuple(true, true) means that (type) tags won't be shown. and sometimes we specifically DO want explicit tag we specify ImplicitTuple(false, false)
 *
 * @since 3.1.0
 */
@SuppressWarnings("NullAway")
public class YamlEncoder extends LimitingStream implements Encoder {

    private final YamlOutputStreamWriter writer;
    private final Emitter emitter;
    private final FlowStyle flowStyle;
    private final boolean explicitStart;
    private final boolean explicitEnd;
    private final boolean minimizeQuotes;
    private final boolean literalBlockStyle;
    private final YamlStringQuotingChecker quotingChecker;
    private final ScalarResolver resolver = new CoreScalarResolver(true);
    private final Deque<CollectionContext> contextStack = new ArrayDeque<>();
    private boolean streamStarted;
    private boolean documentClosed;

    /**
     * Creates a YAML encoder with the supplied stream limits.
     *
     * @param remainingLimits The remaining stream limits
     */
    public YamlEncoder(@NonNull RemainingLimits remainingLimits) {
        this(OutputStream.nullOutputStream(), remainingLimits);
    }

    /**
     * Creates a YAML encoder with the supplied output stream and stream limits.
     *
     * @param outputStream The output stream
     * @param remainingLimits The remaining stream limits
     */
    public YamlEncoder(@NonNull OutputStream outputStream, @NonNull RemainingLimits remainingLimits) {
        this(outputStream, remainingLimits, new SerdeYamlConfiguration(), new YamlStringQuotingChecker());
    }

    YamlEncoder(@NonNull OutputStream outputStream,
                @NonNull RemainingLimits remainingLimits,
                @NonNull SerdeYamlConfiguration yamlConfiguration) {
        this(outputStream, remainingLimits, yamlConfiguration, new YamlStringQuotingChecker());
    }

    YamlEncoder(@NonNull OutputStream outputStream,
                @NonNull RemainingLimits remainingLimits,
                @NonNull SerdeYamlConfiguration yamlConfiguration,
                @NonNull YamlStringQuotingChecker quotingChecker) {
        super(remainingLimits);
        SerdeYamlConfiguration configuration = Objects.requireNonNull(yamlConfiguration, "yamlConfiguration");
        flowStyle = configuration.getWriteStyle().toFlowStyle();
        explicitStart = configuration.isExplicitStart();
        explicitEnd = configuration.isExplicitEnd();
        minimizeQuotes = configuration.isMinimizeQuotes();
        literalBlockStyle = configuration.isLiteralBlockStyle();
        writer = new YamlOutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        emitter = new Emitter(createEmitterOptions(configuration), writer);
        this.quotingChecker = Objects.requireNonNull(quotingChecker, "quotingChecker");
    }

    private static DumpSettings createEmitterOptions(SerdeYamlConfiguration configuration) {
        return DumpSettings.builder()
            .setIndent(configuration.getIndent())
            .setBestLineBreak(System.lineSeparator())
            .setSplitLines(configuration.isSplitLines())
            .build();
    }

    private void ensureDocumentOpen() throws IOException {
        if (documentClosed) {
            throw new SerdeException("Multiple documents encountered, serialization rejected.");
        }
        if (!streamStarted) {
            emit(new StreamStartEvent());
            emit(new DocumentStartEvent(explicitStart, Optional.empty(), Map.of()));
            streamStarted = true;
        }
    }

    private void emit(Event event) throws IOException {
        try {
            emitter.emit(event);
        } catch (YamlEngineException e) {
            throw new SerdeException("YAML emission failed: " + e.getMessage(), e);
        }
    }

    @Override
    public @NonNull Encoder encodeArray(@NonNull Argument<?> type) throws IOException {
        ensureDocumentOpen();
        increaseDepth();
        contextStack.push(new CollectionContext(true));
        emit(new SequenceStartEvent(Optional.empty(), Optional.empty(), true, flowStyle));
        return this;
    }

    @Override
    public @NonNull Encoder encodeObject(@NonNull Argument<?> type) throws IOException {
        ensureDocumentOpen();
        increaseDepth();
        contextStack.push(new CollectionContext(false));
        emit(new MappingStartEvent(Optional.empty(), Optional.empty(), true, flowStyle));
        return this;
    }

    @Override
    public void finishStructure() throws IOException {
        if (contextStack.isEmpty()) {
            closeDocumentIfOpen();
            return;
        }
        CollectionContext context = contextStack.pop();
        emit(context.isSequence
            ? new SequenceEndEvent()
            : new MappingEndEvent());
        decreaseDepth();
        if (contextStack.isEmpty()) {
            closeDocumentIfOpen();
        } else {
            flipKeyIfInMapping();
        }
    }

    private void flipKeyIfInMapping() {
        CollectionContext context = contextStack.peek();
        if (context != null && !context.isSequence) {
            context.expectingKey = !context.expectingKey;
        }
    }

    private void closeDocumentIfOpen() throws IOException {
        if (streamStarted && !documentClosed) {
            emit(new DocumentEndEvent(explicitEnd));
            emit(new StreamEndEvent());
            documentClosed = true;
            flushWriter();
        }
    }

    @Override
    public void close() throws IOException {
        while (!contextStack.isEmpty()) {
            finishStructure();
        }
        closeDocumentIfOpen();
        flushWriter();
    }

    private void flushWriter() throws IOException {
        try {
            writer.flush();
        } catch (UncheckedIOException e) {
            throw new SerdeException("YAML writer failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void encodeKey(@NonNull String key) throws IOException {
        CollectionContext context = contextStack.peek();
        if (context == null || context.isSequence) {
            throw new SerdeException("Cannot encode a key outside of a mapping.");
        }
        if (!context.expectingKey) {
            throw new SerdeException("Encoder expected a value, got a key.");
        }
        ScalarStyle style = quotingChecker.needToQuoteName(key)
            ? ScalarStyle.DOUBLE_QUOTED
            : ScalarStyle.PLAIN;
        emit(new ScalarEvent(Optional.empty(), Optional.empty(), new ImplicitTuple(true, true), key, style));
        context.expectingKey = false;
    }

    @Override
    public void encodeString(@NonNull String value) throws IOException {
        Tag detectedTag = resolver.resolve(value, true);
        Tag defaultTag = resolver.resolve(value, false);
        ScalarStyle style;
        if ((literalBlockStyle || minimizeQuotes) && value.indexOf('\n') != -1) {  // matches Jackson’s behavior where MINIMIZE_QUOTES also enables literal block style.
            style = ScalarStyle.LITERAL;
        } else {
            if (minimizeQuotes
                && !Tag.BOOL.equals(detectedTag) // YAML boolean representations from spec
                && !quotingChecker.needToQuoteValue(value)) {   // Quoting checker
                style = ScalarStyle.PLAIN;
            } else {
                style = ScalarStyle.DOUBLE_QUOTED;
            }
        }
        emitScalar(
            value,
            new ImplicitTuple(Tag.STR.equals(detectedTag), Tag.STR.equals(defaultTag)),
            style
        );
    }

    @Override
    public void encodeBoolean(boolean value) throws IOException {
        emitScalar(Boolean.toString(value), ScalarStyle.PLAIN);
    }

    @Override
    public void encodeByte(byte value) throws IOException {
        emitScalar(Byte.toString(value), ScalarStyle.PLAIN);
    }

    @Override
    public void encodeShort(short value) throws IOException {
        emitScalar(Short.toString(value), ScalarStyle.PLAIN);
    }

    @Override
    public void encodeChar(char value) throws IOException {
        emitScalar(String.valueOf(value), ScalarStyle.SINGLE_QUOTED);
    }

    @Override
    public void encodeInt(int value) throws IOException {
        emitScalar(Integer.toString(value), ScalarStyle.PLAIN);
    }

    @Override
    public void encodeLong(long value) throws IOException {
        emitScalar(Long.toString(value), ScalarStyle.PLAIN);
    }

    @Override
    public void encodeFloat(float value) throws IOException {
        emitScalar(formatDouble(value), ScalarStyle.PLAIN);
    }

    @Override
    public void encodeDouble(double value) throws IOException {
        emitScalar(formatDouble(value), ScalarStyle.PLAIN);
    }

    @Override
    public void encodeBigInteger(@NonNull BigInteger value) throws IOException {
        emitScalar(value.toString(), ScalarStyle.PLAIN);
    }

    @Override
    public void encodeBigDecimal(@NonNull BigDecimal value) throws IOException {
        emitScalar(value.toPlainString(), ScalarStyle.PLAIN);
    }

    @Override
    public void encodeNull() throws IOException {
        emitScalar("null", ScalarStyle.PLAIN);
    }

    private void emitScalar(String value, ScalarStyle style) throws IOException {
        emitScalar(value, new ImplicitTuple(true, true), style);
    }

    private void emitScalar(String value, ImplicitTuple implicit, ScalarStyle style) throws IOException {
        ensureDocumentOpen();
        emit(new ScalarEvent(Optional.empty(), Optional.empty(), implicit, value, style));
        flipKeyIfInMapping();
    }

    private static String formatDouble(double value) {
        if (Double.isNaN(value)) {
            return ".NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? ".inf" : "-.inf";
        }
        return Double.toString(value);
    }

    private static final class CollectionContext {
        private final boolean isSequence;
        private boolean expectingKey;

        CollectionContext(boolean isSequence) {
            this.isSequence = isSequence;
            expectingKey = !isSequence;
        }
    }
}
