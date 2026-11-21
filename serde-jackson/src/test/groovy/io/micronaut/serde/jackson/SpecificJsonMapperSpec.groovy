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

    void "the serdes of types without annotations are created once"() {
        given:
            def mapper = jacksonJsonMapper.cloneWithConfiguration(null, null, null)
            def annotated = Argument.of(TestX, 'x', new io.micronaut.inject.annotation.MutableAnnotationMetadata().tap {
                addAnnotation('com.fasterxml.jackson.annotation.JsonView', [value: Object])
            })

        when:
            def first = mapper.writeValueAsString(new TestX(name: "Fred"))
            def second = mapper.writeValueAsString(Argument.of(TestX), new TestX(name: "Bob"))
            def read = mapper.readValue('{"name":"Fred"}', TestX)
            def readAgain = mapper.readValue('{"name":"Bob"}', Argument.of(TestX))
            mapper.readValue('{"name":"Ann"}', annotated)
            mapper.writeValueAsString(annotated, new TestX(name: "Ann"))

        then:
            first == '{"name":"Fred"}'
            second == '{"name":"Bob"}'
            read.name == 'Fred'
            readAgain.name == 'Bob'
            mapper.@serdeCache.@serializers.size() == 1
            mapper.@serdeCache.@deserializers.size() == 1
    }

    void "the serdes are cached by type, whatever the names of the arguments"() {
        given:
            def mapper = jacksonJsonMapper.cloneWithConfiguration(null, null, null)

        when:
            mapper.writeValueAsString(Argument.of(TestX, 'first'), new TestX(name: "Fred"))
            mapper.writeValueAsString(Argument.of(TestX, 'second'), new TestX(name: "Bob"))
            def strings = mapper.writeValueAsString(Argument.listOf(String), ['a'])
            def numbers = mapper.writeValueAsString(Argument.listOf(Integer), [1])
            def readStrings = mapper.readValue('["1"]', Argument.listOf(String))
            def readNumbers = mapper.readValue('["1"]', Argument.listOf(Integer))

        then:
            strings == '["a"]'
            numbers == '[1]'
            readStrings == ['1']
            readNumbers == [1]
            mapper.@serdeCache.@serializers.size() == 3
            mapper.@serdeCache.@deserializers.size() == 2
    }

    void "untyped maps reuse the serializers of their values"() {
        given:
            def mapper = jacksonJsonMapper.cloneWithConfiguration(null, null, null)
            def type = Argument.mapOf(String, Object)
            Map<String, Object> value = [id: 1, owner: [name: 'Fred'], items: [[sku: 'a'], 'b']]
            def valueSerializer = { ->
                def field = Class.forName('io.micronaut.serde.support.serializers.AbstractMapObjectSerializer').getDeclaredField('valueSerializer')
                field.accessible = true
                field.get(mapper.@serdeCache.@serializers.values().first())
            }
            def inners = { serializer ->
                def field = serializer.class.getDeclaredField('inners')
                field.accessible = true
                new HashMap(field.get(serializer)).collectEntries { k, v -> [k, v.serializer()] }
            }

        when:
            def first = mapper.writeValueAsString(type, value)
            def firstInners = inners(valueSerializer())
            def second = mapper.writeValueAsString(type, value)
            def secondInners = inners(valueSerializer())

        then:
            first == '{"id":1,"owner":{"name":"Fred"},"items":[{"sku":"a"},"b"]}'
            second == first
            firstInners.keySet() == [Integer, LinkedHashMap, ArrayList] as Set
            firstInners.every { k, v -> secondInners[k].is(v) }
    }

    void "untyped maps keep the serializers bound to a document only for that document"() {
        given:
            def mapper = jacksonJsonMapper.cloneWithConfiguration(null, null, null)
            def type = Argument.mapOf(String, Object)
            def valueSerializer = { ->
                def field = Class.forName('io.micronaut.serde.support.serializers.AbstractMapObjectSerializer').getDeclaredField('valueSerializer')
                field.accessible = true
                field.get(mapper.@serdeCache.@serializers.get(type))
            }
            def inners = { serializer ->
                def field = serializer.class.getDeclaredField('inners')
                field.accessible = true
                new HashMap(field.get(serializer))
            }
            example.retain.RetainingSerializer.SPECIFICS.set(0)

        when:
            def first = mapper.writeValueAsString(type, [a: new example.retain.Retained(name: 'x'), b: new example.retain.Retained(name: 'y'), n: 1])
            def second = mapper.writeValueAsString(type, [a: new example.retain.Retained(name: 'z'), n: 2])

        then:
            first == '{"a":"x","b":"y","n":1}'
            second == '{"a":"z","n":2}'
            // created once for each document, and not kept by the serializer that the mapper caches
            example.retain.RetainingSerializer.SPECIFICS.get() == 2
            inners(valueSerializer()).keySet() == [Integer] as Set
    }

    void "derived mappers share the JSON factory and its buffer pool"() {
        when:
            def factory = jacksonJsonMapper.@jsonFactory
            def specific = jacksonJsonMapper.createSpecific(Argument.of(TestX))
            def withView = jacksonJsonMapper.cloneWithViewClass(Object)
            def withSerdeConfiguration = jacksonJsonMapper.cloneWithConfiguration(null, null, null)
            def withJacksonConfiguration = jacksonJsonMapper.cloneWithConfiguration(jacksonJsonMapper.@jacksonConfiguration)

        then:
            specific.@jsonFactory.is(factory)
            withView.@jsonFactory.is(factory)
            withSerdeConfiguration.@jsonFactory.is(factory)
            !withJacksonConfiguration.@jsonFactory.is(factory)
            specific.writeValueAsString(new TestX(name: "Fred")) == '{"name":"Fred"}'
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
