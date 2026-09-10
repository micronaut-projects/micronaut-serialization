package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Unroll
import tools.jackson.databind.json.JsonMapper as DatabindJsonMapper

/**
 * Inclusions declared on a type or a property are resolved when the serializer is generated. For every runtime configuration the generated output has to match the runtime
 * serializer, and with the default configuration it has to match Jackson Databind.
 */
class GeneratedIncludeParitySpec extends JsonCompileSpec {

    private static final List<String> INCLUSIONS = [
        'ALWAYS', 'USE_DEFAULTS', 'NON_NULL', 'NON_ABSENT', 'NON_EMPTY', 'NON_DEFAULT', 'NEVER'
    ]

    @Unroll
    void 'test generated serializers match the runtime serializers with the #inclusion configuration'() {
        given:
        def generatedContext = ApplicationContext.run(['micronaut.serde.serialization.inclusion': inclusion])
        def runtimeContext = ApplicationContext.run([
            'micronaut.serde.serialization.inclusion'                   : inclusion,
            'micronaut.serde.serialization.disable-generated-serializer': true
        ])
        def generatedMapper = generatedContext.getBean(JsonMapper)
        def runtimeMapper = runtimeContext.getBean(JsonMapper)

        expect:
        [SourceGenIncludeOverridesBean, SourceGenIncludeOverridesRecord].every { type ->
            assertGeneratedSerializer(generatedContext.getBean(SerdeRegistry), Argument.of(type), true)
            assertGeneratedSerializer(runtimeContext.getBean(SerdeRegistry), Argument.of(type), false)
            true
        }

        and:
        payloads().every { payload ->
            String generatedJson = serializeToString(generatedMapper, payload)
            String runtimeJson = serializeToString(runtimeMapper, payload)
            assert generatedJson == runtimeJson
            true
        }

        cleanup:
        generatedContext.close()
        runtimeContext.close()

        where:
        inclusion << INCLUSIONS
    }

    void 'test generated serializers apply the declared inclusions with the default configuration'() {
        given:
        def context = ApplicationContext.run()
        def mapper = context.getBean(JsonMapper)
        def databindMapper = DatabindJsonMapper.builder().build()

        expect:
        serializeToString(mapper, new SourceGenIncludeOverridesBean()) == '{"flag":false,"always":null}'
        serializeToString(mapper, filledBean()) == '{"name":"n","items":["i"],"flag":true,"always":"a","text":"t","tags":["g"],"count":1,"id":2,"label":"l","configured":"c"}'
        serializeToString(mapper, new SourceGenIncludeOverridesRecord(null, [], 0, null, 0)) == '{"count":0,"always":null}'
        serializeToString(mapper, new SourceGenIncludeOverridesRecord('n', ['i'], 1, 'a', 2)) == '{"name":"n","items":["i"],"count":1,"always":"a","score":2}'

        and: 'Jackson Databind agrees on the written properties (USE_DEFAULTS defers to the runtime configuration here, so it stays null)'
        payloads().every { payload ->
            assert parse(serializeToString(mapper, payload)) == parse(databindMapper.writeValueAsString(payload))
            true
        }

        cleanup:
        context.close()
    }

    void 'test the generated serializer resolves declared inclusions at build time'() {
        given:
        String beanSource = generatedTestSource('io.micronaut.serde.jackson.compiletime.SerdeSourceGenIncludeOverridesBeanSerializer')
        String recordSource = generatedTestSource('io.micronaut.serde.jackson.compiletime.SerdeSourceGenIncludeOverridesRecordSerializer')

        expect: 'a NON_NULL inclusion is a null check'
        beanSource.contains('if (value0 != null)')

        and: 'only the USE_DEFAULTS property consults the configuration, through the shared field'
        beanSource.count('resolveInclusion') == 1
        beanSource.count('this.includeAll ||') == 1

        and: 'a serializer whose inclusions are all declared never consults the configuration'
        !recordSource.contains('resolveInclusion')
        !recordSource.contains('this.include')

        and: 'each declared inclusion compiles to the check matching the serde that writes the value'
        recordSource.contains('if (value0 != null && !value0.isEmpty())')
        recordSource.contains('GeneratedSerdeInclusionUtil.shouldSerialize(SerdeConfig.SerInclude.NON_EMPTY, context, this.serializer1, ')
        recordSource.contains('if (!(value4 == 0))')
    }

    private static List<Object> payloads() {
        [
            new SourceGenIncludeOverridesBean(),
            filledBean(),
            new SourceGenIncludeOverridesBean(name: '', items: [], text: '', tags: [], count: 0, id: 0L, label: ''),
            new SourceGenIncludeOverridesRecord(null, null, 0, null, 0),
            new SourceGenIncludeOverridesRecord('', [], 0, '', 0),
            new SourceGenIncludeOverridesRecord('n', ['i'], 1, 'a', 2)
        ]
    }

    private static SourceGenIncludeOverridesBean filledBean() {
        new SourceGenIncludeOverridesBean(name: 'n', items: ['i'], flag: true, always: 'a', text: 't', tags: ['g'], count: 1, id: 2L, label: 'l', configured: 'c')
    }

    private static Map<String, Object> parse(String json) {
        (Map<String, Object>) DatabindJsonMapper.builder().build().readValue(json, Map)
    }

    private static void assertGeneratedSerializer(SerdeRegistry registry, Argument argument, boolean generated) {
        Serializer serializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument)
        String packageName = argument.type.package.name
        String localName = argument.type.name.substring(packageName.length() + 1)
        assert (serializer.class.name == "${packageName}.Serde${localName}Serializer".toString()) == generated
    }
}
