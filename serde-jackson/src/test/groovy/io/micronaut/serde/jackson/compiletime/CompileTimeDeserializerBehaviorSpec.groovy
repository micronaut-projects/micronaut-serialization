package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.Decoder
import io.micronaut.serde.DelegatingDecoder
import io.micronaut.serde.Deserializer
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.jackson.JsonCompileSpec
import io.micronaut.serde.jackson.JacksonDecoder
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper as DatabindJsonMapper
import tools.jackson.core.json.JsonFactoryBuilder

import java.io.IOException

class CompileTimeDeserializerBehaviorSpec extends JsonCompileSpec {

    void 'test generated record deserializer source handles nullable primitive booleans and missing constructor properties'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.fail-on-null-for-primitives': false
        ])
        Class<?> generatedType = SourceGenGeneratedConstructorDefaults
        def introspections = context.getBean(SerdeIntrospections)
        def generatedMetadata = introspections.getDeserializableIntrospection(Argument.of(generatedType)).annotationMetadata
        String deserializerClassName = generatedMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
        String deserializerSource = generatedTestSource(deserializerClassName)

        expect:
        deserializerSource.contains('boolean propertyValue1 = false;')
        deserializerSource.contains('if (this.failOnNullForPrimitives)')
        deserializerSource.contains('objectDecoder.decodeBooleanNullable()')
        deserializerSource.contains('objectDecoder.decodeNull()')
        deserializerSource.contains('objectDecoder.decodeBoolean()')
        deserializerSource.contains('propertyValue1 = objectDecoder.decodeBoolean();')
        deserializerSource.contains('failOnNullForPrimitives(context)')

        cleanup:
        context.close()
    }

    void 'test generated record deserializer is selected for constructor defaults'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.fail-on-null-for-primitives': false
        ])
        def registry = context.getBean(SerdeRegistry)

        expect:
        assertGeneratedDeserializer(registry, SourceGenGeneratedConstructorDefaults.class)

        cleanup:
        context.close()
    }

    void 'test generated bean deserializer source handles nullable primitive booleans'() {
        given:
        def context = ApplicationContext.run()
        Class<?> generatedType = SourceGenGeneratedPropertyDefaults
        def introspections = context.getBean(SerdeIntrospections)
        def generatedMetadata = introspections.getDeserializableIntrospection(Argument.of(generatedType)).annotationMetadata
        String deserializerClassName = generatedMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
        String deserializerSource = generatedTestSource(deserializerClassName)

        expect:
        deserializerSource.contains('if (this.failOnNullForPrimitives)')
        deserializerSource.contains('objectDecoder.decodeBooleanNullable()')
        deserializerSource.contains('objectDecoder.decodeNull()')
        deserializerSource.contains('objectDecoder.decodeBoolean()')
        deserializerSource.contains('failOnNullForPrimitives(context)')
        deserializerSource.contains('bean.setActive(objectDecoder.decodeBoolean());')

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
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.fail-on-null-for-primitives': false
        ])
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
        'explicit reference null values' | '{"name":null,"active":true,"count":42,"nullableName":null,"nullableActive":null}'
        'explicit primitive boolean null' | '{"name":"Ada","active":null,"count":42}'
        'explicit primitive int null' | '{"name":"Ada","active":true,"count":null}'
        'missing all values'        | '{}'
    }

    void 'test generated bean deserializer matches runtime for primitive nullable and missing properties'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.fail-on-null-for-primitives': false
        ])
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
        'explicit reference null values' | '{"name":null,"active":false,"count":42,"nullableName":null,"nullableActive":null}'
        'explicit primitive boolean null' | '{"name":"Ada","active":null,"count":42}'
        'explicit primitive int null' | '{"name":"Ada","active":false,"count":null}'
        'missing all values'        | '{}'
    }

    void 'test generated primitive decoder only uses nullable scalar decode when primitive nulls fail'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.fail-on-null-for-primitives': failOnNullForPrimitives
        ])
        def registry = context.getBean(SerdeRegistry)
        Argument argument = Argument.of(SourceGenGeneratedPropertyDefaults)
        def decoderContext = registry.newDecoderContext(Object)
        Deserializer deserializer = registry.findDeserializer(argument).createSpecific(decoderContext, argument)
        def decoder = trackingDecoder(json, !failOnNullForPrimitives)

        when:
        SourceGenGeneratedPropertyDefaults value = deserializer.deserialize(decoder, decoderContext, argument)

        then:
        value.active == expectedActive
        value.count == expectedCount
        decoder.nullablePrimitiveDecodeCalls == expectedNullablePrimitiveDecodeCalls
        decoder.primitiveDecodeCalls == expectedPrimitiveDecodeCalls
        decoder.decodeNullCalls == expectedDecodeNullCalls

        cleanup:
        context.close()

        where:
        failOnNullForPrimitives | json                            | expectedActive | expectedCount | expectedNullablePrimitiveDecodeCalls | expectedPrimitiveDecodeCalls | expectedDecodeNullCalls
        false                   | '{"active":true,"count":12}'    | true           | 12            | 0                                    | 2                            | 2
        false                   | '{"active":null,"count":null}'  | true           | 7             | 0                                    | 0                            | 2
        true                    | '{"active":true,"count":12}'    | true           | 12            | 2                                    | 0                            | 0
    }

    void 'test generated field default deserializer covers primitive and unknown branches'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)

        when:
        SourceGenGeneratedFieldDefaults decoded = jsonMapper.readValue(
            '{"ignored":{"nested":true},"active":false,"count":42}',
            Argument.of(SourceGenGeneratedFieldDefaults)
        )

        then:
        assertGeneratedDeserializer(registry, SourceGenGeneratedFieldDefaults.class)
        decoded.name == 'default-name'
        !decoded.active
        decoded.count == 42
        decoded.nullableName == 'default-nullable-name'
        decoded.nullableActive

        cleanup:
        context.close()
    }

    void 'test generated field default deserializer reports duplicate unknown and primitive null exceptions'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.ignore-unknown': false
        ])
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)

        expect:
        assertGeneratedDeserializer(registry, SourceGenGeneratedFieldDefaults.class)
        assertSerdeFailure(jsonMapper, SourceGenGeneratedFieldDefaults, json, messageFragment, pathFragment)

        cleanup:
        context.close()

        where:
        scenario                    | json                                                      | messageFragment                       | pathFragment
        'duplicate name'            | '{"name":"Ada","name":"Bob"}'                             | 'Duplicate property [name]'           | '["name"]'
        'duplicate active'          | '{"active":true,"active":false}'                          | 'Duplicate property [active]'         | '["active"]'
        'duplicate count'           | '{"count":1,"count":2}'                                   | 'Duplicate property [count]'          | '["count"]'
        'duplicate nullable name'   | '{"nullableName":"Ada","nullableName":"Bob"}'             | 'Duplicate property [nullableName]'   | '["nullableName"]'
        'duplicate nullable active' | '{"nullableActive":true,"nullableActive":false}'          | 'Duplicate property [nullableActive]' | '["nullableActive"]'
        'unknown'                   | '{"extra":1}'                                             | 'Unknown property [extra]'            | '["extra"]'
        'null boolean'              | '{"active":null}'                                         | 'Non-null property'                   | '["active"]'
        'null int'                  | '{"count":null}'                                          | 'Non-null property'                   | '["count"]'
        'invalid name'              | '{"name":{"nested":true}}'                                | null                                  | '["name"]'
        'invalid nullable boolean'  | '{"nullableActive":{"nested":true}}'                      | null                                  | '["nullableActive"]'
    }

    void 'test generated runtime field default deserializer reports duplicate unknown and primitive null exceptions'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.ignore-unknown': false
        ])
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)

        expect:
        assertGeneratedDeserializer(registry, SourceGenRuntimeFieldDefaults.class)
        assertSerdeFailure(jsonMapper, SourceGenRuntimeFieldDefaults, json, messageFragment, pathFragment)

        cleanup:
        context.close()

        where:
        scenario                    | json                                                      | messageFragment                       | pathFragment
        'duplicate name'            | '{"name":"Ada","name":"Bob"}'                             | 'Duplicate property [name]'           | '["name"]'
        'duplicate active'          | '{"active":true,"active":false}'                          | 'Duplicate property [active]'         | '["active"]'
        'duplicate count'           | '{"count":1,"count":2}'                                   | 'Duplicate property [count]'          | '["count"]'
        'duplicate nullable name'   | '{"nullableName":"Ada","nullableName":"Bob"}'             | 'Duplicate property [nullableName]'   | '["nullableName"]'
        'duplicate nullable active' | '{"nullableActive":true,"nullableActive":false}'          | 'Duplicate property [nullableActive]' | '["nullableActive"]'
        'unknown'                   | '{"extra":1}'                                             | 'Unknown property [extra]'            | '["extra"]'
        'null boolean'              | '{"active":null}'                                         | 'Non-null property'                   | '["active"]'
        'null int'                  | '{"count":null}'                                          | 'Non-null property'                   | '["count"]'
        'invalid name'              | '{"name":{"nested":true}}'                                | null                                  | '["name"]'
        'invalid nullable boolean'  | '{"nullableActive":{"nested":true}}'                      | null                                  | '["nullableActive"]'
    }

    void 'test generated shape deserializer covers primitive null default and unknown skip'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.fail-on-null-for-primitives': false
        ])
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)

        when:
        SourceGenGeneratedShape decoded = jsonMapper.readValue(
            '{"ignored":{"nested":true},"name":"Ada","count":null}',
            Argument.of(SourceGenGeneratedShape)
        )

        then:
        assertGeneratedDeserializer(registry, SourceGenGeneratedShape.class)
        decoded.name() == 'Ada'
        decoded.count() == 0

        cleanup:
        context.close()
    }

    void 'test generated shape deserializer reports duplicate unknown and primitive null exceptions'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.ignore-unknown': false
        ])
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)

        expect:
        assertGeneratedDeserializer(registry, SourceGenGeneratedShape.class)
        assertSerdeFailure(jsonMapper, SourceGenGeneratedShape, json, messageFragment, pathFragment)

        cleanup:
        context.close()

        where:
        scenario         | json                    | messageFragment              | pathFragment
        'duplicate name' | '{"name":"Ada","name":"Bob"}' | 'Duplicate property [name]'  | '["name"]'
        'duplicate count' | '{"count":1,"count":2}' | 'Duplicate property [count]' | '["count"]'
        'unknown'        | '{"unknown":1}'         | 'Unknown property [unknown]' | '["unknown"]'
        'null int'       | '{"name":"Ada","count":null}' | 'Non-null property'     | '["count"]'
        'invalid name'   | '{"name":{"nested":true}}' | null                       | '["name"]'
        'invalid int'    | '{"count":{"nested":true}}' | null                      | '["count"]'
    }

    void 'test generated dispatch bean deserializer covers primitive null defaults and unknown skip'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.fail-on-null-for-primitives': false
        ])
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)

        when:
        SourceGenSmallDispatchBean decoded = jsonMapper.readValue(
            '{"ignored":{"nested":true},"a":"x","b":7,"c":true}',
            Argument.of(SourceGenSmallDispatchBean)
        )
        SourceGenSmallDispatchBean decodedNulls = jsonMapper.readValue(
            '{"a":"x","b":null,"c":null}',
            Argument.of(SourceGenSmallDispatchBean)
        )

        then:
        assertGeneratedDeserializer(registry, SourceGenSmallDispatchBean.class)
        decoded.a == 'x'
        decoded.b == 7
        decoded.c
        decodedNulls.a == 'x'
        decodedNulls.b == 0
        !decodedNulls.c

        cleanup:
        context.close()
    }

    void 'test generated dispatch bean deserializer reports duplicate unknown and primitive null exceptions'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.ignore-unknown': false
        ])
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)

        expect:
        assertGeneratedDeserializer(registry, SourceGenSmallDispatchBean.class)
        assertSerdeFailure(jsonMapper, SourceGenSmallDispatchBean, json, messageFragment, pathFragment)

        cleanup:
        context.close()

        where:
        scenario          | json                    | messageFragment               | pathFragment
        'duplicate a'     | '{"a":"x","a":"y"}'     | 'Duplicate property [a]'      | '["a"]'
        'duplicate b'     | '{"b":1,"b":2}'         | 'Duplicate property [b]'      | '["b"]'
        'duplicate c'     | '{"c":true,"c":false}'  | 'Duplicate property [c]'      | '["c"]'
        'unknown'         | '{"unknown":1}'         | 'Unknown property [unknown]'  | '["unknown"]'
        'null int'        | '{"a":"x","b":null}'    | 'Non-null property'           | '["b"]'
        'null boolean'    | '{"a":"x","c":null}'    | 'Non-null property'           | '["c"]'
        'invalid string'  | '{"a":{"nested":true}}' | null                          | '["a"]'
        'invalid int'     | '{"b":{"nested":true}}' | null                          | '["b"]'
        'invalid boolean' | '{"c":{"nested":true}}' | null                          | '["c"]'
    }

    void 'test generated dispatch record deserializer covers primitive null defaults and unknown skip'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.fail-on-null-for-primitives': false
        ])
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)

        when:
        SourceGenSmallDispatchRecord decoded = jsonMapper.readValue(
            '{"ignored":{"nested":true},"a":"x","b":null,"c":null}',
            Argument.of(SourceGenSmallDispatchRecord)
        )

        then:
        assertGeneratedDeserializer(registry, SourceGenSmallDispatchRecord.class)
        decoded.a() == 'x'
        decoded.b() == 0
        !decoded.c()

        cleanup:
        context.close()
    }

    void 'test generated dispatch record deserializer reports duplicate unknown and primitive null exceptions'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.ignore-unknown': false
        ])
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)

        expect:
        assertGeneratedDeserializer(registry, SourceGenSmallDispatchRecord.class)
        assertSerdeFailure(jsonMapper, SourceGenSmallDispatchRecord, json, messageFragment, pathFragment)

        cleanup:
        context.close()

        where:
        scenario          | json                    | messageFragment               | pathFragment
        'duplicate a'     | '{"a":"x","a":"y"}'     | 'Duplicate property [a]'      | '["a"]'
        'duplicate b'     | '{"b":1,"b":2}'         | 'Duplicate property [b]'      | '["b"]'
        'duplicate c'     | '{"c":true,"c":false}'  | 'Duplicate property [c]'      | '["c"]'
        'unknown'         | '{"unknown":1}'         | 'Unknown property [unknown]'  | '["unknown"]'
        'null int'        | '{"a":"x","b":null}'    | 'Non-null property'           | '["b"]'
        'null boolean'    | '{"a":"x","c":null}'    | 'Non-null property'           | '["c"]'
        'invalid string'  | '{"a":{"nested":true}}' | null                          | '["a"]'
        'invalid int'     | '{"b":{"nested":true}}' | null                          | '["b"]'
        'invalid boolean' | '{"c":{"nested":true}}' | null                          | '["c"]'
    }

    void 'test generated boundary and large dispatch deserializers cover primitive null defaults and unknown skip'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.fail-on-null-for-primitives': false
        ])
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)

        when:
        def decoded = jsonMapper.readValue(json, Argument.of(type))

        then:
        assertGeneratedDeserializer(registry, type)
        propertyValue(decoded, 'a') == 'x'
        propertyValue(decoded, 'b') == 0
        !propertyValue(decoded, 'c')
        propertyValue(decoded, 'd') == 0L
        propertyValue(decoded, 'e') == 0d
        if (hasProperty(decoded, 'f')) {
            assert propertyValue(decoded, 'f') == null
        }

        cleanup:
        context.close()

        where:
        type                                  | json
        SourceGenBoundaryDispatchBean.class  | '{"ignored":{"nested":true},"a":"x","b":null,"c":null,"d":null,"e":null}'
        SourceGenBoundaryDispatchRecord.class | '{"ignored":{"nested":true},"a":"x","b":null,"c":null,"d":null,"e":null}'
        SourceGenLargeDispatchBean.class     | '{"ignored":{"nested":true},"a":"x","b":null,"c":null,"d":null,"e":null,"f":null}'
        SourceGenLargeDispatchRecord.class   | '{"ignored":{"nested":true},"a":"x","b":null,"c":null,"d":null,"e":null,"f":null}'
    }

    void 'test generated boundary and large dispatch deserializers report branch exceptions'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.ignore-unknown': false
        ])
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)

        expect:
        assertGeneratedDeserializer(registry, type)
        assertSerdeFailure(jsonMapper, type, json, messageFragment, pathFragment)

        cleanup:
        context.close()

        where:
        type                                   | json                            | messageFragment              | pathFragment
        SourceGenBoundaryDispatchBean.class   | '{"d":1,"d":2}'                 | 'Duplicate property [d]'     | '["d"]'
        SourceGenBoundaryDispatchBean.class   | '{"e":1.0,"e":2.0}'             | 'Duplicate property [e]'     | '["e"]'
        SourceGenBoundaryDispatchBean.class   | '{"d":null}'                    | 'Non-null property'          | '["d"]'
        SourceGenBoundaryDispatchBean.class   | '{"e":{"nested":true}}'         | null                         | '["e"]'
        SourceGenBoundaryDispatchBean.class   | '{"unknown":1}'                 | 'Unknown property [unknown]' | '["unknown"]'
        SourceGenBoundaryDispatchRecord.class | '{"d":1,"d":2}'                 | 'Duplicate property [d]'     | '["d"]'
        SourceGenBoundaryDispatchRecord.class | '{"e":1.0,"e":2.0}'             | 'Duplicate property [e]'     | '["e"]'
        SourceGenBoundaryDispatchRecord.class | '{"d":null}'                    | 'Non-null property'          | '["d"]'
        SourceGenBoundaryDispatchRecord.class | '{"e":{"nested":true}}'         | null                         | '["e"]'
        SourceGenBoundaryDispatchRecord.class | '{"unknown":1}'                 | 'Unknown property [unknown]' | '["unknown"]'
        SourceGenLargeDispatchBean.class      | '{"f":"x","f":"y"}'             | 'Duplicate property [f]'     | '["f"]'
        SourceGenLargeDispatchBean.class      | '{"d":null}'                    | 'Non-null property'          | '["d"]'
        SourceGenLargeDispatchBean.class      | '{"f":{"nested":true}}'         | null                         | '["f"]'
        SourceGenLargeDispatchBean.class      | '{"unknown":1}'                 | 'Unknown property [unknown]' | '["unknown"]'
        SourceGenLargeDispatchRecord.class    | '{"f":"x","f":"y"}'             | 'Duplicate property [f]'     | '["f"]'
        SourceGenLargeDispatchRecord.class    | '{"d":null}'                    | 'Non-null property'          | '["d"]'
        SourceGenLargeDispatchRecord.class    | '{"f":{"nested":true}}'         | null                         | '["f"]'
        SourceGenLargeDispatchRecord.class    | '{"unknown":1}'                 | 'Unknown property [unknown]' | '["unknown"]'
    }

    void 'test generated non null record deserializer strict nullable accepts present values and primitive null defaults'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.strict-nullable': true,
            'micronaut.serde.deserialization.fail-on-null-for-primitives': false
        ])
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)

        when:
        def decoded = jsonMapper.readValue('{"a":"x","b":null,"c":null}', Argument.of(type))

        then:
        assertGeneratedDeserializer(registry, type)
        propertyValue(decoded, 'a') == 'x'
        propertyValue(decoded, 'b') == 0
        !propertyValue(decoded, 'c')

        cleanup:
        context.close()

        where:
        type << [
            SourceGenSmallDispatchNonNullRecord.class,
            SourceGenRuntimeSmallDispatchNonNullRecord.class
        ]
    }

    void 'test runtime keeps initialized primitive property values and databind uses primitive defaults when configured'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.fail-on-null-for-primitives': false
        ])
        jsonMapper = context.getBean(JsonMapper)
        def databindMapper = DatabindJsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build()

        when:
        def runtime = jsonMapper.readValue(
            '{"active":null,"count":null}',
            Argument.of(SourceGenRuntimePropertyDefaults)
        )
        def databind = databindMapper.readValue(
            '{"active":null,"count":null}',
            DatabindPrimitiveDefaults
        )

        then:
        runtime.active
        runtime.count == 7
        !databind.active
        databind.count == 0

        cleanup:
        context.close()
    }

    void 'test generated record deserializer failures match runtime for primitive null properties'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)

        expect:
        assertReadFailureMatches(jsonMapper, SourceGenGeneratedConstructorDefaults, SourceGenRuntimeConstructorDefaults, json)

        cleanup:
        context.close()

        where:
        scenario                 | json
        'primitive boolean null' | '{"name":"Ada","active":null,"count":42}'
        'primitive int null'     | '{"name":"Ada","active":true,"count":null}'
    }

    void 'test generated bean deserializer failures match runtime for primitive null properties'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)

        expect:
        assertReadFailureMatches(jsonMapper, SourceGenGeneratedPropertyDefaults, SourceGenRuntimePropertyDefaults, json)

        cleanup:
        context.close()

        where:
        scenario                 | json
        'primitive boolean null' | '{"name":"Ada","active":null,"count":42}'
        'primitive int null'     | '{"name":"Ada","active":true,"count":null}'
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

    private static void assertReadFailureMatches(JsonMapper jsonMapper,
                                                 Class<?> generatedType,
                                                 Class<?> runtimeType,
                                                 String json) {
        def generatedFailure = readFailure(jsonMapper, generatedType, json)
        def runtimeFailure = readFailure(jsonMapper, runtimeType, json)
        assert generatedFailure != null
        assert runtimeFailure != null
    }

    private static void assertSerdeFailure(JsonMapper jsonMapper,
                                           Class<?> type,
                                           String json,
                                           String messageFragment,
                                           String pathFragment) {
        SerdeException failure = serdeFailure(readFailure(jsonMapper, type, json))
        if (messageFragment != null) {
            assert failure.message.contains(messageFragment)
        }
        assert failure.pathAsString.contains(type.name)
        assert failure.pathAsString.contains(pathFragment)
    }

    private static SerdeException serdeFailure(Throwable failure) {
        assert failure != null
        Throwable current = failure
        while (current != null) {
            if (current instanceof SerdeException) {
                return current
            }
            current = current.cause
        }
        throw new AssertionError("No SerdeException in ${failure}")
    }

    private static Throwable readFailure(JsonMapper jsonMapper, Class<?> type, String json) {
        try {
            jsonMapper.readValue(json, Argument.of(type))
            return null
        } catch (Throwable e) {
            return e
        }
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
        def field = findDeclaredField(target, propertyName)
        if (field != null) {
            field.setAccessible(true)
            return field.get(target)
        }
        throw new IllegalArgumentException("No readable property '${propertyName}' on ${target.class.name}")
    }

    private static boolean hasProperty(Object target, String propertyName) {
        findDeclaredMethod(target, propertyName) != null
            || findDeclaredMethod(target, 'get' + propertyName.capitalize()) != null
            || findDeclaredMethod(target, 'is' + propertyName.capitalize()) != null
            || findDeclaredField(target, propertyName) != null
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

    private static def findDeclaredField(Object target, String fieldName) {
        try {
            return target.getClass().getDeclaredField(fieldName)
        } catch (NoSuchFieldException ignored) {
            return null
        }
    }

    private static TrackingPrimitiveDecoder trackingDecoder(String json, boolean failOnNullablePrimitiveDecode) {
        def parser = new JsonFactoryBuilder().build().createParser(json)
        new TrackingPrimitiveDecoder(JacksonDecoder.create(parser, LimitingStream.DEFAULT_LIMITS), failOnNullablePrimitiveDecode)
    }

    private static class TrackingPrimitiveDecoder extends DelegatingDecoder {
        private final Decoder delegate
        private final boolean failOnNullablePrimitiveDecode
        int nullablePrimitiveDecodeCalls
        int primitiveDecodeCalls
        int decodeNullCalls

        TrackingPrimitiveDecoder(Decoder delegate, boolean failOnNullablePrimitiveDecode) {
            this.delegate = delegate
            this.failOnNullablePrimitiveDecode = failOnNullablePrimitiveDecode
        }

        @Override
        protected Decoder delegate() throws IOException {
            delegate
        }

        @Override
        Decoder decodeObject(Argument<?> type) throws IOException {
            new TrackingObjectDecoder(delegate.decodeObject(type), this)
        }

        @Override
        IOException createDeserializationException(String message, Object invalidValue) {
            delegate.createDeserializationException(message, invalidValue)
        }
    }

    private static final class TrackingObjectDecoder extends DelegatingDecoder {
        private final Decoder delegate
        private final TrackingPrimitiveDecoder tracker

        TrackingObjectDecoder(Decoder delegate, TrackingPrimitiveDecoder tracker) {
            this.delegate = delegate
            this.tracker = tracker
        }

        @Override
        protected Decoder delegate() throws IOException {
            delegate
        }

        @Override
        Boolean decodeBooleanNullable() throws IOException {
            trackNullablePrimitiveDecode()
            delegate.decodeBooleanNullable()
        }

        @Override
        Integer decodeIntNullable() throws IOException {
            trackNullablePrimitiveDecode()
            delegate.decodeIntNullable()
        }

        @Override
        boolean decodeBoolean() throws IOException {
            tracker.primitiveDecodeCalls++
            delegate.decodeBoolean()
        }

        @Override
        int decodeInt() throws IOException {
            tracker.primitiveDecodeCalls++
            delegate.decodeInt()
        }

        @Override
        boolean decodeNull() throws IOException {
            tracker.decodeNullCalls++
            delegate.decodeNull()
        }

        @Override
        IOException createDeserializationException(String message, Object invalidValue) {
            delegate.createDeserializationException(message, invalidValue)
        }

        private void trackNullablePrimitiveDecode() {
            tracker.nullablePrimitiveDecodeCalls++
            if (tracker.failOnNullablePrimitiveDecode) {
                throw new AssertionError('Nullable primitive decode should only be used when failOnNullForPrimitives is enabled')
            }
        }
    }

    static class DatabindPrimitiveDefaults {
        boolean active = true
        int count = 7
    }
}
