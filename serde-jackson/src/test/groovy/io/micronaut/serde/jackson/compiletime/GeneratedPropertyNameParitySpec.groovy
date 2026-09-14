package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.jackson.JsonCompileSpec
import tools.jackson.databind.json.JsonMapper as DatabindJsonMapper

/**
 * Property names resolved at build time, through {@code @JsonProperty} or a compile-time naming
 * strategy, are honored by the generated serdes exactly as the runtime serde and Jackson Databind
 * honor them.
 */
class GeneratedPropertyNameParitySpec extends JsonCompileSpec {

    void 'test renamed bean properties serialize identically on every stack'() {
        given:
        def generatedContext = ApplicationContext.run()
        def runtimeContext = runtimeContext()
        def generatedMapper = generatedContext.getBean(JsonMapper)
        def runtimeMapper = runtimeContext.getBean(JsonMapper)
        def databindMapper = DatabindJsonMapper.builder().build()
        def bean = new SourceGenRenamedPropertiesBean(firstName: 'Ada', count: 42, email: 'ada@example.com')

        when:
        String generatedJson = serializeToString(generatedMapper, bean)
        String runtimeJson = serializeToString(runtimeMapper, bean)
        String databindJson = databindMapper.writeValueAsString(bean)

        then:
        assertRegistrySelection(generatedContext.getBean(SerdeRegistry), Argument.of(SourceGenRenamedPropertiesBean), true)
        assertRegistrySelection(runtimeContext.getBean(SerdeRegistry), Argument.of(SourceGenRenamedPropertiesBean), false)
        generatedJson == '{"first_name":"Ada","n":42,"email":"ada@example.com"}'
        generatedJson == runtimeJson
        parse(generatedJson) == parse(databindJson)

        cleanup:
        generatedContext.close()
        runtimeContext.close()
    }

    void 'test renamed record components serialize identically on every stack'() {
        given:
        def generatedContext = ApplicationContext.run()
        def runtimeContext = runtimeContext()
        def generatedMapper = generatedContext.getBean(JsonMapper)
        def runtimeMapper = runtimeContext.getBean(JsonMapper)
        def databindMapper = DatabindJsonMapper.builder().build()
        def record = new SourceGenRenamedPropertiesRecord('Ada', 42, 'ada@example.com')

        when:
        String generatedJson = serializeToString(generatedMapper, record)
        String runtimeJson = serializeToString(runtimeMapper, record)
        String databindJson = databindMapper.writeValueAsString(record)

        then:
        assertRegistrySelection(generatedContext.getBean(SerdeRegistry), Argument.of(SourceGenRenamedPropertiesRecord), true)
        assertRegistrySelection(runtimeContext.getBean(SerdeRegistry), Argument.of(SourceGenRenamedPropertiesRecord), false)
        generatedJson == '{"first_name":"Ada","n":42,"email":"ada@example.com"}'
        generatedJson == runtimeJson
        parse(generatedJson) == parse(databindJson)

        cleanup:
        generatedContext.close()
        runtimeContext.close()
    }

    void 'test renamed properties deserialize from the serialized name on every stack'() {
        given:
        def generatedContext = ApplicationContext.run()
        def runtimeContext = runtimeContext()
        def generatedMapper = generatedContext.getBean(JsonMapper)
        def runtimeMapper = runtimeContext.getBean(JsonMapper)
        def databindMapper = DatabindJsonMapper.builder().build()
        String json = '{"first_name":"Ada","n":42,"email":"ada@example.com"}'

        when:
        def generatedBean = generatedMapper.readValue(json, SourceGenRenamedPropertiesBean)
        def runtimeBean = runtimeMapper.readValue(json, SourceGenRenamedPropertiesBean)
        def databindBean = databindMapper.readValue(json, SourceGenRenamedPropertiesBean)
        def generatedRecord = generatedMapper.readValue(json, SourceGenRenamedPropertiesRecord)
        def runtimeRecord = runtimeMapper.readValue(json, SourceGenRenamedPropertiesRecord)
        def databindRecord = databindMapper.readValue(json, SourceGenRenamedPropertiesRecord)

        then:
        [generatedBean, runtimeBean, databindBean].every { it.firstName == 'Ada' && it.count == 42 && it.email == 'ada@example.com' }
        generatedRecord == new SourceGenRenamedPropertiesRecord('Ada', 42, 'ada@example.com')
        generatedRecord == runtimeRecord
        generatedRecord == databindRecord

        cleanup:
        generatedContext.close()
        runtimeContext.close()
    }

    void 'test the original property name is unknown to the generated and the runtime deserializer'() {
        given:
        def generatedContext = ApplicationContext.run(['micronaut.serde.deserialization.ignore-unknown': false])
        def runtimeContext = runtimeContext(['micronaut.serde.deserialization.ignore-unknown': false])
        def generatedMapper = generatedContext.getBean(JsonMapper)
        def runtimeMapper = runtimeContext.getBean(JsonMapper)
        String json = '{"firstName":"Ada","count":42,"email":"ada@example.com"}'

        when:
        generatedMapper.readValue(json, SourceGenRenamedPropertiesBean)

        then:
        def generatedFailure = thrown(SerdeException)
        generatedFailure.message.contains('Unknown property [firstName]')

        when:
        runtimeMapper.readValue(json, SourceGenRenamedPropertiesBean)

        then:
        def runtimeFailure = thrown(SerdeException)
        runtimeFailure.message.contains('Unknown property [firstName]')

        when:
        generatedMapper.readValue(json, SourceGenRenamedPropertiesRecord)

        then:
        def generatedRecordFailure = thrown(SerdeException)
        generatedRecordFailure.message.contains('Unknown property [firstName]')

        when:
        runtimeMapper.readValue(json, SourceGenRenamedPropertiesRecord)

        then:
        def runtimeRecordFailure = thrown(SerdeException)
        runtimeRecordFailure.message.contains('Unknown property [firstName]')

        cleanup:
        generatedContext.close()
        runtimeContext.close()
    }

    void 'test a compile-time naming strategy is baked into the generated serdes'() {
        given:
        def generatedContext = ApplicationContext.run()
        def runtimeContext = runtimeContext()
        def generatedMapper = generatedContext.getBean(JsonMapper)
        def runtimeMapper = runtimeContext.getBean(JsonMapper)
        Argument argument = Argument.of(SourceGenSnakeCaseNamingRecord)
        def record = new SourceGenSnakeCaseNamingRecord('Ada', 'Lovelace', 1815)
        String json = '{"first_name":"Ada","last_name":"Lovelace","zip_code":1815}'

        expect:
        assertRegistrySelection(generatedContext.getBean(SerdeRegistry), argument, true)
        assertRegistrySelection(runtimeContext.getBean(SerdeRegistry), argument, false)
        serializeToString(generatedMapper, record) == json
        serializeToString(runtimeMapper, record) == json
        generatedMapper.readValue(json, argument) == record
        runtimeMapper.readValue(json, argument) == record

        cleanup:
        generatedContext.close()
        runtimeContext.close()
    }

    /**
     * Jackson Databind orders bean properties alphabetically by default, so the comparison is on the
     * parsed document rather than on the exact text.
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
