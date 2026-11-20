package io.micronaut.serde.jackson.specific

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

/**
 * {@link SecretSerde.SecretSerializer} requires the context of the call it serves to be the one it was created with.
 */
@Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/1516")
class RawCollectionContextSerdeSpec extends Specification {

    @Unroll
    void "the element serializer of a #shape property is created with the context of each document (generated: #generated)"() {
        given:
        def context = ApplicationContext.run(generated ? [:] : [
            'micronaut.serde.serialization.disable-generated-serializer': true,
            'micronaut.serde.deserialization.disable-generated-deserializer': true
        ])
        def mapper = context.getBean(ObjectMapper)

        expect:
        mapper.writeValueAsString(bean) == json
        mapper.writeValueAsString(bean) == json

        cleanup:
        context.close()

        where:
        shape         | generated | bean                                                         | json
        'raw List'    | true      | new RawListHolder(values: [new Secret("a"), "x"])            | '{"values":["a","x"]}'
        'raw List'    | false     | new RawListHolder(values: [new Secret("a"), "x"])            | '{"values":["a","x"]}'
        'List<Object>' | true     | new ObjectListHolder(values: [new Secret("b")])              | '{"values":["b"]}'
        'List<Object>' | false    | new ObjectListHolder(values: [new Secret("b")])              | '{"values":["b"]}'
        'Object'      | true      | new ObjectHolder(value: new Secret("c"))                     | '{"value":"c"}'
        'Object'      | false     | new ObjectHolder(value: new Secret("c"))                     | '{"value":"c"}'
        'raw Map'     | true      | new RawMapHolder(values: [k: new Secret("d")])               | '{"values":{"k":"d"}}'
        'raw Map'     | false     | new RawMapHolder(values: [k: new Secret("d")])               | '{"values":{"k":"d"}}'
    }

    @Serdeable
    static class RawListHolder {
        List values
    }

    @Serdeable
    static class ObjectListHolder {
        List<Object> values
    }

    @Serdeable
    static class ObjectHolder {
        Object value
    }

    @Serdeable
    static class RawMapHolder {
        Map values
    }
}
