package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.Decoder
import io.micronaut.serde.Deserializer
import io.micronaut.serde.Encoder
import io.micronaut.serde.FormatConfiguration
import io.micronaut.serde.FormattedDeserializer
import io.micronaut.serde.FormattedSerializer
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.ObjectSerializer
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.jackson.JacksonDecoder
import io.micronaut.serde.jackson.JacksonEncoder
import io.micronaut.serde.jackson.JsonCompileSpec
import tools.jackson.core.json.JsonFactory

class CompileTimeSourceGenSpec extends JsonCompileSpec {

    void 'test generated serializers use object encoder and generated classes use indexed fields'() {
        given:
        def context = ApplicationContext.run()
        Class<?> beanType = SourceGenIndexedShapeBean.class
        Class<?> recordType = SourceGenIndexedShapeRecord.class
        def introspections = context.getBean(SerdeIntrospections)

        when:
        def beanMetadata = introspections.getSerializableIntrospection(Argument.of(beanType)).annotationMetadata
        def recordMetadata = introspections.getSerializableIntrospection(Argument.of(recordType)).annotationMetadata
        String beanSerializerClassName = beanMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).orElseThrow()
        String beanDeserializerClassName = beanMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElseThrow()
        String recordSerializerClassName = recordMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).orElseThrow()
        String recordDeserializerClassName = recordMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElseThrow()
        Class<?> beanSerializerClass = context.classLoader.loadClass(beanSerializerClassName)
        Class<?> beanDeserializerClass = context.classLoader.loadClass(beanDeserializerClassName)
        Class<?> recordSerializerClass = context.classLoader.loadClass(recordSerializerClassName)
        Class<?> recordDeserializerClass = context.classLoader.loadClass(recordDeserializerClassName)
        String beanSerializerSource = generatedTestSource(beanSerializerClassName)
        String beanDeserializerSource = generatedTestSource(beanDeserializerClassName)
        String recordSerializerSource = generatedTestSource(recordSerializerClassName)
        String recordDeserializerSource = generatedTestSource(recordDeserializerClassName)
        Argument enumArgument = Argument.of(SourceGenFeatureEnum)
        def enumMetadata = introspections.getSerializableIntrospection(enumArgument).annotationMetadata
        String enumSerializerClassName = enumMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).orElseThrow()
        String enumDeserializerClassName = enumMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElseThrow()
        String enumSerializerSource = generatedTestSource(enumSerializerClassName)
        String enumDeserializerSource = generatedTestSource(enumDeserializerClassName)

        then:
        assertSerializeMethodUsesObjectEncoder(beanSerializerSource)
        assertSerializeMethodUsesObjectEncoder(recordSerializerSource)
        assertGeneratedPrototypeSerdeSource(beanSerializerSource)
        assertGeneratedPrototypeSerdeSource(beanDeserializerSource)
        assertGeneratedPrototypeSerdeSource(recordSerializerSource)
        assertGeneratedPrototypeSerdeSource(recordDeserializerSource)
        assertGeneratedPrototypeSerdeSource(enumSerializerSource)
        assertGeneratedPrototypeSerdeSource(enumDeserializerSource)
        assertSerializerValueNullability(beanSerializerSource, 'SourceGenIndexedShapeBean')
        assertSerializerValueNullability(recordSerializerSource, 'SourceGenIndexedShapeRecord')
        assertSerializerValueNullability(enumSerializerSource, 'SourceGenFeatureEnum')
        assertArgumentWithNameUsesKeyConstant(beanSerializerSource, beanSerializerClassName)
        assertArgumentWithNameUsesKeyConstant(beanDeserializerSource, beanDeserializerClassName)
        assertArgumentWithNameUsesKeyConstant(recordSerializerSource, recordSerializerClassName)
        assertArgumentWithNameUsesKeyConstant(recordDeserializerSource, recordDeserializerClassName)
        assertKeysAwareSerializerSource(beanSerializerSource)
        assertKeysAwareSerializerSource(recordSerializerSource)
        assertStringSwitchDispatchSource(beanDeserializerSource)
        assertStringSwitchDispatchSource(recordDeserializerSource)
        !beanSerializerSource.contains('propertyArgument')
        beanSerializerSource.contains("withPropertyPath(e0, type, ${simpleName(beanSerializerClassName)}.ARGUMENT_0)")
        beanSerializerSource.contains("withPropertyPath(e0, type, ${simpleName(beanSerializerClassName)}.ARGUMENT_1)")
        !recordSerializerSource.contains('propertyArgument')
        recordSerializerSource.contains("withPropertyPath(e0, type, ${simpleName(recordSerializerClassName)}.ARGUMENT_0)")
        recordSerializerSource.contains("withPropertyPath(e0, type, ${simpleName(recordSerializerClassName)}.ARGUMENT_1)")
        recordDeserializerSource.contains('String propertyValue0')
        beanSerializerClass.declaredFields*.name.containsAll(['KEY_0', 'ARGUMENT_0', 'KEY_1', 'ARGUMENT_1', 'KEYS', 'serializer1'])
        beanDeserializerClass.declaredFields*.name.containsAll(['KEY_0', 'ARGUMENT_0', 'KEY_1', 'ARGUMENT_1', 'KEYS', 'deserializer1'])
        recordSerializerClass.declaredFields*.name.containsAll(['KEY_0', 'ARGUMENT_0', 'KEY_1', 'ARGUMENT_1', 'KEYS', 'serializer1'])
        recordDeserializerClass.declaredFields*.name.containsAll(['KEY_0', 'ARGUMENT_0', 'KEY_1', 'ARGUMENT_1', 'KEYS', 'deserializer1'])
        enumDeserializerSource.contains('private final Deserializer stringDeserializer;')

        cleanup:
        context.close()
    }

    void 'test generated serializers and deserializers are selected for bean and record shapes'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)
        Class<?> beanType = SourceGenIndexedShapeBean.class
        Class<?> recordType = SourceGenIndexedShapeRecord.class
        Argument beanArgument = Argument.of(beanType)
        Argument recordArgument = Argument.of(recordType)

        when:
        def bean = new SourceGenIndexedShapeBean()
        bean.value = 'hello'
        bean.tags = ['a', 'b']
        def record = new SourceGenIndexedShapeRecord('hi', ['x', 'y'])
        String beanJson = serializeToString(jsonMapper, bean)
        String recordJson = serializeToString(jsonMapper, record)
        def beanDeserialized = jsonMapper.readValue(beanJson, beanArgument)
        def recordDeserialized = jsonMapper.readValue(recordJson, recordArgument)

        then:
        assertGeneratedSerializer(registry, beanArgument)
        assertGeneratedDeserializer(registry, beanArgument)
        assertGeneratedSerializer(registry, recordArgument)
        assertGeneratedDeserializer(registry, recordArgument)
        assertGeneratedPrototypeSerializer(registry, beanArgument)
        assertGeneratedPrototypeDeserializer(registry, beanArgument)
        assertGeneratedPrototypeSerializer(registry, recordArgument)
        assertGeneratedPrototypeDeserializer(registry, recordArgument)
        validateJsonWithoutOrder(jsonMapper, '{"value":"hello","tags":["a","b"]}', beanJson)
        validateJsonWithoutOrder(jsonMapper, '{"value":"hi","tags":["x","y"]}', recordJson)
        beanDeserialized.value == 'hello'
        beanDeserialized.tags == ['a', 'b']
        recordDeserialized.value() == 'hi'
        recordDeserialized.tags() == ['x', 'y']

        cleanup:
        context.close()
    }

    void 'test generated property serdes fall back for customised property metadata'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)
        Argument payloadArgument = Argument.of(SourceGenPropertyAnnotationPayload)
        def payload = new SourceGenPropertyAnnotationPayload()
        payload.value = 'direct'

        when:
        String json = serializeToString(jsonMapper, payload)
        SourceGenPropertyAnnotationPayload decoded = jsonMapper.readValue(json, payloadArgument)

        then:
        assertGeneratedSerializer(registry, payloadArgument)
        assertGeneratedDeserializer(registry, payloadArgument)
        assertPropertyArgumentFallsBackToObjectSerde(registry, 'formatted')
        assertPropertyArgumentFallsBackToObjectSerde(registry, 'unwrapped')
        assertPropertyArgumentFallsBackToObjectSerde(registry, 'included')
        json == '{"value":"direct"}'
        decoded.value == 'direct'

        cleanup:
        context.close()
    }

    void 'test generated bean shape supports field properties'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)
        Class<?> beanType = SourceGenFieldShapeBean.class
        Argument beanArgument = Argument.of(beanType)

        when:
        def bean = new SourceGenFieldShapeBean()
        bean.value = 'hello'
        bean.count = 7
        bean.tags = ['a', 'b']
        String json = serializeToString(jsonMapper, bean)
        def deserialized = jsonMapper.readValue(json, beanArgument)

        then:
        assertGeneratedSerializer(registry, beanArgument)
        assertGeneratedDeserializer(registry, beanArgument)
        validateJsonWithoutOrder(jsonMapper, '{"value":"hello","count":7,"tags":["a","b"]}', json)
        deserialized.value == 'hello'
        deserialized.count == 7
        deserialized.tags == ['a', 'b']

        cleanup:
        context.close()
    }

    void 'test generated default serializers are selected and functional'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)

        def generatedConstructorDefaults = new SourceGenGeneratedConstructorDefaults('Ada', true, 42, 'Grace', false)
        def generatedConstructorNulls = new SourceGenGeneratedConstructorDefaults(null, false, 7, null, null)
        def runtimeConstructorDefaults = new SourceGenRuntimeConstructorDefaults('Ada', true, 42, 'Grace', false)
        def runtimeConstructorNulls = new SourceGenRuntimeConstructorDefaults(null, false, 7, null, null)
        def generatedPropertyDefaults = new SourceGenGeneratedPropertyDefaults()
        generatedPropertyDefaults.name = 'Ada'
        generatedPropertyDefaults.active = false
        generatedPropertyDefaults.count = 42
        generatedPropertyDefaults.nullableName = 'Grace'
        generatedPropertyDefaults.nullableActive = false
        def generatedPropertyNulls = new SourceGenGeneratedPropertyDefaults()
        generatedPropertyNulls.name = null
        generatedPropertyNulls.nullableName = null
        generatedPropertyNulls.nullableActive = null
        def runtimePropertyDefaults = new SourceGenRuntimePropertyDefaults()
        runtimePropertyDefaults.name = 'Ada'
        runtimePropertyDefaults.active = false
        runtimePropertyDefaults.count = 42
        runtimePropertyDefaults.nullableName = 'Grace'
        runtimePropertyDefaults.nullableActive = false
        def runtimePropertyNulls = new SourceGenRuntimePropertyDefaults()
        runtimePropertyNulls.name = null
        runtimePropertyNulls.nullableName = null
        runtimePropertyNulls.nullableActive = null

        when:
        String generatedConstructorJson = serializeToString(jsonMapper, generatedConstructorDefaults)
        String generatedConstructorNullJson = serializeToString(jsonMapper, generatedConstructorNulls)
        String runtimeConstructorJson = serializeToString(jsonMapper, runtimeConstructorDefaults)
        String runtimeConstructorNullJson = serializeToString(jsonMapper, runtimeConstructorNulls)
        String generatedPropertyJson = serializeToString(jsonMapper, generatedPropertyDefaults)
        String generatedPropertyNullJson = serializeToString(jsonMapper, generatedPropertyNulls)
        String runtimePropertyJson = serializeToString(jsonMapper, runtimePropertyDefaults)
        String runtimePropertyNullJson = serializeToString(jsonMapper, runtimePropertyNulls)

        then:
        assertGeneratedSerializer(registry, Argument.of(SourceGenGeneratedConstructorDefaults))
        assertGeneratedSerializer(registry, Argument.of(SourceGenRuntimeConstructorDefaults))
        assertGeneratedSerializer(registry, Argument.of(SourceGenGeneratedPropertyDefaults))
        assertGeneratedSerializer(registry, Argument.of(SourceGenRuntimePropertyDefaults))
        validateJsonWithoutOrder(jsonMapper, '{"name":"Ada","active":true,"count":42,"nullableName":"Grace","nullableActive":false}', generatedConstructorJson)
        validateJsonWithoutOrder(jsonMapper, '{"name":null,"active":false,"count":7,"nullableName":null,"nullableActive":null}', generatedConstructorNullJson)
        validateJsonWithoutOrder(jsonMapper, '{"name":"Ada","active":true,"count":42,"nullableName":"Grace","nullableActive":false}', runtimeConstructorJson)
        validateJsonWithoutOrder(jsonMapper, '{"name":null,"active":false,"count":7,"nullableName":null,"nullableActive":null}', runtimeConstructorNullJson)
        validateJsonWithoutOrder(jsonMapper, '{"name":"Ada","active":false,"count":42,"nullableName":"Grace","nullableActive":false}', generatedPropertyJson)
        validateJsonWithoutOrder(jsonMapper, '{"name":null,"active":true,"count":7,"nullableName":null,"nullableActive":null}', generatedPropertyNullJson)
        validateJsonWithoutOrder(jsonMapper, '{"name":"Ada","active":false,"count":42,"nullableName":"Grace","nullableActive":false}', runtimePropertyJson)
        validateJsonWithoutOrder(jsonMapper, '{"name":null,"active":true,"count":7,"nullableName":null,"nullableActive":null}', runtimePropertyNullJson)

        cleanup:
        context.close()
    }

    void 'test generated field default serdes are selected and functional'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.fail-on-null-for-primitives': false
        ])
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)
        Argument generatedArgument = Argument.of(SourceGenGeneratedFieldDefaults)
        Argument runtimeArgument = Argument.of(SourceGenRuntimeFieldDefaults)
        def generatedDefaults = new SourceGenGeneratedFieldDefaults()
        generatedDefaults.name = 'Ada'
        generatedDefaults.active = false
        generatedDefaults.count = 42
        generatedDefaults.nullableName = 'Grace'
        generatedDefaults.nullableActive = false
        def generatedNulls = new SourceGenGeneratedFieldDefaults()
        generatedNulls.name = null
        generatedNulls.nullableName = null
        generatedNulls.nullableActive = null
        def runtimeDefaults = new SourceGenRuntimeFieldDefaults()
        runtimeDefaults.name = 'Ada'
        runtimeDefaults.active = false
        runtimeDefaults.count = 42
        runtimeDefaults.nullableName = 'Grace'
        runtimeDefaults.nullableActive = false
        def runtimeNulls = new SourceGenRuntimeFieldDefaults()
        runtimeNulls.name = null
        runtimeNulls.nullableName = null
        runtimeNulls.nullableActive = null

        when:
        String generatedJson = serializeToString(jsonMapper, generatedDefaults)
        String generatedNullJson = serializeToString(jsonMapper, generatedNulls)
        String runtimeJson = serializeToString(jsonMapper, runtimeDefaults)
        String runtimeNullJson = serializeToString(jsonMapper, runtimeNulls)
        SourceGenGeneratedFieldDefaults generatedDecoded = jsonMapper.readValue(
            '{"name":"Ada","active":false,"count":42,"nullableName":"Grace","nullableActive":false}',
            generatedArgument
        )
        SourceGenGeneratedFieldDefaults generatedDecodedNulls = jsonMapper.readValue(
            '{"name":null,"active":null,"count":null,"nullableName":null,"nullableActive":null}',
            generatedArgument
        )
        SourceGenRuntimeFieldDefaults runtimeDecoded = jsonMapper.readValue(
            '{"name":"Ada","active":false,"count":42,"nullableName":"Grace","nullableActive":false}',
            runtimeArgument
        )
        SourceGenRuntimeFieldDefaults runtimeDecodedNulls = jsonMapper.readValue(
            '{"name":null,"active":null,"count":null,"nullableName":null,"nullableActive":null}',
            runtimeArgument
        )

        then:
        assertGeneratedSerializer(registry, generatedArgument)
        assertGeneratedDeserializer(registry, generatedArgument)
        assertGeneratedSerializer(registry, runtimeArgument)
        assertGeneratedDeserializer(registry, runtimeArgument)
        validateJsonWithoutOrder(jsonMapper, '{"name":"Ada","active":false,"count":42,"nullableName":"Grace","nullableActive":false}', generatedJson)
        validateJsonWithoutOrder(jsonMapper, '{"name":null,"active":true,"count":7,"nullableName":null,"nullableActive":null}', generatedNullJson)
        validateJsonWithoutOrder(jsonMapper, '{"name":"Ada","active":false,"count":42,"nullableName":"Grace","nullableActive":false}', runtimeJson)
        validateJsonWithoutOrder(jsonMapper, '{"name":null,"active":true,"count":7,"nullableName":null,"nullableActive":null}', runtimeNullJson)
        generatedDecoded.name == 'Ada'
        !generatedDecoded.active
        generatedDecoded.count == 42
        generatedDecoded.nullableName == 'Grace'
        !generatedDecoded.nullableActive
        generatedDecodedNulls.name == null
        generatedDecodedNulls.active
        generatedDecodedNulls.count == 7
        generatedDecodedNulls.nullableName == null
        generatedDecodedNulls.nullableActive == null
        runtimeDecoded.name == 'Ada'
        !runtimeDecoded.active
        runtimeDecoded.count == 42
        runtimeDecoded.nullableName == 'Grace'
        !runtimeDecoded.nullableActive
        runtimeDecodedNulls.name == null
        runtimeDecodedNulls.active
        runtimeDecodedNulls.count == 7
        runtimeDecodedNulls.nullableName == null
        runtimeDecodedNulls.nullableActive == null

        cleanup:
        context.close()
    }

    void 'test generated enum serializer object and formatted branches are functional'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)
        Argument enumArgument = Argument.of(SourceGenFeatureEnum)
        def encoderContext = registry.newEncoderContext(Object)
        def decoderContext = registry.newDecoderContext(Object)
        def serializer = registry.findSerializer(enumArgument).createSpecific(encoderContext, enumArgument)

        when:
        String json = serializeToString(jsonMapper, SourceGenFeatureEnum.ALPHA)
        String intoJson = serializeIntoString(serializer as ObjectSerializer, encoderContext, enumArgument, SourceGenFeatureEnum.BETA)
        Serializer formattedSerializer = (registry.findSerializer(enumArgument) as FormattedSerializer)
            .createSpecific(encoderContext, enumArgument, FormatConfiguration.EMPTY)
        Deserializer formattedDeserializer = (registry.findDeserializer(enumArgument) as FormattedDeserializer)
            .createSpecific(decoderContext, enumArgument, FormatConfiguration.EMPTY)

        then:
        assertGeneratedSerializer(registry, enumArgument)
        assertGeneratedDeserializer(registry, enumArgument)
        serializer instanceof ObjectSerializer
        json == '"ALPHA"'
        intoJson == '"BETA"'
        formattedSerializer.class.name != generatedClassName(enumArgument.type, 'Serializer')
        formattedDeserializer.class.name != generatedClassName(enumArgument.type, 'Deserializer')

        cleanup:
        context.close()
    }

    void 'test generated enum deserializer reports unknown values'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)
        Argument enumArgument = Argument.of(SourceGenFeatureEnum)

        when:
        jsonMapper.readValue('"GAMMA"', enumArgument)

        then:
        SerdeException e = thrown()
        assertGeneratedDeserializer(registry, enumArgument)
        e.message.contains('GAMMA')

        cleanup:
        context.close()
    }

    void 'test generated serializers can be disabled with serialization feature'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.serialization.disable-generated-serializer': true
        ])
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)
        Argument recordArgument = Argument.of(SourceGenIndexedShapeRecord)
        Argument enumArgument = Argument.of(SourceGenFeatureEnum)

        when:
        def record = new SourceGenIndexedShapeRecord('hi', ['x', 'y'])
        String recordJson = serializeToString(jsonMapper, record)
        String enumJson = serializeToString(jsonMapper, SourceGenFeatureEnum.BETA)
        def decodedRecord = jsonMapper.readValue(recordJson, recordArgument)
        def decodedEnum = jsonMapper.readValue(enumJson, enumArgument)

        then:
        assertRuntimeSerializer(registry, recordArgument)
        assertGeneratedDeserializer(registry, recordArgument)
        assertRuntimeSerializer(registry, enumArgument)
        assertGeneratedDeserializer(registry, enumArgument)
        validateJsonWithoutOrder(jsonMapper, '{"value":"hi","tags":["x","y"]}', recordJson)
        enumJson == '"BETA"'
        decodedRecord == record
        decodedEnum == SourceGenFeatureEnum.BETA

        cleanup:
        context.close()
    }

    void 'test generated deserializers can be disabled with deserialization feature'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.deserialization.disable-generated-deserializer': true
        ])
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)
        Argument recordArgument = Argument.of(SourceGenIndexedShapeRecord)
        Argument enumArgument = Argument.of(SourceGenFeatureEnum)

        when:
        def record = new SourceGenIndexedShapeRecord('hi', ['x', 'y'])
        String recordJson = serializeToString(jsonMapper, record)
        String enumJson = serializeToString(jsonMapper, SourceGenFeatureEnum.BETA)
        def decodedRecord = jsonMapper.readValue(recordJson, recordArgument)
        def decodedEnum = jsonMapper.readValue(enumJson, enumArgument)

        then:
        assertGeneratedSerializer(registry, recordArgument)
        assertRuntimeDeserializer(registry, recordArgument)
        assertGeneratedSerializer(registry, enumArgument)
        assertRuntimeDeserializer(registry, enumArgument)
        validateJsonWithoutOrder(jsonMapper, '{"value":"hi","tags":["x","y"]}', recordJson)
        enumJson == '"BETA"'
        decodedRecord == record
        decodedEnum == SourceGenFeatureEnum.BETA

        cleanup:
        context.close()
    }

    @SuppressWarnings('JsonDuplicatePropertyKeys')
    void 'test generated bean deserializer dispatch source for small boundary and large property sets'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)
        def decoderContext = registry.newDecoderContext(Object)

        Class<?> smallType = SourceGenSmallDispatchBean.class
        Class<?> boundaryType = SourceGenBoundaryDispatchBean.class
        Class<?> largeType = SourceGenLargeDispatchBean.class
        Argument smallArgument = Argument.of(smallType)
        Argument boundaryArgument = Argument.of(boundaryType)
        Argument largeArgument = Argument.of(largeType)

        def smallDeserializer = buildDeserializer(registry, decoderContext, smallType)
        def boundaryDeserializer = buildDeserializer(registry, decoderContext, boundaryType)
        def largeDeserializer = buildDeserializer(registry, decoderContext, largeType)
        String smallDeserializerSource = generatedTestSource(smallDeserializer.class.name)
        String boundaryDeserializerSource = generatedTestSource(boundaryDeserializer.class.name)
        String largeDeserializerSource = generatedTestSource(largeDeserializer.class.name)

        when:
        def small = deserializeValue(smallDeserializer, decoderContext, smallArgument, '{"a":"x","b":7,"c":true}')
        def boundary = deserializeValue(boundaryDeserializer, decoderContext, boundaryArgument, '{"a":"x","b":7,"c":true,"d":9,"e":3.5}')
        def large = deserializeValue(largeDeserializer, decoderContext, largeArgument, '{"a":"x","b":7,"c":true,"d":9,"e":3.5,"f":"z"}')
        def smallToSerialize = new SourceGenSmallDispatchBean()
        smallToSerialize.a = 'x'
        smallToSerialize.b = 7
        smallToSerialize.c = true
        def boundaryToSerialize = new SourceGenBoundaryDispatchBean()
        boundaryToSerialize.a = 'x'
        boundaryToSerialize.b = 7
        boundaryToSerialize.c = true
        boundaryToSerialize.d = 9L
        boundaryToSerialize.e = 3.5d
        def largeToSerialize = new SourceGenLargeDispatchBean()
        largeToSerialize.a = 'x'
        largeToSerialize.b = 7
        largeToSerialize.c = true
        largeToSerialize.d = 9L
        largeToSerialize.e = 3.5d
        largeToSerialize.f = 'z'
        def largeNullsToSerialize = new SourceGenLargeDispatchBean()
        largeNullsToSerialize.b = 7
        largeNullsToSerialize.c = true
        largeNullsToSerialize.d = 9L
        largeNullsToSerialize.e = 3.5d
        String smallJson = serializeToString(jsonMapper, smallToSerialize)
        String boundaryJson = serializeToString(jsonMapper, boundaryToSerialize)
        String largeJson = serializeToString(jsonMapper, largeToSerialize)
        String largeNullJson = serializeToString(jsonMapper, largeNullsToSerialize)

        then:
        assertGeneratedSerializer(registry, smallArgument)
        assertGeneratedSerializer(registry, boundaryArgument)
        assertGeneratedSerializer(registry, largeArgument)
        assertGeneratedDeserializer(registry, smallArgument)
        assertGeneratedDeserializer(registry, boundaryArgument)
        assertGeneratedDeserializer(registry, largeArgument)
        assertSmallDispatchSource(smallDeserializerSource)
        assertSmallDispatchSource(boundaryDeserializerSource)
        assertLargeBeanDispatchSource(largeDeserializerSource)
        smallDeserializerSource.contains('bean.setA(objectDecoder.decodeStringNullable());')
        small.getA() == 'x'
        small.getB() == 7
        small.isC()
        boundary.getA() == 'x'
        boundary.getB() == 7
        boundary.isC()
        boundary.getD() == 9L
        boundary.getE() == 3.5d
        large.getA() == 'x'
        large.getB() == 7
        large.isC()
        large.getD() == 9L
        large.getE() == 3.5d
        large.getF() == 'z'
        validateJsonWithoutOrder(jsonMapper, '{"a":"x","b":7,"c":true}', smallJson)
        validateJsonWithoutOrder(jsonMapper, '{"a":"x","b":7,"c":true,"d":9,"e":3.5}', boundaryJson)
        validateJsonWithoutOrder(jsonMapper, '{"a":"x","b":7,"c":true,"d":9,"e":3.5,"f":"z"}', largeJson)
        validateJsonWithoutOrder(jsonMapper, '{"a":null,"b":7,"c":true,"d":9,"e":3.5,"f":null}', largeNullJson)

        cleanup:
        context.close()
    }

    void 'test generated large bean serializer wraps property exceptions'() {
        given:
        def context = ApplicationContext.run()
        def registry = context.getBean(SerdeRegistry)
        def encoderContext = registry.newEncoderContext(Object)
        Argument argument = Argument.of(SourceGenLargeDispatchBean)
        def serializer = registry.findSerializer(argument).createSpecific(encoderContext, argument) as ObjectSerializer
        def bean = new SourceGenLargeDispatchBean()
        bean.a = 'x'
        bean.b = 7
        bean.c = true
        bean.d = 9L
        bean.e = 3.5d
        bean.f = 'z'

        when:
        serializer.serializeInto(new FailingPropertyEncoder(propertyName), encoderContext, argument, bean)

        then:
        SerdeException e = thrown()
        assertGeneratedSerializer(registry, argument)
        e.pathAsString.contains(argument.type.name)
        e.pathAsString.contains(pathFragment)
        e.cause instanceof IOException

        cleanup:
        context.close()

        where:
        propertyName | pathFragment
        'a'          | '["a"]'
        'b'          | '["b"]'
        'c'          | '["c"]'
        'd'          | '["d"]'
        'e'          | '["e"]'
        'f'          | '["f"]'
    }

    void 'test generated large record serializers cover nullable branches'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)
        Argument argument = Argument.of(type)

        when:
        String json = serializeToString(jsonMapper, largeRecord(type, 'x', 'z'))
        String nullJson = serializeToString(jsonMapper, largeRecord(type, null, null))

        then:
        assertGeneratedSerializer(registry, argument)
        validateJsonWithoutOrder(jsonMapper, '{"a":"x","b":7,"c":true,"d":9,"e":3.5,"f":"z"}', json)
        validateJsonWithoutOrder(jsonMapper, '{"a":null,"b":7,"c":true,"d":9,"e":3.5,"f":null}', nullJson)

        cleanup:
        context.close()

        where:
        type << [
            SourceGenLargeDispatchRecord.class,
            SourceGenLargeDispatchNonNullRecord.class,
            SourceGenRuntimeLargeDispatchNonNullRecord.class
        ]
    }

    void 'test generated large record serializers wrap property exceptions'() {
        given:
        def context = ApplicationContext.run()
        def registry = context.getBean(SerdeRegistry)
        def encoderContext = registry.newEncoderContext(Object)
        Argument argument = Argument.of(type)
        def serializer = registry.findSerializer(argument).createSpecific(encoderContext, argument) as ObjectSerializer
        def value = largeRecord(type, 'x', 'z')

        when:
        serializer.serializeInto(new FailingPropertyEncoder(propertyName), encoderContext, argument, value)

        then:
        SerdeException e = thrown()
        assertGeneratedSerializer(registry, argument)
        e.pathAsString.contains(argument.type.name)
        e.pathAsString.contains(pathFragment)
        e.cause instanceof IOException

        cleanup:
        context.close()

        where:
        type                                                | propertyName | pathFragment
        SourceGenLargeDispatchRecord.class                  | 'a'          | '["a"]'
        SourceGenLargeDispatchRecord.class                  | 'b'          | '["b"]'
        SourceGenLargeDispatchRecord.class                  | 'c'          | '["c"]'
        SourceGenLargeDispatchRecord.class                  | 'd'          | '["d"]'
        SourceGenLargeDispatchRecord.class                  | 'e'          | '["e"]'
        SourceGenLargeDispatchRecord.class                  | 'f'          | '["f"]'
        SourceGenLargeDispatchNonNullRecord.class           | 'a'          | '["a"]'
        SourceGenLargeDispatchNonNullRecord.class           | 'b'          | '["b"]'
        SourceGenLargeDispatchNonNullRecord.class           | 'c'          | '["c"]'
        SourceGenLargeDispatchNonNullRecord.class           | 'd'          | '["d"]'
        SourceGenLargeDispatchNonNullRecord.class           | 'e'          | '["e"]'
        SourceGenLargeDispatchNonNullRecord.class           | 'f'          | '["f"]'
        SourceGenRuntimeLargeDispatchNonNullRecord.class    | 'a'          | '["a"]'
        SourceGenRuntimeLargeDispatchNonNullRecord.class    | 'b'          | '["b"]'
        SourceGenRuntimeLargeDispatchNonNullRecord.class    | 'c'          | '["c"]'
        SourceGenRuntimeLargeDispatchNonNullRecord.class    | 'd'          | '["d"]'
        SourceGenRuntimeLargeDispatchNonNullRecord.class    | 'e'          | '["e"]'
        SourceGenRuntimeLargeDispatchNonNullRecord.class    | 'f'          | '["f"]'
    }

    void 'test generated record deserializer dispatch source for small boundary and large property sets'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)
        def decoderContext = registry.newDecoderContext(Object)

        Class<?> smallType = SourceGenSmallDispatchRecord.class
        Class<?> boundaryType = SourceGenBoundaryDispatchRecord.class
        Class<?> largeType = SourceGenLargeDispatchRecord.class
        Argument smallArgument = Argument.of(smallType)
        Argument boundaryArgument = Argument.of(boundaryType)
        Argument largeArgument = Argument.of(largeType)

        def smallDeserializer = buildDeserializer(registry, decoderContext, smallType)
        def boundaryDeserializer = buildDeserializer(registry, decoderContext, boundaryType)
        def largeDeserializer = buildDeserializer(registry, decoderContext, largeType)
        String smallDeserializerSource = generatedTestSource(smallDeserializer.class.name)
        String boundaryDeserializerSource = generatedTestSource(boundaryDeserializer.class.name)
        String largeDeserializerSource = generatedTestSource(largeDeserializer.class.name)

        when:
        def small = deserializeValue(smallDeserializer, decoderContext, smallArgument, '{"a":"x","b":7,"c":true}')
        def boundary = deserializeValue(boundaryDeserializer, decoderContext, boundaryArgument, '{"a":"x","b":7,"c":true,"d":9,"e":3.5}')
        def large = deserializeValue(largeDeserializer, decoderContext, largeArgument, '{"a":"x","b":7,"c":true,"d":9,"e":3.5,"f":"z"}')
        String smallJson = serializeToString(jsonMapper, new SourceGenSmallDispatchRecord('x', 7, true))
        String boundaryJson = serializeToString(jsonMapper, new SourceGenBoundaryDispatchRecord('x', 7, true, 9L, 3.5d))
        String largeJson = serializeToString(jsonMapper, new SourceGenLargeDispatchRecord('x', 7, true, 9L, 3.5d, 'z'))

        then:
        assertGeneratedSerializer(registry, smallArgument)
        assertGeneratedSerializer(registry, boundaryArgument)
        assertGeneratedSerializer(registry, largeArgument)
        assertGeneratedDeserializer(registry, smallArgument)
        assertGeneratedDeserializer(registry, boundaryArgument)
        assertGeneratedDeserializer(registry, largeArgument)
        assertSmallDispatchSource(smallDeserializerSource)
        assertSmallDispatchSource(boundaryDeserializerSource)
        assertLargeRecordDispatchSource(largeDeserializerSource)
        small.a() == 'x'
        small.b() == 7
        small.c()
        boundary.a() == 'x'
        boundary.b() == 7
        boundary.c()
        boundary.d() == 9L
        boundary.e() == 3.5d
        large.a() == 'x'
        large.b() == 7
        large.c()
        large.d() == 9L
        large.e() == 3.5d
        large.f() == 'z'
        validateJsonWithoutOrder(jsonMapper, '{"a":"x","b":7,"c":true}', smallJson)
        validateJsonWithoutOrder(jsonMapper, '{"a":"x","b":7,"c":true,"d":9,"e":3.5}', boundaryJson)
        validateJsonWithoutOrder(jsonMapper, '{"a":"x","b":7,"c":true,"d":9,"e":3.5,"f":"z"}', largeJson)

        cleanup:
        context.close()
    }

    void 'test generated non null record serializers are selected and functional'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)
        Argument smallArgument = Argument.of(SourceGenSmallDispatchNonNullRecord)
        Argument runtimeSmallArgument = Argument.of(SourceGenRuntimeSmallDispatchNonNullRecord)
        Argument largeArgument = Argument.of(SourceGenLargeDispatchNonNullRecord)
        Argument runtimeLargeArgument = Argument.of(SourceGenRuntimeLargeDispatchNonNullRecord)

        when:
        String smallJson = serializeToString(jsonMapper, new SourceGenSmallDispatchNonNullRecord('x', 7, true))
        String runtimeSmallJson = serializeToString(jsonMapper, new SourceGenRuntimeSmallDispatchNonNullRecord('x', 7, true))
        String largeJson = serializeToString(jsonMapper, new SourceGenLargeDispatchNonNullRecord('x', 7, true, 9L, 3.5d, 'z'))
        String runtimeLargeJson = serializeToString(jsonMapper, new SourceGenRuntimeLargeDispatchNonNullRecord('x', 7, true, 9L, 3.5d, 'z'))

        then:
        assertGeneratedSerializer(registry, smallArgument)
        assertGeneratedSerializer(registry, runtimeSmallArgument)
        assertGeneratedSerializer(registry, largeArgument)
        assertGeneratedSerializer(registry, runtimeLargeArgument)
        validateJsonWithoutOrder(jsonMapper, '{"a":"x","b":7,"c":true}', smallJson)
        validateJsonWithoutOrder(jsonMapper, '{"a":"x","b":7,"c":true}', runtimeSmallJson)
        validateJsonWithoutOrder(jsonMapper, '{"a":"x","b":7,"c":true,"d":9,"e":3.5,"f":"z"}', largeJson)
        validateJsonWithoutOrder(jsonMapper, '{"a":"x","b":7,"c":true,"d":9,"e":3.5,"f":"z"}', runtimeLargeJson)

        cleanup:
        context.close()
    }

    void 'test generated record constructor failures match runtime for small and large property sets'() {
        given:
        def context = ApplicationContext.run(['micronaut.serde.deserialization.strict-nullable': true])
        jsonMapper = context.getBean(JsonMapper)

        expect:
        assertReadFailureMatches(jsonMapper, generatedType, runtimeType, json)

        cleanup:
        context.close()

        where:
        scenario                         | generatedType                                  | runtimeType                                         | json
        'small missing non-null value'   | SourceGenSmallDispatchNonNullRecord.class      | SourceGenRuntimeSmallDispatchNonNullRecord.class    | '{"b":7,"c":true}'
        'small explicit non-null null'   | SourceGenSmallDispatchNonNullRecord.class      | SourceGenRuntimeSmallDispatchNonNullRecord.class    | '{"a":null,"b":7,"c":true}'
        'large missing non-null value'   | SourceGenLargeDispatchNonNullRecord.class      | SourceGenRuntimeLargeDispatchNonNullRecord.class    | '{"a":"x","b":7,"c":true,"d":9,"e":3.5}'
        'large explicit non-null null'   | SourceGenLargeDispatchNonNullRecord.class      | SourceGenRuntimeLargeDispatchNonNullRecord.class    | '{"a":"x","b":7,"c":true,"d":9,"e":3.5,"f":null}'
    }

    private static void assertSerializeMethodUsesObjectEncoder(String serializerSource) {
        String serializeMethodSource = serializerSource.substring(
            serializerSource.indexOf('public void serialize('),
            serializerSource.indexOf('public void serializeInto(')
        )
        assert serializeMethodSource.contains('Encoder objectEncoder = encoder.encodeObject(type);')
        assert serializeMethodSource.contains('this.serializeInto(objectEncoder, context, type, value);')
        assert serializeMethodSource.contains('objectEncoder.finishStructure();')
        assert serializeMethodSource.indexOf('encoder.encodeObject(type)') < serializeMethodSource.indexOf('this.serializeInto')
        assert serializeMethodSource.indexOf('this.serializeInto') < serializeMethodSource.indexOf('objectEncoder.finishStructure()')
    }

    private static void assertSerializerValueNullability(String serializerSource, String valueType) {
        String serializeMethodSource = serializerSource.substring(
            serializerSource.indexOf('public void serialize('),
            serializerSource.indexOf('public void serializeInto(')
        )
        String serializeIntoMethodSource = serializerSource.substring(serializerSource.indexOf('public void serializeInto('))
        assert serializeMethodSource.contains("${valueType} value")
        assert !serializeMethodSource.contains("@Nullable ${valueType} value")
        assert !serializeMethodSource.contains('value == null')
        assert serializeIntoMethodSource.contains("${valueType} value")
        assert !serializeIntoMethodSource.contains("@Nullable ${valueType} value")
        assert !serializeIntoMethodSource.contains('value == null')
    }

    private static void assertGeneratedPrototypeSerdeSource(String serdeSource) {
        assert serdeSource.contains('@Prototype')
    }

    private static void assertArgumentWithNameUsesKeyConstant(String serdeSource, String className) {
        assert serdeSource.contains(".withName(${simpleName(className)}.KEY_0)")
    }

    private static void assertSmallDispatchSource(String deserializerSource) {
        assertStringSwitchDispatchSource(deserializerSource)
    }

    private static void assertLargeBeanDispatchSource(String deserializerSource) {
        assertLargeDispatchSource(deserializerSource)
        assert deserializerSource.contains('if (this.failOnNullForPrimitives)')
        assert deserializerSource.contains('objectDecoder.decodeNull()')
        assert deserializerSource.contains('objectDecoder.decodeBoolean()')
        assert deserializerSource.contains('failOnNullForPrimitives(context)')
        assert deserializerSource.contains('GeneratedSerdeExceptionUtil.withPropertyPath(e0, type, ')
        assert !deserializerSource.contains('GeneratedSerdeExceptionUtil.nullValue(type, Argument.OBJECT_ARGUMENT.withName(key))')
    }

    private static void assertLargeRecordDispatchSource(String deserializerSource) {
        assertLargeDispatchSource(deserializerSource)
        assert deserializerSource.contains('boolean propertyValue2 = false;')
        assert deserializerSource.contains('if (this.failOnNullForPrimitives)')
        assert !deserializerSource.contains('objectDecoder.decodeBooleanNullable()')
        assert deserializerSource.contains('objectDecoder.decodeNull()')
        assert deserializerSource.contains('objectDecoder.decodeBoolean()')
        assert deserializerSource.contains('failOnNullForPrimitives(context)')
        assert deserializerSource.contains('GeneratedSerdeExceptionUtil.withPropertyPath(e0, type, ')
        assert !deserializerSource.contains('GeneratedSerdeExceptionUtil.nullValue(type, Argument.OBJECT_ARGUMENT.withName(key))')
    }

    private static void assertLargeDispatchSource(String deserializerSource) {
        assertStringSwitchDispatchSource(deserializerSource)
    }

    private static void assertStringSwitchDispatchSource(String deserializerSource) {
        assert deserializerSource.contains('while (true)')
        assert deserializerSource.contains('private static final Keys KEYS = Keys.create(new String[]{')
        assert deserializerSource.contains('KeysAwareDecoder keysAwareDecoder = KeysAwareDecoder.of(objectDecoder);')
        assert deserializerSource.contains('switch (keysAwareDecoder.decodeKey(')
        assert !deserializerSource.contains('int keyIndex = keysAwareDecoder.decodeKey(')
        assert !deserializerSource.contains('switch (keyIndex)')
        assert deserializerSource.contains('case -1 ->')
        assert deserializerSource.contains('case -2 ->')
        assert !deserializerSource.contains('if (keyIndex == -1)')
        assert !deserializerSource.contains('if (keyIndex == -2)')
        assert deserializerSource.contains('case 0 ->')
        assert deserializerSource.contains('String key = keysAwareDecoder.decodeKey();')
        if (deserializerSource.contains('long seenProperties = 0l;')) {
            assert !deserializerSource.contains('long propertyBit = 1l << (long) keyIndex;')
            assert !deserializerSource.contains('ARGUMENTS[keyIndex]')
            assert deserializerSource.contains('GeneratedSerdeExceptionUtil.duplicateProperty(type, ')
            assert !deserializerSource.contains('propertyDispatchStatus')
        }
        assert !deserializerSource.contains('if (objectDecoder.decodeNull()) {\n              } else {')
        assert !deserializerSource.contains('switch (key)')
        assert !deserializerSource.contains('PropertyDispatchResult')
        assert !deserializerSource.contains('PropertyDispatchResult propertyDispatchResult')
        assert !deserializerSource.contains('default -> GeneratedSerdeExceptionUtil.PropertyDispatchResult.UNKNOWN;')
        assert !deserializerSource.contains('yield dispatchResult;')
        assert !deserializerSource.contains('switch (propertyDispatchResult)')
        assert !deserializerSource.contains('if (propertyDispatchResult != GeneratedSerdeExceptionUtil.PropertyDispatchResult.HANDLED)')
        assert deserializerSource.contains('GeneratedSerdeExceptionUtil.unknownProperty(type, Argument.OBJECT_ARGUMENT.withName(key))')
        assert !deserializerSource.contains('GeneratedSerdeExceptionUtil.duplicateProperty(type, Argument.OBJECT_ARGUMENT.withName(key))')
        int expectedUnknownKeyDecodeCount = deserializerSource.contains('if (this.failOnNullForPrimitives)')
            && deserializerSource.count('while (true)') > 1 ? 2 : 1
        assert deserializerSource.count('decodeKey()') == expectedUnknownKeyDecodeCount
        assert !deserializerSource.contains('key.equals(')
        assert !deserializerSource.contains('boolean handledProperty')
        assert !deserializerSource.contains('if (!handledProperty)')
        assert deserializerSource.contains('skipValue')
    }

    private static void assertKeysAwareSerializerSource(String serializerSource) {
        assert serializerSource.contains('private static final Keys KEYS = Keys.create(new String[]{')
        assert serializerSource.contains('KeysAwareEncoder keysAwareEncoder = KeysAwareEncoder.of(encoder);')
        assert serializerSource.contains('keysAwareEncoder.encodeKey(')
        assert !serializerSource.contains('encoder.encodeKey(')
    }

    private Object buildDeserializer(SerdeRegistry registry,
                                     Deserializer.DecoderContext decoderContext,
                                     Class<?> type) {
        Argument argument = Argument.of(type)
        registry.findDeserializer(argument).createSpecific(decoderContext, argument)
    }

    private static Object deserializeValue(Deserializer deserializer,
                                           Deserializer.DecoderContext decoderContext,
                                           Argument type,
                                           String json) {
        def jsonFactory = new JsonFactory()
        def result
        jsonFactory.createParser(json).withCloseable { parser ->
            Decoder decoder = JacksonDecoder.create(parser, LimitingStream.DEFAULT_LIMITS)
            result = deserializer.deserialize(decoder, decoderContext, type)
        }
        result
    }

    private static String serializeIntoString(ObjectSerializer serializer,
                                              Serializer.EncoderContext encoderContext,
                                              Argument type,
                                              Object value) {
        def jsonFactory = new JsonFactory()
        def writer = new StringWriter()
        jsonFactory.createGenerator(writer).withCloseable { generator ->
            def encoder = JacksonEncoder.create(generator)
            serializer.serializeInto(encoder, encoderContext, type, value)
        }
        writer.toString()
    }

    private static Object largeRecord(Class<?> type, String a, String f) {
        if (type == SourceGenLargeDispatchRecord.class) {
            return new SourceGenLargeDispatchRecord(a, 7, true, 9L, 3.5d, f)
        }
        if (type == SourceGenLargeDispatchNonNullRecord.class) {
            return new SourceGenLargeDispatchNonNullRecord(a, 7, true, 9L, 3.5d, f)
        }
        if (type == SourceGenRuntimeLargeDispatchNonNullRecord.class) {
            return new SourceGenRuntimeLargeDispatchNonNullRecord(a, 7, true, 9L, 3.5d, f)
        }
        throw new IllegalArgumentException(type.name)
    }

    private static final class FailingPropertyEncoder implements Encoder {
        private final String propertyName
        private String currentKey

        FailingPropertyEncoder(String propertyName) {
            this.propertyName = propertyName
        }

        @Override
        Encoder encodeArray(Argument type) {
            this
        }

        @Override
        Encoder encodeObject(Argument type) {
            this
        }

        @Override
        void finishStructure() {
        }

        @Override
        void encodeKey(String key) {
            currentKey = key
        }

        @Override
        void encodeString(String value) throws IOException {
            failIfSelected()
        }

        @Override
        void encodeBoolean(boolean value) throws IOException {
            failIfSelected()
        }

        @Override
        void encodeByte(byte value) throws IOException {
            failIfSelected()
        }

        @Override
        void encodeShort(short value) throws IOException {
            failIfSelected()
        }

        @Override
        void encodeChar(char value) throws IOException {
            failIfSelected()
        }

        @Override
        void encodeInt(int value) throws IOException {
            failIfSelected()
        }

        @Override
        void encodeLong(long value) throws IOException {
            failIfSelected()
        }

        @Override
        void encodeFloat(float value) throws IOException {
            failIfSelected()
        }

        @Override
        void encodeDouble(double value) throws IOException {
            failIfSelected()
        }

        @Override
        void encodeBigInteger(BigInteger value) throws IOException {
            failIfSelected()
        }

        @Override
        void encodeBigDecimal(BigDecimal value) throws IOException {
            failIfSelected()
        }

        @Override
        void encodeNull() throws IOException {
            failIfSelected()
        }

        private void failIfSelected() throws IOException {
            if (currentKey == propertyName) {
                throw new IOException("Failure for property $propertyName")
            }
        }
    }

    private static void assertGeneratedSerializer(SerdeRegistry registry, Argument argument) {
        Serializer serializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument)
        assert serializer.class.name == generatedClassName(argument.type, 'Serializer')
    }

    private static void assertGeneratedDeserializer(SerdeRegistry registry, Argument argument) {
        Deserializer deserializer = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)
        assert deserializer.class.name == generatedClassName(argument.type, 'Deserializer')
    }

    private static void assertRuntimeSerializer(SerdeRegistry registry, Argument argument) {
        Serializer serializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument)
        assert serializer.class.name != generatedClassName(argument.type, 'Serializer')
    }

    private static void assertRuntimeDeserializer(SerdeRegistry registry, Argument argument) {
        Deserializer deserializer = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)
        assert deserializer.class.name != generatedClassName(argument.type, 'Deserializer')
    }

    private static void assertGeneratedPrototypeSerializer(SerdeRegistry registry, Argument argument) {
        def encoderContext = registry.newEncoderContext(Object)
        Serializer serializer1 = registry.findSerializer(argument).createSpecific(encoderContext, argument)
        Serializer serializer2 = registry.findSerializer(argument).createSpecific(encoderContext, argument)
        assert serializer1.class.name == generatedClassName(argument.type, 'Serializer')
        assert serializer2.class.name == generatedClassName(argument.type, 'Serializer')
        assert !serializer1.is(serializer2)
    }

    private static void assertGeneratedPrototypeDeserializer(SerdeRegistry registry, Argument argument) {
        def decoderContext = registry.newDecoderContext(Object)
        Deserializer deserializer1 = registry.findDeserializer(argument).createSpecific(decoderContext, argument)
        Deserializer deserializer2 = registry.findDeserializer(argument).createSpecific(decoderContext, argument)
        assert deserializer1.class.name == generatedClassName(argument.type, 'Deserializer')
        assert deserializer2.class.name == generatedClassName(argument.type, 'Deserializer')
        assert !deserializer1.is(deserializer2)
    }

    private static void assertPropertyArgumentFallsBackToObjectSerde(SerdeRegistry registry, String propertyName) {
        def property = BeanIntrospector.SHARED
            .getIntrospection(SourceGenPropertyAnnotationHolder)
            .getRequiredProperty(propertyName, SourceGenPropertyAnnotationPayload)
        Argument propertyArgument = property.asArgument()
        def encoderContext = registry.newEncoderContext(Object)
        def decoderContext = registry.newDecoderContext(Object)

        Serializer serializer = registry.findSerializer(propertyArgument).createSpecific(encoderContext, propertyArgument)
        Serializer objectSerializer = encoderContext.findSerializer(Argument.OBJECT_ARGUMENT).createSpecific(encoderContext, propertyArgument)
        Deserializer deserializer = registry.findDeserializer(propertyArgument).createSpecific(decoderContext, propertyArgument)
        Deserializer objectDeserializer = decoderContext.findDeserializer(Argument.OBJECT_ARGUMENT).createSpecific(decoderContext, propertyArgument)

        assert serializer.class == objectSerializer.class
        assert deserializer.class == objectDeserializer.class
        assert serializer.class.name != generatedClassName(propertyArgument.type, 'Serializer')
        assert deserializer.class.name != generatedClassName(propertyArgument.type, 'Deserializer')
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

    private static Throwable readFailure(JsonMapper jsonMapper, Class<?> type, String json) {
        try {
            jsonMapper.readValue(json, Argument.of(type))
            return null
        } catch (Throwable e) {
            return e
        }
    }

    private static String simpleName(String className) {
        className.substring(className.lastIndexOf('.') + 1)
    }

    private static String generatedClassName(Class<?> type, String suffix) {
        "${type.package.name}.Serde${type.simpleName}${suffix}"
    }
}
