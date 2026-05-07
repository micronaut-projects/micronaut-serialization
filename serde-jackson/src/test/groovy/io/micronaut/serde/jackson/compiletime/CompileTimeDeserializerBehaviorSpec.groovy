package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.serde.jackson.JsonCompileSpec

class CompileTimeDeserializerBehaviorSpec extends JsonCompileSpec {

    void 'test generated record deserializer source handles primitive booleans and missing constructor properties'() {
        given:
        def context = ApplicationContext.run()
        Class<?> generatedType = SourceGenGeneratedConstructorDefaults
        def introspections = context.getBean(SerdeIntrospections)
        def generatedMetadata = introspections.getDeserializableIntrospection(Argument.of(generatedType)).annotationMetadata
        String deserializerClassName = generatedMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
        String deserializerSource = generatedTestSource(deserializerClassName)

        expect:
        deserializerSource.contains('boolean propertyValue1 = false;')
        deserializerSource.contains('propertyValue1 = objectDecoder.decodeBoolean();')
        !deserializerSource.contains('propertyValue1 = objectDecoder.decodeBooleanNullable();')
        !deserializerSource.contains('component0')
        !deserializerSource.contains('if (!seenProperty')
        !deserializerSource.contains('duplicateProperty')

        cleanup:
        context.close()
    }

    void 'test generated record deserializer is selected for constructor defaults'() {
        given:
        def context = ApplicationContext.run()
        def registry = context.getBean(SerdeRegistry)

        expect:
        assertGeneratedDeserializer(registry, SourceGenGeneratedConstructorDefaults.class)

        cleanup:
        context.close()
    }

    void 'test generated bean deserializer source handles primitive booleans without nullable decode'() {
        given:
        def context = ApplicationContext.run()
        Class<?> generatedType = SourceGenGeneratedPropertyDefaults
        def introspections = context.getBean(SerdeIntrospections)
        def generatedMetadata = introspections.getDeserializableIntrospection(Argument.of(generatedType)).annotationMetadata
        String deserializerClassName = generatedMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
        String deserializerSource = generatedTestSource(deserializerClassName)

        expect:
        !deserializerSource.contains('boolean value1 = false;')
        !deserializerSource.contains('value1 = objectDecoder.decodeBoolean();')
        deserializerSource.contains('bean.setActive(objectDecoder.decodeBoolean());')
        !deserializerSource.contains('value1 = objectDecoder.decodeBooleanNullable();')
        !deserializerSource.contains('duplicateProperty')

        cleanup:
        context.close()
    }

    void 'test generated bean deserializer is selected for property defaults'() {
        given:
        def context = ApplicationContext.run()
        def registry = context.getBean(SerdeRegistry)

        expect:
        assertGeneratedDeserializer(registry, SourceGenGeneratedPropertyDefaults.class)

        cleanup:
        context.close()
    }

