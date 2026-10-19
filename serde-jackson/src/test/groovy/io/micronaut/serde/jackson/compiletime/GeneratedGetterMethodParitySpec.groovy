package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.jackson.JsonCompileSpec
import tools.jackson.databind.json.JsonMapper as DatabindJsonMapper

/**
 * Methods that are not property accessors but declare a property through {@code @JsonProperty} or
 * {@code @JsonGetter} are written by the generated serializers exactly as the runtime serializer and
 * Jackson Databind write them.
 */
class GeneratedGetterMethodParitySpec extends JsonCompileSpec {

    void 'test record methods declaring a property serialize identically on every stack'() {
        given:
        def generatedContext = ApplicationContext.run()
        def runtimeContext = runtimeContext()
        def databindMapper = DatabindJsonMapper.builder().build()
        def record = new SourceGenGetterMethodRecord('x', 21)

        when:
        String generatedJson = serializeToString(generatedContext.getBean(JsonMapper), record)
        String runtimeJson = serializeToString(runtimeContext.getBean(JsonMapper), record)
        String databindJson = databindMapper.writeValueAsString(record)

        then:
        assertRegistrySelection(generatedContext.getBean(SerdeRegistry), Argument.of(SourceGenGetterMethodRecord), true)
        assertRegistrySelection(runtimeContext.getBean(SerdeRegistry), Argument.of(SourceGenGetterMethodRecord), false)
        generatedJson == '{"name":"x","count":21,"computed":"x!","doubled":42,"tags":["x","21"]}'
        generatedJson == runtimeJson
        parse(generatedJson) == parse(databindJson)

        cleanup:
        generatedContext.close()
        runtimeContext.close()
    }

    void 'test bean methods declaring a property serialize identically on every stack'() {
        given:
        def generatedContext = ApplicationContext.run()
        def runtimeContext = runtimeContext()
        def databindMapper = DatabindJsonMapper.builder().build()
        def bean = new SourceGenGetterMethodBean(name: 'ab')

        when:
        String generatedJson = serializeToString(generatedContext.getBean(JsonMapper), bean)
        String runtimeJson = serializeToString(runtimeContext.getBean(JsonMapper), bean)
        String databindJson = databindMapper.writeValueAsString(bean)

        then:
        assertRegistrySelection(generatedContext.getBean(SerdeRegistry), Argument.of(SourceGenGetterMethodBean), true)
        assertRegistrySelection(runtimeContext.getBean(SerdeRegistry), Argument.of(SourceGenGetterMethodBean), false)
        generatedJson == '{"name":"ab","display":"<ab>","letters":["a","b"]}'
        generatedJson == runtimeJson
        parse(generatedJson) == parse(databindJson)

        cleanup:
        generatedContext.close()
        runtimeContext.close()
    }

    void 'test a property order places method properties like the runtime serializer'() {
        given:
        def generatedContext = ApplicationContext.run()
        def runtimeContext = runtimeContext()
        def record = new SourceGenGetterMethodOrderRecord('x')

        when:
        String generatedJson = serializeToString(generatedContext.getBean(JsonMapper), record)
        String runtimeJson = serializeToString(runtimeContext.getBean(JsonMapper), record)

        then:
        assertRegistrySelection(generatedContext.getBean(SerdeRegistry), Argument.of(SourceGenGetterMethodOrderRecord), true)
        generatedJson == '{"computed":"x!","name":"x"}'
        generatedJson == runtimeJson

        cleanup:
        generatedContext.close()
        runtimeContext.close()
    }

    void 'test the serialized method properties are read back like the runtime deserializer'() {
        given:
        def generatedContext = ApplicationContext.run()
        def runtimeContext = runtimeContext()
        String recordJson = '{"name":"x","count":21,"computed":"x!","doubled":42,"tags":["x","21"]}'
        String beanJson = '{"name":"ab","display":"<ab>","letters":["a","b"]}'

        when:
        def generatedRecord = generatedContext.getBean(JsonMapper).readValue(recordJson, SourceGenGetterMethodRecord)
        def runtimeRecord = runtimeContext.getBean(JsonMapper).readValue(recordJson, SourceGenGetterMethodRecord)
        def generatedBean = generatedContext.getBean(JsonMapper).readValue(beanJson, SourceGenGetterMethodBean)
        def runtimeBean = runtimeContext.getBean(JsonMapper).readValue(beanJson, SourceGenGetterMethodBean)

        then:
        generatedRecord == new SourceGenGetterMethodRecord('x', 21)
        generatedRecord == runtimeRecord
        generatedBean.name == 'ab'
        runtimeBean.name == 'ab'

        cleanup:
        generatedContext.close()
        runtimeContext.close()
    }

    /**
     * Jackson Databind orders the properties of a type differently, so the comparison is on the parsed
     * document rather than on the exact text.
     */
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
