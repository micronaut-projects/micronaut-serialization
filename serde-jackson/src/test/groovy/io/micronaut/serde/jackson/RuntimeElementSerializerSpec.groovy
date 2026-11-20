package io.micronaut.serde.jackson

import io.micronaut.core.type.Argument
import io.micronaut.serde.Encoder
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.Serializer
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

@MicronautTest
class RuntimeElementSerializerSpec extends Specification {

    @Inject ObjectMapper objectMapper

    void "the serializer of the elements of a class is created once for a collection without an element type"() {
        given:
            CountedSerializer.CREATED.set(0)
            def values = [new Counted(name: "a"), new Other(name: "Fred"), new Counted(name: "b"), null, new Counted(name: "c"), 1, "x"]

        expect:
            objectMapper.writeValueAsString(values) == '["counted:a",{"name":"Fred"},"counted:b",null,"counted:c",1,"x"]'
            CountedSerializer.CREATED.get() == 1
    }

    void "the values of a property without a type are written with the serializer of their class"() {
        expect:
            objectMapper.writeValueAsString(new Holder(value: new Other(name: "Fred"))) == '{"value":{"name":"Fred"}}'
            objectMapper.writeValueAsString(new Holder(value: new Counted(name: "a"))) == '{"value":"counted:a"}'
            objectMapper.writeValueAsString(new Holder(value: new Other(name: "Bob"))) == '{"value":{"name":"Bob"}}'
            objectMapper.writeValueAsString(new Holder(value: [new Holder(value: 1), new Holder(value: "x")])) == '{"value":[{"value":1},{"value":"x"}]}'
    }

    @Serdeable
    static class Other {
        String name
    }

    @Serdeable
    static class Holder {
        Object value
    }

    static class Counted {
        String name
    }

    @Singleton
    static class CountedSerializer implements Serializer<Counted> {
        static final AtomicInteger CREATED = new AtomicInteger()

        @Override
        Serializer<Counted> createSpecific(EncoderContext context, Argument<? extends Counted> type) {
            CREATED.incrementAndGet()
            return this
        }

        @Override
        void serialize(Encoder encoder, EncoderContext context, Argument<? extends Counted> type, Counted value) throws IOException {
            encoder.encodeString("counted:" + value.name)
        }
    }
}
