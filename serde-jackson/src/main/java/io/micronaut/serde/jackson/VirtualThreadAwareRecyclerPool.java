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
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A Jackson {@link BufferRecycler} pool that keeps a recycler per platform thread, and shares recyclers between virtual
 * threads.
 *
 * <p>A thread local pool never reuses a recycler on virtual threads, which are usually created for one task each: every
 * parser and generator would allocate new buffers. Virtual threads therefore take recyclers from a concurrent deque pool,
 * and give them back to it when their parser or generator is closed. The concurrent deque pool is created when a virtual
 * thread first asks for a recycler.</p>
 *
 * <p>The recyclers of virtual threads are kept in several deques, picked by the thread id, so that the threads running at
 * the same time do not all contend on one deque. A thread takes a recycler from another deque before it creates one.</p>
 *
 * @since 3.3.0
 */
@Internal
public final class VirtualThreadAwareRecyclerPool implements RecyclerPool<BufferRecycler> {

    @Serial
    private static final long serialVersionUID = 1L;

    private final RecyclerPool<BufferRecycler> platformThreadPool = JsonRecyclerPools.threadLocalPool();
    private final AtomicReference<RecyclerPool<BufferRecycler>> virtualThreadPool = new AtomicReference<>();

    /**
     * Creates a pool that uses {@link JsonRecyclerPools#threadLocalPool()} on platform threads and a new pool of
     * concurrent deques on virtual threads.
     */
    public VirtualThreadAwareRecyclerPool() {
        // the pool of virtual threads is created when a virtual thread first asks for a recycler
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
        RecyclerPool<BufferRecycler> pool = virtualThreadPool.get();
        return pool != null ? pool.pooledCount() : 0;
    }

    @Override
    public boolean clear() {
        RecyclerPool<BufferRecycler> pool = virtualThreadPool.get();
        return pool == null || pool.clear();
    }

    private RecyclerPool<BufferRecycler> pool() {
        if (!Thread.currentThread().isVirtual()) {
            return platformThreadPool;
        }
        RecyclerPool<BufferRecycler> pool = virtualThreadPool.get();
        if (pool == null) {
            pool = virtualThreadPool.updateAndGet(current -> current != null ? current : new StripedDequePool());
        }
        return pool;
    }

    /**
     * Concurrent deques of recyclers, picked by the id of the current thread. A thread takes the most recently released
     * recycler of its deque, whose buffers are the most likely to still be cached, and takes one from another deque
     * before it creates one.
     */
    private static final class StripedDequePool implements RecyclerPool<BufferRecycler> {

        @Serial
        private static final long serialVersionUID = 1L;

        private static final int STRIPES = Math.min(64, Integer.highestOneBit(Math.max(1, 2 * Runtime.getRuntime().availableProcessors() - 1)) << 1);

        private final ConcurrentLinkedDeque<BufferRecycler>[] stripes;

        @SuppressWarnings("unchecked")
        StripedDequePool() {
            stripes = new ConcurrentLinkedDeque[STRIPES];
            for (int i = 0; i < STRIPES; i++) {
                stripes[i] = new ConcurrentLinkedDeque<>();
            }
        }

        @Override
        public BufferRecycler acquireAndLinkPooled() {
            return acquirePooled().withPool(this);
        }

        @Override
        public BufferRecycler acquirePooled() {
            int stripe = stripe();
            BufferRecycler recycler = stripes[stripe].pollFirst();
            if (recycler != null) {
                return recycler;
            }
            for (int i = 1; i < STRIPES; i++) {
                recycler = stripes[(stripe + i) & (STRIPES - 1)].pollFirst();
                if (recycler != null) {
                    return recycler;
                }
            }
            return new BufferRecycler();
        }

        @Override
        public void releasePooled(BufferRecycler pooled) {
            stripes[stripe()].offerFirst(pooled);
        }

        @Override
        public int pooledCount() {
            int count = 0;
            for (ConcurrentLinkedDeque<BufferRecycler> deque : stripes) {
                count += deque.size();
            }
            return count;
        }

        @Override
        public boolean clear() {
            for (ConcurrentLinkedDeque<BufferRecycler> deque : stripes) {
                deque.clear();
            }
            return true;
        }

        private static int stripe() {
            return (int) Thread.currentThread().threadId() & (STRIPES - 1);
        }
    }
}
