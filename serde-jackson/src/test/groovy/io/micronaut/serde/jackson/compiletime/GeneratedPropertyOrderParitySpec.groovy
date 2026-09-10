package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.jackson.JsonCompileSpec
import tools.jackson.databind.json.JsonMapper as DatabindJsonMapper

/**
 * A type-level property order is applied when the generated serializer is built, so the generated
 * output has to match the runtime serializer and Jackson Databind property for property.
 */
class GeneratedPropertyOrderParitySpec extends JsonCompileSpec {

    void 'test an explicit property order is applied by the generated bean serializer'() {
        given:
        def generatedContext = ApplicationContext.run()
        def runtimeContext = runtimeContext()
        def generatedMapper = generatedContext.getBean(JsonMapper)
        def runtimeMapper = runtimeContext.getBean(JsonMapper)
        def bean = new SourceGenOrderedBean(first: '1', second: '2', third: '3', fourth: '4')

        when:
        String generatedJson = serializeToString(generatedMapper, bean)
        String runtimeJson = serializeToString(runtimeMapper, bean)
        String databindJson = DatabindJsonMapper.builder().build().writeValueAsString(bean)

        then:
        assertGeneratedSerializer(generatedContext.getBean(SerdeRegistry), Argument.of(SourceGenOrderedBean), true)
        assertGeneratedSerializer(runtimeContext.getBean(SerdeRegistry), Argument.of(SourceGenOrderedBean), false)
        generatedJson == '{"second":"2","first_name":"1","third":"3","fourth":"4"}'
        generatedJson == runtimeJson

        and: 'the listed properties lead on every stack; Jackson Databind sorts the unlisted ones alphabetically'
        keys(databindJson).take(2) == ['second', 'first_name']
        keys(databindJson).drop(2).toSet() == ['third', 'fourth'].toSet()

        cleanup:
        generatedContext.close()
        runtimeContext.close()
    }

    void 'test an alphabetic property order is applied by the generated record serializer'() {
        given:
        def generatedContext = ApplicationContext.run()
        def runtimeContext = runtimeContext()
        def generatedMapper = generatedContext.getBean(JsonMapper)
        def runtimeMapper = runtimeContext.getBean(JsonMapper)
        def record = new SourceGenAlphabeticOrderRecord('c', 1, true)

        when:
        String generatedJson = serializeToString(generatedMapper, record)
        String runtimeJson = serializeToString(runtimeMapper, record)
        String databindJson = DatabindJsonMapper.builder().build().writeValueAsString(record)

        then:
        assertGeneratedSerializer(generatedContext.getBean(SerdeRegistry), Argument.of(SourceGenAlphabeticOrderRecord), true)
        assertGeneratedSerializer(runtimeContext.getBean(SerdeRegistry), Argument.of(SourceGenAlphabeticOrderRecord), false)
        generatedJson == '{"alpha":1,"bravo":true,"charlie":"c"}'
        generatedJson == runtimeJson
        parse(generatedJson) == parse(databindJson)

        and: 'the order does not affect deserialization'
        generatedMapper.readValue('{"charlie":"c","alpha":1,"bravo":true}', SourceGenAlphabeticOrderRecord) == record

        cleanup:
        generatedContext.close()
        runtimeContext.close()
    }

    void 'test the generated serializer writes the properties in the resolved order'() {
        given:
        String source = generatedTestSource('io.micronaut.serde.jackson.compiletime.SerdeSourceGenOrderedBeanSerializer')

        expect:
        source.indexOf('"second"') < source.indexOf('"first_name"')
        source.indexOf('"first_name"') < source.indexOf('"third"')
        source.indexOf('"third"') < source.indexOf('"fourth"')
    }

    private static Map<String, Object> parse(String json) {
        (Map<String, Object>) DatabindJsonMapper.builder().build().readValue(json, Map)
    }

    private static List<String> keys(String json) {
        ((Map<String, Object>) DatabindJsonMapper.builder().build().readValue(json, LinkedHashMap)).keySet().toList()
    }

    private static ApplicationContext runtimeContext() {
        ApplicationContext.run(['micronaut.serde.serialization.disable-generated-serializer': true])
    }

    private static void assertGeneratedSerializer(SerdeRegistry registry, Argument argument, boolean generated) {
        Serializer serializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument)
        String packageName = argument.type.package.name
        String localName = argument.type.name.substring(packageName.length() + 1)
        assert (serializer.class.name == "${packageName}.Serde${localName}Serializer".toString()) == generated
    }
}
