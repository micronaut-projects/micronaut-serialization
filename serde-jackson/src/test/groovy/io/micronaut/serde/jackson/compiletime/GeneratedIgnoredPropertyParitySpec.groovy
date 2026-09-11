package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Unroll
import tools.jackson.databind.json.JsonMapper as DatabindJsonMapper

/**
 * Ignored, read-only, write-only, getter-only and setter-only properties are resolved per direction
 * at build time. The generated serdes have to write and read exactly what the runtime serdes do.
 */
class GeneratedIgnoredPropertyParitySpec extends JsonCompileSpec {

    private static final List<Class<?>> SHAPES = [
        SourceGenIgnoredPropertiesBean,
        SourceGenIgnoreUnknownBean,
        SourceGenIgnoredNamesBean,
        SourceGenIncludedPropertiesBean
    ]

    void 'test the generated serdes are selected for every exclusion shape'() {
        given:
        def generatedContext = ApplicationContext.run()
        def runtimeContext = runtimeContext()

        expect:
        SHAPES.every { type ->
            assertRegistrySelection(generatedContext.getBean(SerdeRegistry), Argument.of(type), true)
            assertRegistrySelection(runtimeContext.getBean(SerdeRegistry), Argument.of(type), false)
            true
        }

        cleanup:
        generatedContext.close()
        runtimeContext.close()
    }

    @Unroll
    void 'test #type.simpleName serializes identically on every stack'() {
        given:
        def generatedContext = ApplicationContext.run()
        def runtimeContext = runtimeContext()
        def generatedMapper = generatedContext.getBean(JsonMapper)
        def runtimeMapper = runtimeContext.getBean(JsonMapper)
        def databindMapper = DatabindJsonMapper.builder().build()

        when:
        String generatedJson = serializeToString(generatedMapper, value)
        String runtimeJson = serializeToString(runtimeMapper, value)
        String databindJson = databindMapper.writeValueAsString(value)

        then:
        generatedJson == expected
        generatedJson == runtimeJson
        parse(generatedJson) == parse(databindJson)

        cleanup:
        generatedContext.close()
        runtimeContext.close()

        where:
        value                                                                                     | expected
        new SourceGenIgnoredPropertiesBean(name: 'ada', secret: 's', id: 7L, password: 'p')      | '{"name":"ada","id":7,"displayName":"ADA"}'
        new SourceGenIgnoreUnknownBean(name: 'ada')                                               | '{"name":"ada"}'
        new SourceGenIgnoredNamesBean(name: 'ada', internal: 'i')                                 | '{"name":"ada","internal":"i"}'
        new SourceGenIncludedPropertiesBean(name: 'ada', extra: 'e')                              | '{"name":"ada"}'
        type = value.getClass()
    }

    @Unroll
    void 'test #type.simpleName deserializes #json identically on the generated and the runtime stack'() {
        given:
        def generatedContext = ApplicationContext.run(['micronaut.serde.deserialization.ignore-unknown': false])
        def runtimeContext = runtimeContext(['micronaut.serde.deserialization.ignore-unknown': false])
        def generatedMapper = generatedContext.getBean(JsonMapper)
        def runtimeMapper = runtimeContext.getBean(JsonMapper)

        when:
        def generated = readOutcome(generatedMapper, type, json)
        def runtime = readOutcome(runtimeMapper, type, json)

        then:
        generated == expected
        generated == runtime

        cleanup:
        generatedContext.close()
        runtimeContext.close()

        where:
        type                            | json                                              | expected
        SourceGenIgnoredPropertiesBean  | '{"name":"ada","password":"p","token":"t"}'       | [name: 'ada', secret: null, id: 0L, password: 'p', tokenSetValue: 't']
        SourceGenIgnoredPropertiesBean  | '{"name":"ada","secret":"s"}'                     | [name: 'ada', secret: null, id: 0L, password: null, tokenSetValue: null]
        SourceGenIgnoredPropertiesBean  | '{"name":"ada","id":7}'                           | [name: 'ada', secret: null, id: 0L, password: null, tokenSetValue: null]
        SourceGenIgnoredPropertiesBean  | '{"name":"ada","displayName":"ADA"}'              | 'Unknown property [displayName]'
        SourceGenIgnoredPropertiesBean  | '{"name":"ada","other":1}'                        | 'Unknown property [other]'
        SourceGenIgnoreUnknownBean      | '{"name":"ada","other":1}'                        | [name: 'ada']
        SourceGenIgnoredNamesBean       | '{"name":"ada","internal":"i"}'                   | [name: 'ada', internal: null]
        SourceGenIgnoredNamesBean       | '{"name":"ada","other":1}'                        | 'Unknown property [other]'
        SourceGenIncludedPropertiesBean | '{"name":"ada","extra":"e","other":1}'            | [name: 'ada', extra: null]
    }

