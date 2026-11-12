package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.jackson.JsonCompileSpec
import tools.jackson.databind.json.JsonMapper as DatabindJsonMapper

/**
 * Kotlin declares nested collection properties such as {@code Map<String, Map<String, String>>} as
 * {@code Map<String, ? extends Map<String, String>>}. The generated serdes have to keep the nested
 * collection type of the wildcard bound instead of collapsing it to the bound's first type argument.
 */
class GeneratedWildcardNestedCollectionsParitySpec extends JsonCompileSpec {

    private static final String JSON = '{"deepMap":{"characteristics":{"color":"pink"},"issues":{"isWorking":"no","hasCracks":"yes"}},' +
        '"deepObjectMap":{"polygons":{"triangle":{"name":"a"}}},' +
        '"deepList":[["a","b"],["c"]]}'

    void 'test the wildcard nested collections bean uses generated serdes'() {
        given:
        def context = ApplicationContext.run()
        def registry = context.getBean(SerdeRegistry)
        Argument argument = Argument.of(SourceGenWildcardNestedCollectionsBean)

        expect:
        registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument).class.name == 'io.micronaut.serde.jackson.compiletime.SerdeSourceGenWildcardNestedCollectionsBeanSerializer'
        registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument).class.name == 'io.micronaut.serde.jackson.compiletime.SerdeSourceGenWildcardNestedCollectionsBeanDeserializer'

        cleanup:
        context.close()
    }

    void 'test nested wildcard collections serialize identically on every stack'() {
        given:
        def generatedContext = ApplicationContext.run()
        def runtimeContext = ApplicationContext.run(['micronaut.serde.serialization.disable-generated-serializer': true])
        def bean = newBean()

        when:
        String generatedJson = serializeToString(generatedContext.getBean(JsonMapper), bean)
        String runtimeJson = serializeToString(runtimeContext.getBean(JsonMapper), bean)
        String databindJson = DatabindJsonMapper.builder().build().writeValueAsString(bean)

        then:
        generatedJson == JSON
        generatedJson == runtimeJson
        parse(generatedJson) == parse(databindJson)

        cleanup:
        generatedContext.close()
        runtimeContext.close()
    }

    void 'test nested wildcard collections deserialize identically on the generated and the runtime stack'() {
        given:
        def generatedContext = ApplicationContext.run()
        def runtimeContext = ApplicationContext.run(['micronaut.serde.deserialization.disable-generated-deserializer': true])

        when:
        def generated = generatedContext.getBean(JsonMapper).readValue(JSON, SourceGenWildcardNestedCollectionsBean)
        def runtime = runtimeContext.getBean(JsonMapper).readValue(JSON, SourceGenWildcardNestedCollectionsBean)

        then:
        generated.deepMap == [characteristics: [color: 'pink'], issues: [isWorking: 'no', hasCracks: 'yes']]
        generated.deepObjectMap == [polygons: [triangle: new SourceGenWildcardNestedCollectionsBean.Item('a')]]
        generated.deepList == [['a', 'b'], ['c']]
        generated.deepMap == runtime.deepMap
        generated.deepObjectMap == runtime.deepObjectMap
        generated.deepList == runtime.deepList

        cleanup:
        generatedContext.close()
        runtimeContext.close()
    }

    private static SourceGenWildcardNestedCollectionsBean newBean() {
        def deepMap = new LinkedHashMap<String, Map<String, String>>()
        deepMap.characteristics = Collections.singletonMap('color', 'pink')
        def issues = new LinkedHashMap<String, String>()
        issues.isWorking = 'no'
        issues.hasCracks = 'yes'
        deepMap.issues = issues
        def deepObjectMap = new LinkedHashMap<String, Map<String, SourceGenWildcardNestedCollectionsBean.Item>>()
        deepObjectMap.polygons = Collections.singletonMap('triangle', new SourceGenWildcardNestedCollectionsBean.Item('a'))
        new SourceGenWildcardNestedCollectionsBean(deepMap, deepObjectMap, [['a', 'b'], ['c']])
    }

    private static Map<String, Object> parse(String json) {
        (Map<String, Object>) DatabindJsonMapper.builder().build().readValue(json, Map)
    }
}
