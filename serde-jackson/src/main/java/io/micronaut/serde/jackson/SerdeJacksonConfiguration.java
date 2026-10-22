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
package io.micronaut.serde.jackson;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.config.SerdeConfiguration;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.core.util.BufferRecycler;
import tools.jackson.core.util.JsonRecyclerPools;
import tools.jackson.core.util.RecyclerPool;

import java.util.Collections;
import java.util.Map;

/**
 * Configuration for the Jackson.
 *
 * @author Denis Stepanov
 * @since 2.3
 */
@BootstrapContextCompatible
@Internal
@ConfigurationProperties(SerdeJacksonConfiguration.PREFIX)
public final class SerdeJacksonConfiguration {

    static final String PREFIX = SerdeConfiguration.PREFIX + ".jackson";

    // TODO: document breaking changes
    private Map<JsonReadFeature, Boolean> jsonReadFeatures = Collections.emptyMap();
    private Map<JsonWriteFeature, Boolean> jsonWriteFeatures = Collections.emptyMap();
    private Map<TokenStreamFactory.Feature, Boolean> jsonFactoryFeatures = Collections.emptyMap();
    private Map<StreamReadFeature, Boolean> streamReadFeatures = Collections.emptyMap();
    private Map<StreamWriteFeature, Boolean> streamWriteFeatures = Collections.emptyMap();
    private boolean prettyPrint;
    private RecyclerPoolType recyclerPool = RecyclerPoolType.DEFAULT;
    private int recyclerPoolSize = RecyclerPool.BoundedPoolBase.DEFAULT_CAPACITY;

    public Map<JsonReadFeature, Boolean> getJsonReadFeatures() {
        return jsonReadFeatures;
    }

    public void setJsonReadFeatures(Map<JsonReadFeature, Boolean> jsonReadFeatures) {
        this.jsonReadFeatures = jsonReadFeatures;
    }

    public Map<JsonWriteFeature, Boolean> getJsonWriteFeatures() {
        return jsonWriteFeatures;
    }

    public void setJsonWriteFeatures(Map<JsonWriteFeature, Boolean> jsonWriteFeatures) {
        this.jsonWriteFeatures = jsonWriteFeatures;
    }

    public Map<TokenStreamFactory.Feature, Boolean> getJsonFactoryFeatures() {
        return jsonFactoryFeatures;
    }

    public void setJsonFactoryFeatures(Map<TokenStreamFactory.Feature, Boolean> jsonFactoryFeatures) {
        this.jsonFactoryFeatures = jsonFactoryFeatures;
    }

    public Map<StreamWriteFeature, Boolean> getStreamWriteFeatures() {
        return streamWriteFeatures;
    }

    public void setStreamWriteFeatures(Map<StreamWriteFeature, Boolean> streamWriteFeatures) {
        this.streamWriteFeatures = streamWriteFeatures;
    }

    public Map<StreamReadFeature, Boolean> getStreamReadFeatures() {
        return streamReadFeatures;
    }

    public void setStreamReadFeatures(Map<StreamReadFeature, Boolean> streamReadFeatures) {
        this.streamReadFeatures = streamReadFeatures;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /**
     * @return The pool of the buffers that parsers and generators reuse
     * @since 3.3.0
     */
    public RecyclerPoolType getRecyclerPool() {
        return recyclerPool;
    }

    /**
     * The pool of the buffers that parsers and generators reuse. Defaults to {@link RecyclerPoolType#DEFAULT}.
     *
     * @param recyclerPool The recycler pool
     * @since 3.3.0
     */
    public void setRecyclerPool(RecyclerPoolType recyclerPool) {
        this.recyclerPool = recyclerPool;
    }

    /**
     * @return The capacity of a {@link RecyclerPoolType#BOUNDED} pool
     * @since 3.3.0
     */
    public int getRecyclerPoolSize() {
        return recyclerPoolSize;
    }

    /**
     * The capacity of a {@link RecyclerPoolType#BOUNDED} pool. Defaults to {@value RecyclerPool.BoundedPoolBase#DEFAULT_CAPACITY}.
     *
     * @param recyclerPoolSize The pool capacity
     * @since 3.3.0
     */
    public void setRecyclerPoolSize(int recyclerPoolSize) {
        this.recyclerPoolSize = recyclerPoolSize;
    }

    RecyclerPool<BufferRecycler> createRecyclerPool() {
        return switch (recyclerPool) {
            case DEFAULT -> new VirtualThreadAwareRecyclerPool();
            case THREAD_LOCAL -> JsonRecyclerPools.threadLocalPool();
            case CONCURRENT_DEQUE -> JsonRecyclerPools.newConcurrentDequePool();
            case SHARED_CONCURRENT_DEQUE -> JsonRecyclerPools.sharedConcurrentDequePool();
            case BOUNDED -> JsonRecyclerPools.newBoundedPool(recyclerPoolSize);
            case SHARED_BOUNDED -> JsonRecyclerPools.sharedBoundedPool();
            case NONE -> JsonRecyclerPools.nonRecyclingPool();
        };
    }

    /**
     * The pools of the buffers that Jackson parsers and generators reuse. Pools that are not shared belong to one JSON
     * mapper.
     *
     * @since 3.3.0
     */
    public enum RecyclerPoolType {
        /**
         * Selects the pool for the current thread: a buffer recycler per platform thread, and a concurrent deque pool for
         * virtual threads, which a thread local pool would never reuse.
         */
        DEFAULT,
        /**
         * A buffer recycler per thread, which is not reused on virtual threads.
         */
        THREAD_LOCAL,
        /**
         * An unbounded pool.
         */
        CONCURRENT_DEQUE,
        /**
         * An unbounded pool shared by all JSON mappers.
         */
        SHARED_CONCURRENT_DEQUE,
        /**
         * A pool bounded by {@link #getRecyclerPoolSize()}.
         */
        BOUNDED,
        /**
         * A bounded pool shared by all JSON mappers, with the Jackson default capacity.
         */
        SHARED_BOUNDED,
        /**
         * No pool: every parser and generator allocates its buffers.
         */
        NONE
    }
}
