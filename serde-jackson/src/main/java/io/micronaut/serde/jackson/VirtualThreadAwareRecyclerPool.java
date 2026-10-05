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
package io.micronaut.serde.jackson;

import io.micronaut.core.annotation.Internal;
import tools.jackson.core.util.BufferRecycler;
import tools.jackson.core.util.JsonRecyclerPools;
import tools.jackson.core.util.RecyclerPool;

import java.io.Serial;

/**
 * A Jackson {@link BufferRecycler} pool that keeps a recycler per platform thread, and shares recyclers between virtual
 * threads.
 *
 * <p>A thread local pool never reuses a recycler on virtual threads, which are usually created for one task each: every
 * parser and generator would allocate new buffers. Virtual threads therefore take recyclers from a concurrent deque pool,
 * and give them back to it when their parser or generator is closed.</p>
 *
 * @since 3.3.0
 */
@Internal
public final class VirtualThreadAwareRecyclerPool implements RecyclerPool<BufferRecycler> {

    @Serial
    private static final long serialVersionUID = 1L;

    private final RecyclerPool<BufferRecycler> platformThreadPool;
    private final RecyclerPool<BufferRecycler> virtualThreadPool;

    /**
     * Creates a pool that uses {@link JsonRecyclerPools#threadLocalPool()} on platform threads and a new
     * {@link JsonRecyclerPools#newConcurrentDequePool() concurrent deque pool} on virtual threads.
     */
    public VirtualThreadAwareRecyclerPool() {
        this(JsonRecyclerPools.threadLocalPool(), JsonRecyclerPools.newConcurrentDequePool());
    }

    VirtualThreadAwareRecyclerPool(RecyclerPool<BufferRecycler> platformThreadPool,
                                   RecyclerPool<BufferRecycler> virtualThreadPool) {
        this.platformThreadPool = platformThreadPool;
        this.virtualThreadPool = virtualThreadPool;
    }

    @Override
    public BufferRecycler acquireAndLinkPooled() {
        // The recycler is linked to the pool it came from, which then takes it back directly
        return pool().acquireAndLinkPooled();
    }

    @Override
    public BufferRecycler acquirePooled() {
        return pool().acquirePooled();
    }

    @Override
    public void releasePooled(BufferRecycler pooled) {
        pool().releasePooled(pooled);
    }

    @Override
    public int pooledCount() {
        return virtualThreadPool.pooledCount();
    }

    @Override
    public boolean clear() {
        return virtualThreadPool.clear();
    }

    private RecyclerPool<BufferRecycler> pool() {
        return Thread.currentThread().isVirtual() ? virtualThreadPool : platformThreadPool;
    }
}
