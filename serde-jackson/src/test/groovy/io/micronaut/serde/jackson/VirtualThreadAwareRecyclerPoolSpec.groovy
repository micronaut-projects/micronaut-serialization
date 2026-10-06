package io.micronaut.serde.jackson

import io.micronaut.context.ApplicationContext
import io.micronaut.json.JsonMapper
import spock.lang.Specification
import tools.jackson.core.util.JsonRecyclerPools
import tools.jackson.core.util.BufferRecycler

import java.util.concurrent.atomic.AtomicReference

class VirtualThreadAwareRecyclerPoolSpec extends Specification {

    void "virtual threads reuse recyclers released by other virtual threads"() {
        given:
        def pool = new VirtualThreadAwareRecyclerPool()
        def first = new AtomicReference<BufferRecycler>()
        def second = new AtomicReference<BufferRecycler>()

        when:
        Thread.ofVirtual().start {
            BufferRecycler recycler = pool.acquireAndLinkPooled()
            first.set(recycler)
            recycler.releaseToPool()
        }.join()

        then:
        pool.pooledCount() == 1

        when:
        Thread.ofVirtual().start { second.set(pool.acquireAndLinkPooled()) }.join()

        then:
        second.get().is(first.get())
    }

    void "platform threads keep their own recycler"() {
        given:
        def pool = new VirtualThreadAwareRecyclerPool()

        when:
        BufferRecycler recycler = pool.acquireAndLinkPooled()
        recycler.releaseToPool()

        then:
        pool.acquireAndLinkPooled().is(recycler)
        pool.pooledCount() == 0
    }

    void "unlinked recyclers released on virtual threads are pooled and can be cleared"() {
        given:
        def pool = new VirtualThreadAwareRecyclerPool()
        def first = new AtomicReference<BufferRecycler>()
        def second = new AtomicReference<BufferRecycler>()

        when:
        Thread.ofVirtual().start {
            BufferRecycler recycler = pool.acquirePooled()
            first.set(recycler)
            pool.releasePooled(recycler)
        }.join()

        then:
        pool.pooledCount() == 1

        when:
        Thread.ofVirtual().start { second.set(pool.acquirePooled()) }.join()

        then:
        second.get().is(first.get())

        when:
        Thread.ofVirtual().start { pool.releasePooled(second.get()) }.join()

        then:
        pool.clear()
        pool.pooledCount() == 0
    }

    void "unlinked recyclers on platform threads stay with the thread"() {
        given:
        def pool = new VirtualThreadAwareRecyclerPool()

        when:
        BufferRecycler recycler = pool.acquirePooled()
        pool.releasePooled(recycler)

        then:
        pool.acquirePooled().is(recycler)
        pool.pooledCount() == 0
    }

    void "the JSON mapper writes and reads on virtual threads"() {
        given:
        def context = ApplicationContext.run()
        def jsonMapper = context.getBean(JsonMapper)
        def read = new AtomicReference<Object>()

        when:
        Thread.ofVirtual().start {
            String json = jsonMapper.writeValueAsString([name: 'micronaut'])
            read.set(jsonMapper.readValue(json, Map))
        }.join()

        then:
        read.get() == [name: 'micronaut']

        cleanup:
        context.close()
    }

    void "the recycler pool is configurable"() {
        given:
        def context = ApplicationContext.run(["micronaut.serde.jackson.recycler-pool": type.name()])
        def configuration = context.getBean(SerdeJacksonConfiguration)
        def jsonMapper = context.getBean(JsonMapper)

        expect:
        configuration.recyclerPool == type
        type.create().getClass() == poolType
        jsonMapper.readValue(jsonMapper.writeValueAsString([name: "micronaut"]), Map) == [name: "micronaut"]

        cleanup:
        context.close()

        where:
        type                                                  | poolType
        SerdeJacksonConfiguration.RecyclerPoolType.VIRTUAL_THREAD_AWARE | VirtualThreadAwareRecyclerPool
        SerdeJacksonConfiguration.RecyclerPoolType.THREAD_LOCAL         | JsonRecyclerPools.threadLocalPool().getClass()
        SerdeJacksonConfiguration.RecyclerPoolType.CONCURRENT_DEQUE     | JsonRecyclerPools.sharedConcurrentDequePool().getClass()
        SerdeJacksonConfiguration.RecyclerPoolType.BOUNDED              | JsonRecyclerPools.sharedBoundedPool().getClass()
        SerdeJacksonConfiguration.RecyclerPoolType.NONE                 | JsonRecyclerPools.nonRecyclingPool().getClass()
    }
}