    void 'test generated record deserializer matches runtime for primitive nullable and missing properties'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)

        expect:
        assertReadMatches(jsonMapper, SourceGenGeneratedConstructorDefaults, SourceGenRuntimeConstructorDefaults, json)

        cleanup:
        context.close()

        where:
        scenario                    | json
        'full input'                | '{"name":"Ada","active":true,"count":42,"nullableName":"Grace","nullableActive":false}'
        'missing reference'         | '{"active":true,"count":42,"nullableName":"Grace","nullableActive":false}'
        'missing primitives'        | '{"name":"Ada","nullableName":"Grace","nullableActive":false}'
        'missing nullable values'   | '{"name":"Ada","active":true,"count":42}'
        'explicit null values'      | '{"name":null,"active":null,"count":null,"nullableName":null,"nullableActive":null}'
        'missing all values'        | '{}'
    }

    void 'test generated bean deserializer matches runtime for primitive nullable and missing properties'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)

        expect:
        assertReadMatches(jsonMapper, SourceGenGeneratedPropertyDefaults, SourceGenRuntimePropertyDefaults, json)

        cleanup:
        context.close()

        where:
        scenario                    | json
        'full input'                | '{"name":"Ada","active":false,"count":42,"nullableName":"Grace","nullableActive":false}'
        'missing reference'         | '{"active":false,"count":42,"nullableName":"Grace","nullableActive":false}'
        'missing primitives'        | '{"name":"Ada","nullableName":"Grace","nullableActive":false}'
        'missing nullable values'   | '{"name":"Ada","active":false,"count":42}'
        'explicit null values'      | '{"name":null,"active":null,"count":null,"nullableName":null,"nullableActive":null}'
        'missing all values'        | '{}'
    }

    void 'test generated record duplicate property handling matches runtime deserializer'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)

        expect:
        assertReadMatches(jsonMapper, SourceGenGeneratedConstructorDefaults, SourceGenRuntimeConstructorDefaults, json)

        cleanup:
        context.close()

        where:
        scenario                       | json
        'duplicate reference'          | '{"name":"Ada","name":"Grace","active":true,"count":1}'
        'reference null first'         | '{"name":null,"name":"Grace","active":true,"count":1}'
        'duplicate primitive boolean'  | '{"name":"Ada","active":true,"active":false,"count":1}'
        'primitive boolean null first' | '{"name":"Ada","active":null,"active":true,"count":1}'
        'duplicate primitive int'      | '{"name":"Ada","active":true,"count":1,"count":2}'
        'primitive int null first'     | '{"name":"Ada","active":true,"count":null,"count":1}'
        'primitive boolean null last'  | '{"name":"Ada","active":true,"active":null,"count":1}'
        'primitive int null last'      | '{"name":"Ada","active":true,"count":1,"count":null}'
        'duplicate nullable reference' | '{"name":"Ada","active":true,"count":1,"nullableName":"Grace","nullableName":"Lovelace"}'
        'nullable reference null first' | '{"name":"Ada","active":true,"count":1,"nullableName":null,"nullableName":"Grace"}'
        'nullable reference null last' | '{"name":"Ada","active":true,"count":1,"nullableName":"Grace","nullableName":null}'
        'duplicate nullable boolean'   | '{"name":"Ada","active":true,"count":1,"nullableActive":true,"nullableActive":false}'
        'nullable boolean null first'  | '{"name":"Ada","active":true,"count":1,"nullableActive":null,"nullableActive":true}'
        'nullable boolean null last'   | '{"name":"Ada","active":true,"count":1,"nullableActive":true,"nullableActive":null}'
    }

    void 'test generated bean duplicate property handling matches runtime deserializer'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)

        expect:
        assertReadMatches(jsonMapper, SourceGenGeneratedPropertyDefaults, SourceGenRuntimePropertyDefaults, json)

        cleanup:
        context.close()

        where:
        scenario                       | json
        'duplicate reference'          | '{"name":"Ada","name":"Grace","active":true,"count":1}'
        'reference null first'         | '{"name":null,"name":"Grace","active":true,"count":1}'
        'duplicate primitive boolean'  | '{"name":"Ada","active":true,"active":false,"count":1}'
        'primitive boolean null first' | '{"name":"Ada","active":null,"active":true,"count":1}'
        'duplicate primitive int'      | '{"name":"Ada","active":true,"count":1,"count":2}'
        'primitive int null first'     | '{"name":"Ada","active":true,"count":null,"count":1}'
        'primitive boolean null last'  | '{"name":"Ada","active":true,"active":null,"count":1}'
        'primitive int null last'      | '{"name":"Ada","active":true,"count":1,"count":null}'
        'duplicate nullable reference' | '{"name":"Ada","active":true,"count":1,"nullableName":"Grace","nullableName":"Lovelace"}'
        'nullable reference null first' | '{"name":"Ada","active":true,"count":1,"nullableName":null,"nullableName":"Grace"}'
        'nullable reference null last' | '{"name":"Ada","active":true,"count":1,"nullableName":"Grace","nullableName":null}'
        'duplicate nullable boolean'   | '{"name":"Ada","active":true,"count":1,"nullableActive":true,"nullableActive":false}'
        'nullable boolean null first'  | '{"name":"Ada","active":true,"count":1,"nullableActive":null,"nullableActive":true}'
        'nullable boolean null last'   | '{"name":"Ada","active":true,"count":1,"nullableActive":true,"nullableActive":null}'
    }

    private static void assertPropertiesEqual(Object generated, Object runtime) {
        assert propertyValue(generated, 'name') == propertyValue(runtime, 'name')
        assert propertyValue(generated, 'active') == propertyValue(runtime, 'active')
        assert propertyValue(generated, 'count') == propertyValue(runtime, 'count')
        assert propertyValue(generated, 'nullableName') == propertyValue(runtime, 'nullableName')
        assert propertyValue(generated, 'nullableActive') == propertyValue(runtime, 'nullableActive')
    }

    private static void assertReadMatches(JsonMapper jsonMapper,
                                          Class<?> generatedType,
                                          Class<?> runtimeType,
                                          String json) {
        def generated = jsonMapper.readValue(json, Argument.of(generatedType))
        def runtime = jsonMapper.readValue(json, Argument.of(runtimeType))
        assertPropertiesEqual(generated, runtime)
    }

    private static void assertGeneratedDeserializer(SerdeRegistry registry, Class<?> type) {
        Argument argument = Argument.of(type)
        Deserializer deserializer = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)
        assert deserializer.class.name == generatedClassName(type, 'Deserializer')
    }

    private static String generatedClassName(Class<?> type, String suffix) {
        "${type.package.name}.Serde${type.simpleName}${suffix}"
    }

    private static Object propertyValue(Object target, String propertyName) {
        def recordMethod = findDeclaredMethod(target, propertyName)
        if (recordMethod != null) {
            return invoke(recordMethod, target)
        }
        def getter = findDeclaredMethod(target, 'get' + propertyName.capitalize())
        if (getter != null) {
            return invoke(getter, target)
        }
        def booleanGetter = findDeclaredMethod(target, 'is' + propertyName.capitalize())
        if (booleanGetter != null) {
            return invoke(booleanGetter, target)
        }
        throw new IllegalArgumentException("No readable property '${propertyName}' on ${target.class.name}")
    }

    private static def findDeclaredMethod(Object target, String methodName) {
        try {
            return target.getClass().getDeclaredMethod(methodName)
        } catch (NoSuchMethodException ignored) {
            return null
        }
    }

    private static Object invoke(def method, Object target) {
        method.setAccessible(true)
        method.invoke(target)
    }
}
