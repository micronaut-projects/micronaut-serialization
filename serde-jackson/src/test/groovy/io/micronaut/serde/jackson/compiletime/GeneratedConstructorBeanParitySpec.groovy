package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Unroll
import tools.jackson.databind.json.JsonMapper as DatabindJsonMapper

/**
 * A class bound through its constructor is generated like a record. Both directions have to match
 * the runtime serdes, and serialization has to match Jackson Databind.
 */
class GeneratedConstructorBeanParitySpec extends JsonCompileSpec {

    void 'test the immutable bean is generated as a constructor bound shape'() {
        given:
        def context = ApplicationContext.run()
        def registry = context.getBean(SerdeRegistry)
        Argument argument = Argument.of(SourceGenImmutableBean)
        def metadata = context.getBean(SerdeIntrospections).getSerializableIntrospection(argument).annotationMetadata

        expect:
        metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SHAPE).orElse(null) == 'CONSTRUCTOR_BEAN'
        registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument).class.name == 'io.micronaut.serde.jackson.compiletime.SerdeSourceGenImmutableBeanSerializer'
        registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument).class.name == 'io.micronaut.serde.jackson.compiletime.SerdeSourceGenImmutableBeanDeserializer'

        cleanup:
        context.close()
    }

    void 'test the immutable bean serializes identically on every stack'() {
        given:
        def generatedContext = ApplicationContext.run(['micronaut.serde.serialization.inclusion': 'ALWAYS'])
        def runtimeContext = ApplicationContext.run([
            'micronaut.serde.serialization.inclusion'                   : 'ALWAYS',
            'micronaut.serde.serialization.disable-generated-serializer': true
        ])
        def bean = new SourceGenImmutableBean('ada', 42, null, ['x'])

        when:
        String generatedJson = serializeToString(generatedContext.getBean(JsonMapper), bean)
        String runtimeJson = serializeToString(runtimeContext.getBean(JsonMapper), bean)
        String databindJson = DatabindJsonMapper.builder().build().writeValueAsString(bean)

        then:
        generatedJson == '{"name":"ada","count":42,"note":null,"tags":["x"]}'
        generatedJson == runtimeJson
        parse(generatedJson) == parse(databindJson)

        cleanup:
        generatedContext.close()
        runtimeContext.close()
    }

    @Unroll
    void 'test the immutable bean reads #json identically on the generated and the runtime stack (#configuration)'() {
        given:
        def generatedContext = ApplicationContext.run(configuration)
        def runtimeContext = ApplicationContext.run(configuration + ['micronaut.serde.deserialization.disable-generated-deserializer': true])

        when:
        def generated = readOutcome(generatedContext.getBean(JsonMapper), json)
        def runtime = readOutcome(runtimeContext.getBean(JsonMapper), json)

        then:
        generated == expected
        generated == runtime

        cleanup:
        generatedContext.close()
        runtimeContext.close()

        where:
        json                                              | configuration                                                            | expected
        '{"name":"ada","count":42,"note":"n","tags":["x"]}' | [:]                                                                      | [name: 'ada', count: 42, note: 'n', tags: ['x']]
        '{"tags":["x"],"count":42,"name":"ada"}'          | [:]                                                                      | [name: 'ada', count: 42, note: null, tags: ['x']]
        '{"name":"ada"}'                                  | [:]                                                                      | [name: 'ada', count: 0, note: null, tags: null]
        '{"name":"ada","note":null,"count":null}'         | ['micronaut.serde.deserialization.fail-on-null-for-primitives': false]   | [name: 'ada', count: 0, note: null, tags: null]
        '{"name":"ada","other":1}'                        | ['micronaut.serde.deserialization.ignore-unknown': false]                | 'Unknown property [other]'
        '{"count":1}'                                     | ['micronaut.serde.deserialization.require-all-creator-parameters': true] | 'Required constructor parameter [String name] at index [0] is not present or is null in the supplied data'
        '{}'                                              | [:]                                                                      | [name: null, count: 0, note: null, tags: null]
    }

    private static Object readOutcome(JsonMapper mapper, String json) {
        try {
            def value = mapper.readValue(json, SourceGenImmutableBean)
            return [name: value.name, count: value.count, note: value.note, tags: value.tags]
        } catch (Exception e) {
            String message = e.message
            int start = message.indexOf('Required ')
            if (start < 0) {
                start = message.indexOf('Unknown ')
                int end = message.indexOf(' encountered')
                if (start >= 0 && end > start) {
                    return message.substring(start, end)
                }
            }
            return start < 0 ? message : message.substring(start)
        }
    }

    private static Map<String, Object> parse(String json) {
        (Map<String, Object>) DatabindJsonMapper.builder().build().readValue(json, Map)
    }
}