    void 'test the generated deserializer resolves the exclusion configuration at build time'() {
        given:
        def context = ApplicationContext.run()
        def introspections = context.getBean(SerdeIntrospections)
        String ignoredSource = generatedTestSource(deserializerClassName(introspections, SourceGenIgnoredPropertiesBean))
        String ignoreUnknownSource = generatedTestSource(deserializerClassName(introspections, SourceGenIgnoreUnknownBean))
        String includedSource = generatedTestSource(deserializerClassName(introspections, SourceGenIncludedPropertiesBean))

        expect: 'excluded writable properties are skipped through the key index'
        ignoredSource.contains('IGNORED_KEY_0 = "secret";')
        ignoredSource.contains('IGNORED_KEY_1 = "id";')

        and: 'a getter-only property is unknown on input'
        !ignoredSource.contains('"displayName"')
        ignoredSource.contains('this.ignoreUnknown = GeneratedSerdeExceptionUtil.ignoreUnknown(context);')

        and: 'a type-level unknown property policy is a constant'
        ignoreUnknownSource.contains('this.ignoreUnknown = true;')
        includedSource.contains('this.ignoreUnknown = true;')
        !includedSource.contains('"extra"') || includedSource.contains('IGNORED_KEY_0 = "extra";')

        cleanup:
        context.close()
    }

    private static Object readOutcome(JsonMapper mapper, Class<?> type, String json) {
        try {
            def value = mapper.readValue(json, type)
            return type.declaredFields
                .findAll { !it.synthetic }
                .collectEntries { field ->
                    field.accessible = true
                    [(field.name): field.get(value)]
                }
        } catch (Exception e) {
            return e.message.substring(0, e.message.indexOf(']') + 1)
        }
    }

    private static String deserializerClassName(SerdeIntrospections introspections, Class<?> type) {
        introspections.getDeserializableIntrospection(Argument.of(type)).annotationMetadata
            .stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElseThrow()
    }

    private static Map<String, Object> parse(String json) {
        (Map<String, Object>) DatabindJsonMapper.builder().build().readValue(json, Map)
    }

    private static ApplicationContext runtimeContext(Map<String, Object> properties = [:]) {
        ApplicationContext.run([
            'micronaut.serde.serialization.disable-generated-serializer'    : true,
            'micronaut.serde.deserialization.disable-generated-deserializer': true
        ] + properties)
    }

    private static void assertRegistrySelection(SerdeRegistry registry, Argument argument, boolean generated) {
        Serializer serializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument)
        Deserializer deserializer = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)
        assert (serializer.class.name == generatedClassName(argument.type, 'Serializer')) == generated
        assert (deserializer.class.name == generatedClassName(argument.type, 'Deserializer')) == generated
    }

    private static String generatedClassName(Class<?> type, String suffix) {
        String packageName = type.package.name
        String localName = type.name.substring(packageName.length() + 1)
        "${packageName}.Serde${localName.replace('.', '_').replace('$', '_')}${suffix}"
    }
}
