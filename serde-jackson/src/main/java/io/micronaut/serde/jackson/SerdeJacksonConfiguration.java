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
    private RecyclerPoolType recyclerPool = RecyclerPoolType.VIRTUAL_THREAD_AWARE;

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
     * The pool of the buffers that parsers and generators reuse. Defaults to {@link RecyclerPoolType#VIRTUAL_THREAD_AWARE}.
     *
     * @param recyclerPool The recycler pool
     * @since 3.3.0
     */
    public void setRecyclerPool(RecyclerPoolType recyclerPool) {
        this.recyclerPool = recyclerPool;
    }

    /**
     * The pools of the buffers that Jackson parsers and generators reuse.
     *
     * @since 3.3.0
     */
    public enum RecyclerPoolType {
        /**
         * A buffer recycler per platform thread, and a pool shared by virtual threads.
         */
        VIRTUAL_THREAD_AWARE,
        /**
         * A buffer recycler per thread, which is not reused on virtual threads.
         */
        THREAD_LOCAL,
        /**
         * An unbounded pool shared by all threads.
         */
        CONCURRENT_DEQUE,
        /**
         * A bounded pool shared by all threads.
         */
        BOUNDED,
        /**
         * No pool: every parser and generator allocates its buffers.
         */
        NONE;

        RecyclerPool<BufferRecycler> create() {
            return switch (this) {
                case VIRTUAL_THREAD_AWARE -> new VirtualThreadAwareRecyclerPool();
                case THREAD_LOCAL -> JsonRecyclerPools.threadLocalPool();
                case CONCURRENT_DEQUE -> JsonRecyclerPools.sharedConcurrentDequePool();
                case BOUNDED -> JsonRecyclerPools.sharedBoundedPool();
                case NONE -> JsonRecyclerPools.nonRecyclingPool();
            };
        }
    }
}
