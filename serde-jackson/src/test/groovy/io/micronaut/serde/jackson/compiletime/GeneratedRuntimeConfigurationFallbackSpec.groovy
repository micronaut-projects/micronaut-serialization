package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.jackson.JsonCompileSpec

/**
 * Property names and property order are resolved at build time. Runtime configuration that changes
 * either is detected once when the specific serde is created and routes to the runtime object serde.
 */
class GeneratedRuntimeConfigurationFallbackSpec extends JsonCompileSpec {

    void 'test generated serdes are used with the default configuration'() {
        given:
        def context = ApplicationContext.run()
        def mapper = context.getBean(JsonMapper)
        Argument argument = Argument.of(SourceGenRuntimeConfigurationShape)

        expect:
        assertRegistrySelection(context.getBean(SerdeRegistry), argument, true, true)
        serializeToString(mapper, new SourceGenRuntimeConfigurationShape('Lovelace', 'Ada', 1815)) == '{"lastName":"Lovelace","firstName":"Ada","zipCode":1815}'
        mapper.readValue('{"lastName":"Lovelace","firstName":"Ada","zipCode":1815}', argument) == new SourceGenRuntimeConfigurationShape('Lovelace', 'Ada', 1815)

        cleanup:
        context.close()
    }

    void 'test a runtime property naming strategy routes both directions to the runtime serde'() {
        given:
        def context = ApplicationContext.run(['micronaut.serde.property-naming-strategy': 'SNAKE_CASE'])
        def mapper = context.getBean(JsonMapper)
        Argument argument = Argument.of(SourceGenRuntimeConfigurationShape)

        expect:
        assertRegistrySelection(context.getBean(SerdeRegistry), argument, false, false)
        serializeToString(mapper, new SourceGenRuntimeConfigurationShape('Lovelace', 'Ada', 1815)) == '{"last_name":"Lovelace","first_name":"Ada","zip_code":1815}'
        mapper.readValue('{"last_name":"Lovelace","first_name":"Ada","zip_code":1815}', argument) == new SourceGenRuntimeConfigurationShape('Lovelace', 'Ada', 1815)

        cleanup:
        context.close()
    }

    void 'test alphabetical property sorting routes serialization to the runtime serializer'() {
        given:
        def context = ApplicationContext.run(['micronaut.serde.serialization.sort-properties-alphabetically': true])
        def mapper = context.getBean(JsonMapper)
        Argument argument = Argument.of(SourceGenRuntimeConfigurationShape)

        expect:
        assertRegistrySelection(context.getBean(SerdeRegistry), argument, false, true)
        serializeToString(mapper, new SourceGenRuntimeConfigurationShape('Lovelace', 'Ada', 1815)) == '{"firstName":"Ada","lastName":"Lovelace","zipCode":1815}'
        mapper.readValue('{"firstName":"Ada","lastName":"Lovelace","zipCode":1815}', argument) == new SourceGenRuntimeConfigurationShape('Lovelace', 'Ada', 1815)

        cleanup:
        context.close()
    }

    private static void assertRegistrySelection(SerdeRegistry registry,
                                                Argument argument,
                                                boolean serializerGenerated,
                                                boolean deserializerGenerated) {
        Serializer serializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument)
        Deserializer deserializer = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)
        assert (serializer.class.name == generatedClassName(argument.type, 'Serializer')) == serializerGenerated
        assert (deserializer.class.name == generatedClassName(argument.type, 'Deserializer')) == deserializerGenerated
    }

    private static String generatedClassName(Class<?> type, String suffix) {
        String packageName = type.package.name
        String localName = type.name.substring(packageName.length() + 1)
        "${packageName}.Serde${localName.replace('.', '_').replace('$', '_')}${suffix}"
    }
}
