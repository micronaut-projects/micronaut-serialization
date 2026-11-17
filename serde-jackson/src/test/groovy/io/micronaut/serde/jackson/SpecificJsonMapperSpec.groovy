package io.micronaut.serde.jackson

import io.micronaut.core.type.Argument
import io.micronaut.core.io.buffer.ReadBuffer
import io.micronaut.core.io.buffer.ReadBufferFactory
import io.micronaut.json.JsonSyntaxException
import java.nio.ByteBuffer
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class SpecificJsonMapperSpec extends Specification {

    @Inject ObjectMapper objectMapper
    @Inject JacksonJsonMapper jacksonJsonMapper

    void 'read buffers parse their heap slice without allocating an array'() {
        given:
        byte[] bytes = 'junk{"name":"Fred"}tail'.bytes
        def buffer = Mock(ReadBuffer)

        when:
        def value = jacksonJsonMapper.readValue(buffer, Argument.of(TestX))

        then:
        1 * buffer.useFastHeapBuffer(_) >> { arguments ->
            arguments[0].apply(ByteBuffer.wrap(bytes, 4, bytes.length - 8))
        }
        0 * buffer.toArray()
        value.name == 'Fred'
    }

    void 'read buffers support JSON null and direct input'() {
        given:
        def input = ByteBuffer.allocateDirect(4)
        input.put('null'.bytes).flip()
        def buffer = ReadBufferFactory.getJdkFactory().adapt(input)

        expect:
        jacksonJsonMapper.readValue(buffer, Argument.of(TestX)) == null
        jacksonJsonMapper.readValue(ReadBufferFactory.getJdkFactory().adapt('null'.bytes), Argument.of(TestX)) == null
    }

    void 'malformed read buffers preserve syntax exceptions'() {
        when:
        jacksonJsonMapper.readValue(ReadBufferFactory.getJdkFactory().adapt('{'.bytes), Argument.of(TestX))

        then:
        thrown(JsonSyntaxException)
    }

    void "test specific mapper"() {
        when:
            def specific = objectMapper.createSpecific(Argument.of(TestX))
        then:
            specific.writeValueAsString(new TestX(name: "Fred")) == '{"name":"Fred"}'
            specific.@specificType
            specific.@specificSerializer
            specific.@specificDeserializer

    }

    void "write value as bytes uses the thread local buffer recycler on platform threads"() {
        given:
            def recyclerPool = jacksonJsonMapper.@jsonFactory._getRecyclerPool()

        when:
            def bytes = jacksonJsonMapper.writeValueAsBytes(new TestX(name: "Fred"))

        then:
            new String(bytes) == '{"name":"Fred"}'
            recyclerPool instanceof VirtualThreadAwareRecyclerPool
            recyclerPool.pooledCount() == 0
    }

    void "write typed value as bytes uses the thread local buffer recycler on platform threads"() {
        given:
            def recyclerPool = jacksonJsonMapper.@jsonFactory._getRecyclerPool()

        when:
            def bytes = jacksonJsonMapper.writeValueAsBytes(Argument.of(TestX), new TestX(name: "Fred"))

        then:
            new String(bytes) == '{"name":"Fred"}'
            recyclerPool instanceof VirtualThreadAwareRecyclerPool
            recyclerPool.pooledCount() == 0
    }

    void "read value from bytes uses the thread local buffer recycler on platform threads"() {
        given:
            def recyclerPool = jacksonJsonMapper.@jsonFactory._getRecyclerPool()

        when:
            def value = jacksonJsonMapper.readValue('{"name":"Fred"}'.bytes, Argument.of(TestX))

        then:
            value.name == "Fred"
            recyclerPool instanceof VirtualThreadAwareRecyclerPool
            recyclerPool.pooledCount() == 0
    }

    void "read value from stream uses the thread local buffer recycler on platform threads"() {
        given:
            def recyclerPool = jacksonJsonMapper.@jsonFactory._getRecyclerPool()

        when:
            def value = jacksonJsonMapper.readValue(new ByteArrayInputStream('{"name":"Fred"}'.bytes), Argument.of(TestX))

        then:
            value.name == "Fred"
            recyclerPool instanceof VirtualThreadAwareRecyclerPool
            recyclerPool.pooledCount() == 0
    }

    @Serdeable
    static class TestX {
        String name
    }

}
