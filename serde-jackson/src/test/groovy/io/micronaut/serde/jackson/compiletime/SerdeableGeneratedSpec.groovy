package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.SerdeIntrospections
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

    void 'test serdeable generated directional skip permits unsupported skipped direction'() {
        given:
        def context = ApplicationContext.run()

        expect:
        assertEligibility(context, SourceGenAnyGetterSkipSerializerShape, false, true)

        cleanup:
        context.close()
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

    private static String generatedClassName(Class<?> type, String suffix) {
        "${type.package.name}.Serde${type.simpleName}${suffix}"
    }
}
