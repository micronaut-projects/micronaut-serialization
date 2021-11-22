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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.json.tree.JsonArray;
import io.micronaut.json.tree.JsonNode;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Utility class for buffering and parsing JSON to support {@link io.micronaut.json.JsonMapper#createReactiveParser(java.util.function.Consumer, boolean)}.
 *
 * @since 1.0.0
 */
@Internal
@Experimental
public abstract class SimpleBufferingJsonNodeProcessor implements Processor<byte[], JsonNode> {
    private final Consumer<Processor<byte[], JsonNode>> onSubscribe;
    private final boolean streamArray;
    private Subscriber<? super JsonNode> jsonSubscriber;
    private Subscription subscription;
    private byte[] buffer;
    private boolean upstreamComplete;
    private boolean downstreamComplete;
    private int downstreamDemand;
    private final List<JsonNode> nodeBuffer;

    public SimpleBufferingJsonNodeProcessor(Consumer<Processor<byte[], JsonNode>> onSubscribe,
                                            boolean streamArray) {
        this.onSubscribe = onSubscribe;
        this.streamArray = streamArray;
        upstreamComplete = false;
        downstreamComplete = false;
        nodeBuffer = new ArrayList<>();
    }

    @Override
    public void subscribe(Subscriber<? super JsonNode> s) {
        onSubscribe.accept(this);
        this.jsonSubscriber = s;
        this.jsonSubscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                downstreamDemand += n;
                if (subscription != null) {
                    subscription.request(n);
                }
            }

            @Override
            public void cancel() {
                // cancel upstream
                if (!upstreamComplete && subscription != null) {
                    subscription.cancel();
                }
                downstreamComplete = true;
            }
        });
    }

    @Override
    public void onSubscribe(Subscription s) {
        subscription = s;
        subscription.request(1);
    }

    @Override
    public void onNext(byte[] bytes) {
        if (!upstreamComplete) {
            if (streamArray) {
                if (buffer == null) {
                    buffer = bytes;
                } else {
                    if (bytes.length != 0) {
                        buffer = ArrayUtils.concat(buffer, bytes);
                    }
                }
            } else {
                if (bytes.length > 0) {
                    if (buffer == null) {
                        buffer = bytes;
                    } else {
                        buffer = ArrayUtils.concat(buffer, bytes);
                    }
                    final byte s = buffer[0];
                    final byte f = s == (byte) '{' ? (byte) '}' : (byte) ']';
                    if (countBytes(s, f, buffer)) {
                        try {
                            JsonNode node = parseOne(buffer);
                            nodeBuffer.add(node);
                        } catch (Exception e) {
                            subscription.cancel();
                            upstreamComplete = true;
                            if (jsonSubscriber != null) {
                                jsonSubscriber.onError(e);
                                downstreamComplete = true;
                            }
                        }
                        buffer = null;
                        flushBuffer();
                    }
                }
            }
            subscription.request(1);
        }
    }

    private boolean countBytes(byte start, byte end, byte[] data) {
        int[] counts = new int[2];
        for (int i = 0; i < data.length; i++) {
            byte b = data[i];
            if (b == start) {
                counts[0]++;
            } else if (b == end) {
                counts[1]++;
            }
        }
        return counts[0] == counts[1];
    }

    @Override
    public void onError(Throwable t) {
        if (!downstreamComplete && jsonSubscriber != null) {
            downstreamComplete = true;
            jsonSubscriber.onError(t);
        }
        upstreamComplete = true;
    }

    @Override
    public void onComplete() {
        if (!upstreamComplete && !downstreamComplete) {
            upstreamComplete = true;
            initBuffer();
            flushBuffer();
        }
    }

    /**
     * Parse a single node from the given stream.
     * @param is The input stream
     * @return The node
     * @throws IOException if an error occurs
     */
    protected abstract @NonNull JsonNode parseOne(@NonNull InputStream is) throws IOException;

    private void initBuffer() {
        final byte[] buffer = this.buffer;
        if (buffer != null) {
            if (streamArray) {
                try (ByteArrayInputStream is = new ByteArrayInputStream(buffer)) {
                    final JsonNode jsonNode = parseOne(is);
                    if (jsonNode instanceof JsonArray) {
                        JsonArray array = (JsonArray) jsonNode;
                        for (JsonNode value : array.values()) {
                            nodeBuffer.add(value);
                        }
                    } else {
                        nodeBuffer.add(jsonNode);
                    }
                } catch (Exception e) {
                    jsonSubscriber.onError(e);
                    downstreamComplete = true;
                } finally {
                    this.buffer = null;
                }
            }
        }
    }

    private JsonNode parseOne(byte[] remaining) throws IOException {
        try (ByteArrayInputStream is = new ByteArrayInputStream(remaining)) {
            return parseOne(is);
        }
    }

    private void flushBuffer() {
        if (!downstreamComplete) {
            if (downstreamDemand > 0) {
                final Iterator<JsonNode> i = nodeBuffer.iterator();
                while (downstreamDemand-- != 0 && i.hasNext()) {
                    final JsonNode node = i.next();
                    i.remove();
                    jsonSubscriber.onNext(node);
                }
            }

            if (nodeBuffer.isEmpty() && upstreamComplete) {
                downstreamComplete = true;
                jsonSubscriber.onComplete();
            }
        }
    }
}
