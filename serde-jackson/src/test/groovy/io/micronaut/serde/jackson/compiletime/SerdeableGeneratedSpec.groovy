package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.serde.jackson.JsonCompileSpec

class SerdeableGeneratedSpec extends JsonCompileSpec {

    void 'test serdeable generated requires generated serializer and deserializer by default'() {
        given:
        def context = ApplicationContext.run()

        expect:
        assertEligibility(context, SourceGenGeneratedShape, true, true)

        cleanup:
        context.close()
    }

    void 'test serdeable generated serializer and deserializer are functional'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)
        Argument argument = Argument.of(SourceGenGeneratedShape)

        when:
        String json = serializeToString(jsonMapper, new SourceGenGeneratedShape('Ada', 42))
        String nullJson = serializeToString(jsonMapper, new SourceGenGeneratedShape(null, 7))
        SourceGenGeneratedShape decoded = jsonMapper.readValue('{"name":"Ada","count":42}', argument)

        then:
        assertRegistrySelection(registry, argument, 'Serializer', true)
        assertRegistrySelection(registry, argument, 'Deserializer', true)
        json == '{"name":"Ada","count":42}'
        nullJson == '{"name":null,"count":7}'
        decoded.name() == 'Ada'
        decoded.count() == 42

        cleanup:
        context.close()
    }

    void 'test serdeable generated required false allows sourcegen fallback without generated classes'() {
        given:
        def context = ApplicationContext.run()

        expect:
        assertEligibility(context, SourceGenRequiredFalseUnsupportedShape, false, false)

        cleanup:
        context.close()
    }

    void 'test serdeable generated skip disables both generated directions'() {
        given:
        def context = ApplicationContext.run()

        expect:
        assertEligibility(context, SourceGenSkippedShape, false, false)

        cleanup:
        context.close()
    }

    void 'test serdeable generated can skip serializer or deserializer only'() {
        given:
        def context = ApplicationContext.run()

        expect:
        assertEligibility(context, SourceGenSkipSerializerShape, false, true)
        assertEligibility(context, SourceGenSkipDeserializerShape, true, false)

        cleanup:
        context.close()
    }

    void 'test serdeable generated directional skip generated sides are functional'() {
        given:
        def context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)
        Argument skipSerializerArgument = Argument.of(SourceGenSkipSerializerShape)
        Argument skipDeserializerArgument = Argument.of(SourceGenSkipDeserializerShape)
        Argument anyGetterSkipSerializerArgument = Argument.of(SourceGenAnyGetterSkipSerializerShape)

        when:
        SourceGenSkipSerializerShape decodedSkipSerializer = jsonMapper.readValue('{"name":"Ada"}', skipSerializerArgument)
        String skipDeserializerJson = serializeToString(jsonMapper, new SourceGenSkipDeserializerShape('Ada'))
        SourceGenAnyGetterSkipSerializerShape decodedAnyGetterSkipSerializer = jsonMapper.readValue(
            '{"name":"Ada","attributes":{"extra":"value"}}',
            anyGetterSkipSerializerArgument
        )

        then:
        assertRegistrySelection(registry, skipSerializerArgument, 'Deserializer', true)
        assertRegistrySelection(registry, skipDeserializerArgument, 'Serializer', true)
        assertRegistrySelection(registry, anyGetterSkipSerializerArgument, 'Deserializer', true)
        decodedSkipSerializer.name() == 'Ada'
        skipDeserializerJson == '{"name":"Ada"}'
        decodedAnyGetterSkipSerializer.name == 'Ada'
        decodedAnyGetterSkipSerializer.attributes == [extra: 'value']

        cleanup:
        context.close()
    }

    void 'test serdeable generated directional skip permits unsupported skipped direction'() {
        given:
        def context = ApplicationContext.run()

        expect:
        assertEligibility(context, SourceGenAnyGetterSkipSerializerShape, false, true)

        cleanup:
        context.close()
    }

    void 'test serdeable generated required reports unsupported include annotation'() {
        when:
        buildContext('test.GeneratedJsonIncludeBean', '''
package test;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.SerdeableGenerated;

@SerdeableGenerated
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeneratedJsonIncludeBean {
    private String name;

    public GeneratedJsonIncludeBean() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        assertRequiredGenerationFailure(e, 'test.GeneratedJsonIncludeBean', 'Include not supported')
    }

    void 'test serdeable generated required reports unsupported unwrapped annotation'() {
        when:
        buildContext('test.GeneratedJsonUnwrappedBean', '''
package test;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.annotation.SerdeableGenerated;

@SerdeableGenerated
public class GeneratedJsonUnwrappedBean {
    private Nested nested;

    public GeneratedJsonUnwrappedBean() {
    }

    @JsonUnwrapped
    public Nested getNested() {
        return nested;
    }

    public void setNested(Nested nested) {
        this.nested = nested;
    }

    @Serdeable
    static class Nested {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        assertRequiredGenerationFailure(e, 'test.GeneratedJsonUnwrappedBean', 'Unwrapped properties not supported')
    }

    void 'test serdeable generated required reports unsupported property order annotation'() {
        when:
        buildContext('test.GeneratedJsonPropertyOrderBean', '''
package test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.SerdeableGenerated;

@SerdeableGenerated
@JsonPropertyOrder({"second", "first"})
public class GeneratedJsonPropertyOrderBean {
    private String first;
    private String second;

    public GeneratedJsonPropertyOrderBean() {
    }

    public String getFirst() {
        return first;
    }

    public void setFirst(String first) {
        this.first = first;
    }

    public String getSecond() {
        return second;
    }

    public void setSecond(String second) {
        this.second = second;
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        assertRequiredGenerationFailure(e, 'test.GeneratedJsonPropertyOrderBean', 'Property order not supported')
    }

    void 'test serdeable generated required reports unsupported enum jackson annotations'() {
        when:
        buildContext('test.GeneratedJsonValueEnum', '''
package test;

import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.SerdeableGenerated;

@SerdeableGenerated
public enum GeneratedJsonValueEnum {
    A,
    B;

    @JsonValue
    public String value() {
        return name().toLowerCase();
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        assertRequiredGenerationFailure(e, 'test.GeneratedJsonValueEnum', 'Annotations not supported: @JsonValue')

        when:
        buildContext('test.GeneratedJsonAnnotatedFieldEnum', '''
package test;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.serde.annotation.SerdeableGenerated;

@SerdeableGenerated
public enum GeneratedJsonAnnotatedFieldEnum {
    A("a"),
    B("b");

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final String label;

    GeneratedJsonAnnotatedFieldEnum(String label) {
        this.label = label;
    }
}
''')

        then:
        def e2 = thrown(RuntimeException)
        assertRequiredGenerationFailure(e2, 'test.GeneratedJsonAnnotatedFieldEnum', 'Annotations not supported: @JsonFormat')
    }

    private void assertEligibility(ApplicationContext context,
                                   Class<?> beanType,
                                   boolean serializerEligible,
                                   boolean deserializerEligible) {
        def serdeIntrospections = context.getBean(SerdeIntrospections)
        def metadata = serdeIntrospections
            .getSerializableIntrospection(Argument.of(beanType))
            .annotationMetadata

        assert metadata.booleanValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_ELIGIBLE).orElse(false) == serializerEligible
        assert metadata.booleanValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_ELIGIBLE).orElse(false) == deserializerEligible

        assertGeneratedClassState(context, metadata, beanType, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS, 'Serializer', serializerEligible)
        assertGeneratedClassState(context, metadata, beanType, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS, 'Deserializer', deserializerEligible)

        def registry = context.getBean(SerdeRegistry)
        Argument argument = Argument.of(beanType)
        assertRegistrySelection(registry, argument, 'Serializer', serializerEligible)
        assertRegistrySelection(registry, argument, 'Deserializer', deserializerEligible)
    }

    private void assertGeneratedClassState(ApplicationContext context,
                                           def metadata,
                                           Class<?> beanType,
                                           String metadataMember,
                                           String suffix,
                                           boolean expectedGenerated) {
        String expectedClassName = generatedClassName(beanType, suffix)
        if (expectedGenerated) {
            assert metadata.stringValue(SerdeConfig, metadataMember).orElse(null) == expectedClassName
            assert context.classLoader.loadClass(expectedClassName) != null
        } else {
            assert !metadata.stringValue(SerdeConfig, metadataMember).present
            assert loadClassOrNull(context, expectedClassName) == null
        }
    }

    private static Class<?> loadClassOrNull(ApplicationContext context, String className) {
        try {
            return context.classLoader.loadClass(className)
        } catch (ClassNotFoundException ignored) {
            return null
        }
    }

    private void assertRegistrySelection(SerdeRegistry registry,
                                         Argument argument,
                                         String suffix,
                                         boolean expectedGenerated) {
        String expectedClassName = generatedClassName(argument.type, suffix)
        String actualClassName
        if (suffix == 'Serializer') {
            Serializer serializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument)
            actualClassName = serializer.class.name
        } else {
            Deserializer deserializer = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)
            actualClassName = deserializer.class.name
        }

        assert (actualClassName == expectedClassName) == expectedGenerated
    }

    private static String generatedClassName(Class<?> type, String suffix) {
        "${type.package.name}.Serde${type.simpleName}${suffix}"
    }

    private static void assertRequiredGenerationFailure(Throwable failure,
                                                        String className,
                                                        String fallbackReason) {
        String message = fullMessage(failure)
        assert message.contains("Source-generated serializer required for ${className}")
        assert message.contains('but generation is not supported')
        assert message.contains("Fallback reasons: ${fallbackReason}")
    }

    private static String fullMessage(Throwable failure) {
        List<String> messages = []
        Throwable current = failure
        while (current != null) {
            messages.add(current.message ?: current.toString())
            current = current.cause
        }
        messages.join('\n')
    }
}
