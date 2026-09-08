package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Unroll
import tools.jackson.databind.json.JsonMapper as DatabindJsonMapper

/**
 * Required properties, required constructor parameters and aliases are resolved when the
 * deserializer is generated. Every outcome, a value or a failure, has to match the runtime
 * deserializer.
 */
class GeneratedRequiredPropertyParitySpec extends JsonCompileSpec {

    void 'test the generated deserializers are selected'() {
        given:
        def context = ApplicationContext.run()
        def registry = context.getBean(SerdeRegistry)

        expect:
        [SourceGenRequiredPropertiesBean, SourceGenRequiredComponentsRecord].every { type ->
            Argument argument = Argument.of(type)
            Deserializer deserializer = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)
            assert deserializer.class.name == "io.micronaut.serde.jackson.compiletime.Serde${type.simpleName}Deserializer".toString()
            true
        }

        cleanup:
        context.close()
    }

    @Unroll
    void 'test #type.simpleName reads #json identically on the generated and the runtime stack (#configuration)'() {
        given:
        def generatedContext = ApplicationContext.run(configuration)
        def runtimeContext = ApplicationContext.run(configuration + ['micronaut.serde.deserialization.disable-generated-deserializer': true])

        when:
        def generated = readOutcome(generatedContext.getBean(JsonMapper), type, json)
        def runtime = readOutcome(runtimeContext.getBean(JsonMapper), type, json)

        then:
        generated == expected
        generated == runtime

        cleanup:
        generatedContext.close()
        runtimeContext.close()

        where:
        type                             | json                                                | configuration                                                              | expected
        SourceGenRequiredPropertiesBean  | '{"name":"ada","count":1,"email":"a@b","note":"x"}' | [:]                                                                        | [name: 'ada', count: 1, email: 'a@b', note: 'x']
        SourceGenRequiredPropertiesBean  | '{"count":1}'                                       | [:]                                                                        | 'Required property [String name] is not present in supplied data'
        SourceGenRequiredPropertiesBean  | '{"name":null,"count":1}'                           | [:]                                                                        | 'Required property [String name] is not present or is null in the supplied data'
        SourceGenRequiredPropertiesBean  | '{"name":"ada"}'                                    | [:]                                                                        | 'Required property [int count] is not present in supplied data'
        SourceGenRequiredPropertiesBean  | '{"name":"ada","count":null}'                       | ['micronaut.serde.deserialization.fail-on-null-for-primitives': false]     | [name: 'ada', count: 0, email: null, note: null]
        SourceGenRequiredPropertiesBean  | '{"name":"ada","count":1,"mail":"a@b"}'             | [:]                                                                        | [name: 'ada', count: 1, email: 'a@b', note: null]
        SourceGenRequiredPropertiesBean  | '{"name":"ada","count":1,"e-mail":"a@b"}'           | [:]                                                                        | [name: 'ada', count: 1, email: 'a@b', note: null]
        SourceGenRequiredPropertiesBean  | '{"name":"ada","count":1,"email":"a","mail":"b"}'   | ['micronaut.serde.deserialization.ignore-unknown': false]                  | 'Duplicate property [mail] encountered during deserialization'
        SourceGenRequiredComponentsRecord | '{"name":"ada","note":"x","count":1,"label":"l"}'  | [:]                                                                        | [name: 'ada', note: 'x', count: 1, label: 'l']
        SourceGenRequiredComponentsRecord | '{"note":"x","count":1}'                           | [:]                                                                        | 'Required constructor parameter [String name] at index [0] is not present or is null in the supplied data'
        SourceGenRequiredComponentsRecord | '{"name":null,"note":"x","count":1}'               | [:]                                                                        | 'Required constructor parameter [String name] at index [0] is not present or is null in the supplied data'
        SourceGenRequiredComponentsRecord | '{"name":"ada","note":null,"count":1}'             | [:]                                                                        | 'Required constructor parameter [String note] at index [1] is not present or is null in the supplied data'
        SourceGenRequiredComponentsRecord | '{"name":"ada","count":1}'                         | [:]                                                                        | 'Required constructor parameter [String note] at index [1] is not present or is null in the supplied data'
        SourceGenRequiredComponentsRecord | '{"name":"ada","note":"x","n":7}'                  | [:]                                                                        | [name: 'ada', note: 'x', count: 7, label: null]
        SourceGenRequiredComponentsRecord | '{"name":"ada","note":"x","count":1}'              | ['micronaut.serde.deserialization.require-all-creator-parameters': true]   | 'Required constructor parameter [String label] at index [3] is not present or is null in the supplied data'
        SourceGenRequiredComponentsRecord | '{"name":"ada","note":"x","count":null,"label":"l"}' | ['micronaut.serde.deserialization.require-all-creator-parameters': true, 'micronaut.serde.deserialization.fail-on-null-for-primitives': false] | 'Required constructor parameter [int count] at index [2] is not present or is null in the supplied data'
        SourceGenRequiredComponentsRecord | '{"name":"ada","note":"x","count":1,"label":"l"}'  | ['micronaut.serde.deserialization.require-all-creator-parameters': true]   | [name: 'ada', note: 'x', count: 1, label: 'l']
    }

    void 'test aliases agree with Jackson Databind'() {
        given:
        def context = ApplicationContext.run()
        def mapper = context.getBean(JsonMapper)
        def databindMapper = DatabindJsonMapper.builder().build()
        String json = '{"name":"ada","count":1,"e-mail":"a@b"}'

        expect:
        mapper.readValue(json, SourceGenRequiredPropertiesBean).email == 'a@b'
        databindMapper.readValue(json, SourceGenRequiredPropertiesBean).email == 'a@b'
        mapper.readValue('{"name":"ada","note":"x","n":7}', SourceGenRequiredComponentsRecord).count() == 7
        databindMapper.readValue('{"name":"ada","note":"x","n":7}', SourceGenRequiredComponentsRecord).count() == 7

        cleanup:
        context.close()
    }

    void 'test the generated deserializers resolve required properties and aliases at build time'() {
        given:
        String beanSource = generatedTestSource('io.micronaut.serde.jackson.compiletime.SerdeSourceGenRequiredPropertiesBeanDeserializer')
        String recordSource = generatedTestSource('io.micronaut.serde.jackson.compiletime.SerdeSourceGenRequiredComponentsRecordDeserializer')

        expect: 'aliases are further keys dispatching to the property'
        beanSource.contains('ALIAS_KEY_0 = "mail";')
        beanSource.contains('ALIAS_KEY_1 = "e-mail";')
        recordSource.contains('ALIAS_KEY_0 = "n";')

        and: 'a required property is checked once the object is consumed, behind a single mask compare'
        beanSource.contains('GeneratedSerdeExceptionUtil.requiredProperty(')
        beanSource.contains('if ((seenProperties & 3l) != 3l)')

        and: 'the record resolves the require-all setting once'
        recordSource.contains('this.requireAllCreatorParameters = GeneratedSerdeExceptionUtil.requireAllCreatorParameters(context);')
        recordSource.contains('GeneratedSerdeExceptionUtil.requiredConstructorParameter(')
    }

    private static Object readOutcome(JsonMapper mapper, Class<?> type, String json) {
        try {
            def value = mapper.readValue(json, type)
            if (type.isRecord()) {
                return type.recordComponents.collectEntries { component -> [(component.name): component.accessor.invoke(value)] }
            }
            return type.declaredFields
                .findAll { !it.synthetic }
                .collectEntries { field ->
                    field.accessible = true
                    [(field.name): field.get(value)]
                }
        } catch (Exception e) {
            // The generated and the runtime deserializer describe the type differently in a duplicate message
            String message = e.message
            int start = message.indexOf('Required ')
            if (start < 0) {
                start = message.indexOf('Duplicate ')
                int end = message.indexOf(' of type')
                if (start >= 0 && end > start) {
                    return message.substring(start, end)
                }
            }
            return start < 0 ? message : message.substring(start)
        }
    }
}
