package io.micronaut.serde.jackson

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import tools.jackson.core.util.JsonRecyclerPools

@MicronautTest
class SpecificJsonMapperSpec extends Specification {

    @Inject ObjectMapper objectMapper
    @Inject JacksonJsonMapper jacksonJsonMapper

    void "test specific mapper"() {
        when:
            def specific = objectMapper.createSpecific(Argument.of(TestX))
        then:
            specific.writeValueAsString(new TestX(name: "Fred")) == '{"name":"Fred"}'
            specific.@specificType
            specific.@specificSerializer
            specific.@specificDeserializer

    }

    void "write value as bytes uses thread local buffer recycler"() {
        given:
            def recyclerPool = jacksonJsonMapper.@jsonFactory._getRecyclerPool()

        when:
            def bytes = jacksonJsonMapper.writeValueAsBytes(new TestX(name: "Fred"))

        then:
            new String(bytes) == '{"name":"Fred"}'
            recyclerPool instanceof JsonRecyclerPools.ThreadLocalPool
            recyclerPool.pooledCount() == -1
    }

    void "write typed value as bytes uses thread local buffer recycler"() {
        given:
            def recyclerPool = jacksonJsonMapper.@jsonFactory._getRecyclerPool()

        when:
            def bytes = jacksonJsonMapper.writeValueAsBytes(Argument.of(TestX), new TestX(name: "Fred"))

        then:
            new String(bytes) == '{"name":"Fred"}'
            recyclerPool instanceof JsonRecyclerPools.ThreadLocalPool
            recyclerPool.pooledCount() == -1
    }

    void "read value from bytes uses thread local buffer recycler"() {
        given:
            def recyclerPool = jacksonJsonMapper.@jsonFactory._getRecyclerPool()

        when:
            def value = jacksonJsonMapper.readValue('{"name":"Fred"}'.bytes, Argument.of(TestX))

        then:
            value.name == "Fred"
            recyclerPool instanceof JsonRecyclerPools.ThreadLocalPool
            recyclerPool.pooledCount() == -1
    }

    void "read value from stream uses thread local buffer recycler"() {
        given:
            def recyclerPool = jacksonJsonMapper.@jsonFactory._getRecyclerPool()

        when:
            def value = jacksonJsonMapper.readValue(new ByteArrayInputStream('{"name":"Fred"}'.bytes), Argument.of(TestX))

        then:
            value.name == "Fred"
            recyclerPool instanceof JsonRecyclerPools.ThreadLocalPool
            recyclerPool.pooledCount() == -1
    }

    @Serdeable
    static class TestX {
        String name
    }

}
