/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.function.Consumer;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.json.tree.JsonNode;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;

/**
 * Utility class for buffering and parsing JSON to support {@link io.micronaut.json.JsonMapper#createReactiveParser(java.util.function.Consumer, boolean)}.
 *
 * @since 1.0.0
 */
@Internal
@Experimental
public abstract class BufferingJsonNodeProcessor extends SpreadProcessor<byte[], JsonNode> {
    private final Consumer<Processor<byte[], JsonNode>> onSubscribe;

    private final boolean streamArray;
    /**
     * bytes left to process.
     */
    private final Queue<byte[]> buffers = new ArrayDeque<>();
    /**
     * Offset into {@link #buffers}{@code [0]} to use for parsing.
     */
    private int headOffset = 0;
    /**
     * Current state of {@link #walkJson} in the {@link #buffers}.
     */
    private long buffersState = 0;

    private boolean onlyWhitespace = true;

    public BufferingJsonNodeProcessor(Consumer<Processor<byte[], JsonNode>> onSubscribe,
                                      boolean streamArray) {
        this.onSubscribe = onSubscribe;
        this.streamArray = streamArray;
    }

    @Override
    public void subscribe(Subscriber<? super JsonNode> s) {
        onSubscribe.accept(this);
        super.subscribe(s);
    }

    @Override
    protected void spread(byte[] bytes, Collection<JsonNode> out) throws IOException {
        if (bytes.length == 0) {
            return;
        }
        buffers.add(bytes);
        for (int i = 0; i < bytes.length; ) {
            boolean ws = isJsonWhitespace(bytes[i]);
            boolean wasOutsideStructure = buffersState == 0;
            buffersState = walkJson(buffersState, bytes[i]);
            if (buffersState != 0 && wasOutsideStructure && !onlyWhitespace) {
                processOne(bytes.length - i, out);
            }
            onlyWhitespace &= ws;
            i++;
            // split on whitespace
            if (buffersState == 0 && ws && !onlyWhitespace) {
                processOne(bytes.length - i, out);
            }
        }
    }

    @Override
    protected void complete(Collection<JsonNode> out) throws IOException {
        if (!onlyWhitespace) {
            processOne(0, out);
            onlyWhitespace = true;
        }
    }

    /**
     * Process one JSON value from {@link #buffers}{@code [0][}{@link #headOffset}{@code ]} to
     * {@link #buffers}{@code [-1][-tailRemaining]}, then adjust {@link #buffers} and {@link #headOffset} to only
     * contain remaining data from the tail.
     */
    private void processOne(int tailRemaining, Collection<JsonNode> out) throws IOException {
        // count total length
        int totalLength = -headOffset - tailRemaining;
        for (byte[] buffer : buffers) {
            totalLength += buffer.length;
        }
        // copy data into a single buffer
        byte[] composite = new byte[totalLength];
        int compositeOff = 0;
        boolean head = true;
        byte[] tailBuffer = null;
        for (Iterator<byte[]> iterator = buffers.iterator(); iterator.hasNext(); ) {
            byte[] buffer = iterator.next();
            boolean tail = !iterator.hasNext();
            if (tail) {
                tailBuffer = buffer;
            }
            int bufferOff = head ? headOffset : 0;
            int bufferLen = buffer.length - bufferOff - (tail ? tailRemaining : 0);
            System.arraycopy(buffer, bufferOff, composite, compositeOff, bufferLen);
            compositeOff += bufferLen;
            head = false;
        }
        // parse
        processTopLevelNode(parseOne(composite), out);
        // restructure local buffers
        buffers.clear();
        if (tailBuffer != null && tailRemaining != 0) {
            buffers.add(tailBuffer);
            headOffset = tailBuffer.length - tailRemaining;
        } else {
            headOffset = 0;
        }
        onlyWhitespace = true;
    }

    private void processTopLevelNode(JsonNode node, Collection<JsonNode> out) {
        if (streamArray && node.isArray()) {
            for (JsonNode child : node.values()) {
                out.add(child);
            }
        } else {
            out.add(node);
        }
    }

    /**
     * Parse a single node from the given stream.
     * @param is The input stream
     * @return The node
     * @throws IOException if an error occurs
     */
    protected abstract @NonNull JsonNode parseOne(@NonNull InputStream is) throws IOException;

    private JsonNode parseOne(byte[] remaining) throws IOException {
        try (ByteArrayInputStream is = new ByteArrayInputStream(remaining)) {
            return parseOne(is);
        }
    }

    private static boolean isJsonWhitespace(byte b) {
        return b == 0x20 || b == 0x0a || b == 0x0d || b == 0x09;
    }

    /**
     * This method is a simple JSON lexer. It uses a single {@code long state}, which is {@code 0} when a JSON object
     * or array has been fully visited. If there is still data missing (i.e. if there is still an unmatched brace or
     * bracket), the state will be {@code != 0}. If the JSON is invalid, the state is undefined. Example:
     *
     * <pre>{@code
     * long state = 0;
     * for (byte b : bytes) state = walkJson(state, b);
     * }</pre>
     * <p>
     * {@code state} will be 0 if the `bytes` contain a full JSON array or object.
     * <p>
     * Note: Does not work for top-level scalar values, or for invalid JSON.
     *
     * @param state The old state
     * @param b     The new input byte to visit
     * @return The new state
     */
    static long walkJson(long state, byte b) {
        // unpack the two variables
        int dfaState = (int) state;
        int nestCount = (int) (state >> 32);

        switch (dfaState) {
            case 0:
                // outside string
                switch (b) {
                    case '"':
                        dfaState = 1;
                        break;
                    case '{':
                    case '[':
                        nestCount++;
                        break;
                    case '}':
                    case ']':
                        nestCount--;
                        break;
                    default:
                        break;
                }
                break;
            case 1:
                // inside string
                switch (b) {
                    case '"':
                        dfaState = 0;
                        break;
                    case '\\':
                        dfaState = 2;
                        break;
                    default:
                        break;
                }
                break;
            case 2:
                // inside escape sequence
                // the only escape we care about is \", so we don't need to handle longer escapes.
                dfaState = 1;
                break;
            default:
                throw new AssertionError();
        }

        // repack the two variables
        return ((long) nestCount << 32) | dfaState;
    }
}
