package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.Decoder
import io.micronaut.serde.Deserializer
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.serde.jackson.JacksonDecoder
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
        String recordSerializerSource = generatedTestSource(recordSerializerClassName)
        String recordDeserializerSource = generatedTestSource(recordDeserializerClassName)

        then:
        assertSerializeMethodUsesObjectEncoder(beanSerializerSource)
        assertSerializeMethodUsesObjectEncoder(recordSerializerSource)
        beanSerializerSource.contains("withPropertyPath(e0, type, ${simpleName(beanSerializerClassName)}.KEY_0, ${simpleName(beanSerializerClassName)}.ARGUMENT_0)")
        recordSerializerSource.contains("withPropertyPath(e0, type, ${simpleName(recordSerializerClassName)}.KEY_0, ${simpleName(recordSerializerClassName)}.ARGUMENT_0)")
        !beanSerializerSource.contains('withPropertyPath(e0, type, "value"')
        !recordSerializerSource.contains('withPropertyPath(e0, type, "value"')
        recordDeserializerSource.contains('String propertyValue0')
        !recordDeserializerSource.contains('component0')
        beanSerializerClass.declaredFields*.name.containsAll(['KEY_0', 'ARGUMENT_0', 'KEY_1', 'ARGUMENT_1', 'SERIALIZER_1'])
        beanDeserializerClass.declaredFields*.name.containsAll(['KEY_0', 'ARGUMENT_0', 'KEY_1', 'ARGUMENT_1', 'DESERIALIZER_1'])
        recordSerializerClass.declaredFields*.name.containsAll(['KEY_0', 'ARGUMENT_0', 'KEY_1', 'ARGUMENT_1', 'SERIALIZER_1'])
        recordDeserializerClass.declaredFields*.name.containsAll(['KEY_0', 'ARGUMENT_0', 'KEY_1', 'ARGUMENT_1', 'DESERIALIZER_1'])
        !beanSerializerClass.declaredFields*.name.any { it.contains('VALUE') || it.contains('TAGS') }
        !beanDeserializerClass.declaredFields*.name.any { it.contains('VALUE') || it.contains('TAGS') }
        !recordSerializerClass.declaredFields*.name.any { it.contains('VALUE') || it.contains('TAGS') }
        !recordDeserializerClass.declaredFields*.name.any { it.contains('VALUE') || it.contains('TAGS') }

        cleanup:
        context.close()
    }

    @SuppressWarnings('JsonDuplicatePropertyKeys')
    void 'test generated bean deserializer dispatch source for small boundary and large property sets'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        def registry = jsonMapper.serdeRegistry
        def decoderContext = registry.newDecoderContext(Object)

        Class<?> smallType = SourceGenSmallDispatchBean.class
        Class<?> boundaryType = SourceGenBoundaryDispatchBean.class
        Class<?> largeType = SourceGenLargeDispatchBean.class
        Argument smallArgument = Argument.of(smallType)
        Argument boundaryArgument = Argument.of(boundaryType)
        Argument largeArgument = Argument.of(largeType)

        def smallDeserializer = buildDeserializer(context, smallType)
        def boundaryDeserializer = buildDeserializer(context, boundaryType)
        def largeDeserializer = buildDeserializer(context, largeType)
        String smallDeserializerSource = generatedTestSource(smallDeserializer.class.name)
        String boundaryDeserializerSource = generatedTestSource(boundaryDeserializer.class.name)
        String largeDeserializerSource = generatedTestSource(largeDeserializer.class.name)

        when:
        def small = deserializeValue(smallDeserializer, decoderContext, smallArgument, '{"a":"x","b":7,"c":true}')
        def boundary = deserializeValue(boundaryDeserializer, decoderContext, boundaryArgument, '{"a":"x","b":7,"c":true,"d":9,"e":3.5}')
        def large = deserializeValue(largeDeserializer, decoderContext, largeArgument, '{"a":"x","b":7,"c":true,"d":9,"e":3.5,"f":"z"}')

        then:
        assertSmallDispatchSource(smallDeserializerSource)
        assertSmallDispatchSource(boundaryDeserializerSource)
        assertLargeBeanDispatchSource(largeDeserializerSource)
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

        when:
        def smallDuplicate = deserializeValue(smallDeserializer, decoderContext, smallArgument, '{"a":"x","a":"y","b":7,"c":true}')
        def boundaryDuplicate = deserializeValue(boundaryDeserializer, decoderContext, boundaryArgument, '{"a":"x","b":7,"c":true,"d":9,"e":3.5,"e":1.0}')
        def largeDuplicate = deserializeValue(largeDeserializer, decoderContext, largeArgument, '{"a":"x","b":7,"c":true,"d":9,"e":3.5,"f":"z","f":"y"}')

        then:
        smallDuplicate.getA() == 'x'
        smallDuplicate.getB() == 7
        smallDuplicate.isC()
        boundaryDuplicate.getA() == 'x'
        boundaryDuplicate.getB() == 7
        boundaryDuplicate.isC()
        boundaryDuplicate.getD() == 9L
        boundaryDuplicate.getE() == 3.5d
        largeDuplicate.getA() == 'x'
        largeDuplicate.getB() == 7
        largeDuplicate.isC()
        largeDuplicate.getD() == 9L
        largeDuplicate.getE() == 3.5d
        largeDuplicate.getF() == 'z'

        cleanup:
        context.close()
    }

    @SuppressWarnings('JsonDuplicatePropertyKeys')
    void 'test generated record deserializer dispatch source for small boundary and large property sets'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        def registry = jsonMapper.serdeRegistry
        def decoderContext = registry.newDecoderContext(Object)

        Class<?> smallType = SourceGenSmallDispatchRecord.class
        Class<?> boundaryType = SourceGenBoundaryDispatchRecord.class
        Class<?> largeType = SourceGenLargeDispatchRecord.class
        Argument smallArgument = Argument.of(smallType)
        Argument boundaryArgument = Argument.of(boundaryType)
        Argument largeArgument = Argument.of(largeType)

        def smallDeserializer = buildDeserializer(context, smallType)
        def boundaryDeserializer = buildDeserializer(context, boundaryType)
        def largeDeserializer = buildDeserializer(context, largeType)
        String smallDeserializerSource = generatedTestSource(smallDeserializer.class.name)
        String boundaryDeserializerSource = generatedTestSource(boundaryDeserializer.class.name)
        String largeDeserializerSource = generatedTestSource(largeDeserializer.class.name)

        when:
        def small = deserializeValue(smallDeserializer, decoderContext, smallArgument, '{"a":"x","b":7,"c":true}')
        def boundary = deserializeValue(boundaryDeserializer, decoderContext, boundaryArgument, '{"a":"x","b":7,"c":true,"d":9,"e":3.5}')
        def large = deserializeValue(largeDeserializer, decoderContext, largeArgument, '{"a":"x","b":7,"c":true,"d":9,"e":3.5,"f":"z"}')

        then:
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

        when:
        def smallDuplicate = deserializeValue(smallDeserializer, decoderContext, smallArgument, '{"a":"x","a":"y","b":7,"c":true}')
        def boundaryDuplicate = deserializeValue(boundaryDeserializer, decoderContext, boundaryArgument, '{"a":"x","b":7,"c":true,"d":9,"e":3.5,"e":1.0}')
        def largeDuplicate = deserializeValue(largeDeserializer, decoderContext, largeArgument, '{"a":"x","b":7,"c":true,"d":9,"e":3.5,"f":"z","f":"y"}')

        then:
        smallDuplicate.a() == 'x'
        smallDuplicate.b() == 7
        smallDuplicate.c()
        boundaryDuplicate.a() == 'x'
        boundaryDuplicate.b() == 7
        boundaryDuplicate.c()
        boundaryDuplicate.d() == 9L
        boundaryDuplicate.e() == 3.5d
        largeDuplicate.a() == 'x'
        largeDuplicate.b() == 7
        largeDuplicate.c()
        largeDuplicate.d() == 9L
        largeDuplicate.e() == 3.5d
        largeDuplicate.f() == 'z'

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
        assert !serializeMethodSource.contains('encodeKey')
    }

    private static void assertSmallDispatchSource(String deserializerSource) {
        assert deserializerSource.contains('key.equals(')
        assert !deserializerSource.contains('Objects.equals(key')
        assert !deserializerSource.contains('switch (key)')
        assert deserializerSource.contains('skipValue')
        assert !deserializerSource.contains('duplicateProperty')
    }

    private static void assertLargeBeanDispatchSource(String deserializerSource) {
        assertLargeDispatchSource(deserializerSource)
        assert !deserializerSource.contains('boolean value2 = false;')
        assert !deserializerSource.contains('value2 = objectDecoder.decodeBoolean();')
        assert deserializerSource.contains('bean.setC(objectDecoder.decodeBoolean());')
        assert !deserializerSource.contains('decodeBooleanNullable')
    }

    private static void assertLargeRecordDispatchSource(String deserializerSource) {
        assertLargeDispatchSource(deserializerSource)
        assert deserializerSource.contains('boolean propertyValue2 = false;')
        assert deserializerSource.contains('propertyValue2 = objectDecoder.decodeBoolean();')
        assert !deserializerSource.contains('decodeBooleanNullable')
        assert !deserializerSource.contains('if (!seenProperty')
    }

    private static void assertLargeDispatchSource(String deserializerSource) {
        assert deserializerSource.contains('switch (key)')
        assert !deserializerSource.contains('key.equals(')
        assert !deserializerSource.contains('Objects.equals(key')
        assert !deserializerSource.contains('switchResult')
        assert !deserializerSource.contains('matched' + 'Property')
        assert !deserializerSource.contains('= switch (key)')
        assert !deserializerSource.contains('yield')
        assert deserializerSource.count('decodeKey()') == 2
        assert deserializerSource.contains('skipValue')
        assert !deserializerSource.contains('duplicateProperty')
    }

    private Object buildDeserializer(def context, Class<?> type) {
        def introspections = context.getBean(SerdeIntrospections)
        def metadata = introspections.getSerializableIntrospection(Argument.of(type)).annotationMetadata
        String deserializerClassName = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
        Class<?> deserializerClass = context.classLoader.loadClass(deserializerClassName)
        (Deserializer) deserializerClass.getDeclaredConstructor().newInstance()
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
}
