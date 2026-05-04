package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.serde.jackson.JsonCompileSpec

class CompileTimeDeserializerBehaviorSpec extends JsonCompileSpec {

    void 'test primitive boolean missing constructor properties and nullable values match runtime deserializer'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        Class<?> generatedType = SourceGenGeneratedConstructorDefaults
        Class<?> runtimeType = SourceGenRuntimeConstructorDefaults
        def introspections = context.getBean(SerdeIntrospections)
        def generatedMetadata = introspections.getDeserializableIntrospection(Argument.of(generatedType)).annotationMetadata
        String deserializerClassName = generatedMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
        String deserializerSource = generatedTestSource(deserializerClassName)

        expect:
        deserializerSource.contains('boolean propertyValue1 = false;')
        deserializerSource.contains('propertyValue1 = objectDecoder.decodeBoolean();')
        !deserializerSource.contains('propertyValue1 = objectDecoder.decodeBooleanNullable();')
        !deserializerSource.contains('component0')
        deserializerSource.indexOf('if (!seenProperty') > -1
        deserializerSource.indexOf('if (!seenProperty') < deserializerSource.indexOf('return new io.micronaut.serde.jackson.compiletime.SourceGenGeneratedConstructorDefaults')

        when:
        def generatedMissingName = jsonMapper.readValue('{"active":true}', Argument.of(generatedType))
        def runtimeMissingName = jsonMapper.readValue('{"active":true}', Argument.of(runtimeType))
        def generatedMissingPrimitiveValues = jsonMapper.readValue('{"name":"Ada"}', Argument.of(generatedType))
        def runtimeMissingPrimitiveValues = jsonMapper.readValue('{"name":"Ada"}', Argument.of(runtimeType))
        def generatedMissingNullableValues = jsonMapper.readValue('{"name":"Ada","active":true,"count":1}', Argument.of(generatedType))
        def runtimeMissingNullableValues = jsonMapper.readValue('{"name":"Ada","active":true,"count":1}', Argument.of(runtimeType))
        def generatedNullValues = jsonMapper.readValue('{"name":null,"active":null,"count":null,"nullableName":null,"nullableActive":null}', Argument.of(generatedType))
        def runtimeNullValues = jsonMapper.readValue('{"name":null,"active":null,"count":null,"nullableName":null,"nullableActive":null}', Argument.of(runtimeType))
        def generatedMissingAll = jsonMapper.readValue('{}', Argument.of(generatedType))
        def runtimeMissingAll = jsonMapper.readValue('{}', Argument.of(runtimeType))

        then:
        assertPropertiesEqual(generatedMissingName, runtimeMissingName)
        assertPropertiesEqual(generatedMissingPrimitiveValues, runtimeMissingPrimitiveValues)
        assertPropertiesEqual(generatedMissingNullableValues, runtimeMissingNullableValues)
        assertPropertiesEqual(generatedNullValues, runtimeNullValues)
        assertPropertiesEqual(generatedMissingAll, runtimeMissingAll)

        cleanup:
        context.close()
    }

    private static void assertPropertiesEqual(Object generated, Object runtime) {
        assert invokeDeclared(generated, 'name') == invokeDeclared(runtime, 'name')
        assert invokeDeclared(generated, 'active') == invokeDeclared(runtime, 'active')
        assert invokeDeclared(generated, 'count') == invokeDeclared(runtime, 'count')
        assert invokeDeclared(generated, 'nullableName') == invokeDeclared(runtime, 'nullableName')
        assert invokeDeclared(generated, 'nullableActive') == invokeDeclared(runtime, 'nullableActive')
    }

    private static Object invokeDeclared(Object target, String methodName) {
        def method = target.getClass().getDeclaredMethod(methodName)
        method.setAccessible(true)
        method.invoke(target)
    }
}
