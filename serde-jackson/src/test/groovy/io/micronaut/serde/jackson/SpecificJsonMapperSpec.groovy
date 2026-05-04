package io.micronaut.serde.jackson

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

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

    void "write value as bytes releases buffer recycler"() {
        given:
            def recyclerPool = jacksonJsonMapper.@jsonFactory._getRecyclerPool()
            recyclerPool.clear()

        when:
            def bytes = jacksonJsonMapper.writeValueAsBytes(new TestX(name: "Fred"))

        then:
            new String(bytes) == '{"name":"Fred"}'
            recyclerPool.pooledCount() == 1

        cleanup:
            recyclerPool.clear()
    }

    void "write typed value as bytes releases buffer recycler"() {
        given:
            def recyclerPool = jacksonJsonMapper.@jsonFactory._getRecyclerPool()
            recyclerPool.clear()

        when:
            def bytes = jacksonJsonMapper.writeValueAsBytes(Argument.of(TestX), new TestX(name: "Fred"))

        then:
            new String(bytes) == '{"name":"Fred"}'
            recyclerPool.pooledCount() == 1

        cleanup:
            recyclerPool.clear()
    }

    void "read value from bytes releases buffer recycler"() {
        given:
            def recyclerPool = jacksonJsonMapper.@jsonFactory._getRecyclerPool()
            recyclerPool.clear()

        when:
            def value = jacksonJsonMapper.readValue('{"name":"Fred"}'.bytes, Argument.of(TestX))

        then:
            value.name == "Fred"
            recyclerPool.pooledCount() == 1

        cleanup:
            recyclerPool.clear()
    }

    void "read value from stream releases buffer recycler"() {
        given:
            def recyclerPool = jacksonJsonMapper.@jsonFactory._getRecyclerPool()
            recyclerPool.clear()

        when:
            def value = jacksonJsonMapper.readValue(new ByteArrayInputStream('{"name":"Fred"}'.bytes), Argument.of(TestX))

        then:
            value.name == "Fred"
            recyclerPool.pooledCount() == 1

        cleanup:
            recyclerPool.clear()
    }

    @Serdeable
    static class TestX {
        String name
    }

}
