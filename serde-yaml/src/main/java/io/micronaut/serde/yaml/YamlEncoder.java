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
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.events.DocumentEndEvent;
import org.yaml.snakeyaml.events.DocumentStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ImplicitTuple;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;
import org.yaml.snakeyaml.events.StreamEndEvent;
import org.yaml.snakeyaml.events.StreamStartEvent;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * YAML implementation of the {@link Encoder} interface.
 *
 * @since 3.1.0
 */
@SuppressWarnings("NullAway")
public class YamlEncoder extends LimitingStream implements Encoder {

    private final Writer writer;
    private final Emitter emitter;
    private final DumperOptions.FlowStyle flowStyle;
    private final boolean explicitStart;
    private final boolean explicitEnd;
    private final Resolver resolver = new Resolver();
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
        this(outputStream, remainingLimits, new SerdeYamlConfiguration());
    }

    YamlEncoder(@NonNull OutputStream outputStream,
                @NonNull RemainingLimits remainingLimits,
                @NonNull SerdeYamlConfiguration yamlConfiguration) {
        super(remainingLimits);
        SerdeYamlConfiguration configuration = Objects.requireNonNull(yamlConfiguration, "yamlConfiguration");
        flowStyle = configuration.getWriteStyle().toFlowStyle();
        explicitStart = configuration.isExplicitStart();
        explicitEnd = configuration.isExplicitEnd();
        writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        emitter = new Emitter(writer, createEmitterOptions(configuration.getIndent()));
    }

    private static DumperOptions createEmitterOptions(int indent) {
        DumperOptions options = new DumperOptions();
        options.setIndent(indent);
        options.setLineBreak(DumperOptions.LineBreak.getPlatformLineBreak());
        return options;
    }

    private void ensureDocumentOpen() throws IOException {
        if (documentClosed) {
            throw new SerdeException("Multiple documents encountered, serialization rejected.");
        }
        if (!streamStarted) {
            emit(new StreamStartEvent(null, null));
            emit(new DocumentStartEvent(null, null, explicitStart, null, null));
            streamStarted = true;
        }
    }

    private void emit(Event event) throws IOException {
        try {
            emitter.emit(event);
        } catch (YAMLException e) {
            throw new SerdeException("YAML emission failed: " + e.getMessage(), e);
        }
    }

    @Override
    public @NonNull Encoder encodeArray(@NonNull Argument<?> type) throws IOException {
        ensureDocumentOpen();
        increaseDepth();
        contextStack.push(new CollectionContext(true));
        emit(new SequenceStartEvent(null, null, true, null, null, flowStyle));
        return this;
    }

    @Override
    public @NonNull Encoder encodeObject(@NonNull Argument<?> type) throws IOException {
        ensureDocumentOpen();
        increaseDepth();
        contextStack.push(new CollectionContext(false));
        emit(new MappingStartEvent(null, null, true, null, null, flowStyle));
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
            ? new SequenceEndEvent(null, null)
            : new MappingEndEvent(null, null));
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
            emit(new DocumentEndEvent(null, null, explicitEnd));
            emit(new StreamEndEvent(null, null));
            documentClosed = true;
            writer.flush();
        }
    }

    @Override
    public void close() throws IOException {
        while (!contextStack.isEmpty()) {
            finishStructure();
        }
        closeDocumentIfOpen();
        writer.flush();
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
        emit(new ScalarEvent(null, null, new ImplicitTuple(true, true), key, null, null, DumperOptions.ScalarStyle.PLAIN));
        context.expectingKey = false;
    }

    @Override
    public void encodeString(@NonNull String value) throws IOException {
        Tag detectedTag = resolver.resolve(NodeId.scalar, value, true);
        Tag defaultTag = resolver.resolve(NodeId.scalar, value, false);
        emitScalar(
            value,
            new ImplicitTuple(Tag.STR.equals(detectedTag), Tag.STR.equals(defaultTag)),
            DumperOptions.ScalarStyle.PLAIN
        );
    }

    @Override
    public void encodeBoolean(boolean value) throws IOException {
        emitScalar(Boolean.toString(value), DumperOptions.ScalarStyle.PLAIN);
    }

    @Override
    public void encodeByte(byte value) throws IOException {
        emitScalar(Byte.toString(value), DumperOptions.ScalarStyle.PLAIN);
    }

    @Override
    public void encodeShort(short value) throws IOException {
        emitScalar(Short.toString(value), DumperOptions.ScalarStyle.PLAIN);
    }

    @Override
    public void encodeChar(char value) throws IOException {
        emitScalar(String.valueOf(value), DumperOptions.ScalarStyle.SINGLE_QUOTED);
    }

    @Override
    public void encodeInt(int value) throws IOException {
        emitScalar(Integer.toString(value), DumperOptions.ScalarStyle.PLAIN);
    }

    @Override
    public void encodeLong(long value) throws IOException {
        emitScalar(Long.toString(value), DumperOptions.ScalarStyle.PLAIN);
    }

    @Override
    public void encodeFloat(float value) throws IOException {
        emitScalar(formatDouble(value), DumperOptions.ScalarStyle.PLAIN);
    }

    @Override
    public void encodeDouble(double value) throws IOException {
        emitScalar(formatDouble(value), DumperOptions.ScalarStyle.PLAIN);
    }

    @Override
    public void encodeBigInteger(@NonNull BigInteger value) throws IOException {
        emitScalar(value.toString(), DumperOptions.ScalarStyle.PLAIN);
    }

    @Override
    public void encodeBigDecimal(@NonNull BigDecimal value) throws IOException {
        emitScalar(value.toPlainString(), DumperOptions.ScalarStyle.PLAIN);
    }

    @Override
    public void encodeNull() throws IOException {
        emitScalar("null", DumperOptions.ScalarStyle.PLAIN);
    }

    private void emitScalar(String value, DumperOptions.ScalarStyle style) throws IOException {
        emitScalar(value, new ImplicitTuple(true, true), style);
    }

    private void emitScalar(String value, ImplicitTuple implicit, DumperOptions.ScalarStyle style) throws IOException {
        ensureDocumentOpen();
        emit(new ScalarEvent(null, null, implicit, value, null, null, style));
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
