package io.micronaut.serde.jackson.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.serde.jackson.GlobalFeaturesSpec

class SerdeGlobalFeaturesSpec extends GlobalFeaturesSpec {

    protected void assertSpecificSerdeSelection(ApplicationContext context,
                                                String className,
                                                boolean serializerGenerated,
                                                boolean deserializerGenerated) {
        Class<?> beanType = context.classLoader.loadClass(className)
        def type = Argument.of(beanType)
        def introspections = context.getBean(SerdeIntrospections)
        def serializableMetadata = introspections.getSerializableIntrospection(type).annotationMetadata
        def deserializableMetadata = introspections.getDeserializableIntrospection(type).annotationMetadata
        String generatedSerializerClass = serializableMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).orElse(null)
        String generatedDeserializerClass = deserializableMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
        def registry = jsonMapper.serdeRegistry
        def specificSerializer = registry.findSerializer(type).createSpecific(registry.newEncoderContext(Object), type)
        def specificDeserializer = registry.findDeserializer(type).createSpecific(registry.newDecoderContext(Object), type)

        if (serializerGenerated) {
            assert generatedSerializerClass != null
            assert specificSerializer.class.name == generatedSerializerClass
        } else {
            assert generatedSerializerClass == null || specificSerializer.class.name != generatedSerializerClass
        }
        if (deserializerGenerated) {
            assert generatedDeserializerClass != null
            assert specificDeserializer.class.name == generatedDeserializerClass
        } else {
            assert generatedDeserializerClass == null || specificDeserializer.class.name != generatedDeserializerClass
        }
    }

    @Override
    protected Map<String, Object> writeSingleElementArraysUnwrappedConfig() {
        ['micronaut.serde.serialization.write-single-elem-arrays-unwrapped': true]
    }

    @Override
    protected Map<String, Object> acceptSingleValueAsArrayConfig() {
        ['micronaut.serde.deserialization.accept-single-value-as-array': true]
    }

    @Override
    protected Map<String, Object> writeSortedMapEntriesConfig() {
        ['micronaut.serde.serialization.write-sorted-map-entries': true]
    }

    @Override
    protected Map<String, Object> acceptCaseInsensitivePropertiesConfig() {
        [
            'micronaut.serde.deserialization.accept-case-insensitive-properties': true,
            'micronaut.serde.deserialization.ignore-unknown': false
        ]
    }

    @Override
    protected Map<String, Object> readUnknownEnumValuesAsNullConfig() {
        ['micronaut.serde.deserialization.read-unknown-enum-values-as-null': true]
    }

    @Override
    protected Map<String, Object> readUnknownEnumValuesUsingDefaultValueConfig() {
        ['micronaut.serde.deserialization.read-unknown-enum-values-using-default-value': true]
    }

    @Override
    protected Map<String, Object> acceptCaseInsensitiveEnumValuesConfig() {
        ['micronaut.serde.deserialization.accept-case-insensitive-enums': true]
    }

    @Override
    protected Map<String, Object> dateTimestampNanosecondsDisabledConfig() {
        [
            'micronaut.serde.time-write-shape': 'INTEGER',
            'micronaut.serde.serialization.write-date-timestamps-as-nanoseconds': false,
            'micronaut.serde.deserialization.read-date-timestamps-as-nanoseconds': false
        ]
    }

    @Override
    protected Map<String, Object> writeDatesWithZoneIdConfig() {
        ['micronaut.serde.serialization.write-dates-with-zone-id': true]
    }

    @Override
    protected Map<String, Object> adjustDatesToContextTimeZoneConfig() {
        [
            'micronaut.serde.deserialization.adjust-dates-to-context-time-zone': true,
            'micronaut.serde.time-zone': 'UTC'
        ]
    }
}
