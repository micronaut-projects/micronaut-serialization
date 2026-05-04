package io.micronaut.serde.jackson.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.jackson.GlobalFeaturesSpec

class SerdeGlobalFeaturesSpec extends GlobalFeaturesSpec {

    protected void assertSpecificSerdeSelection(ApplicationContext context,
                                                String className,
                                                boolean serializerGenerated,
                                                boolean deserializerGenerated) {
        Class<?> beanType = context.classLoader.loadClass(className)
        def type = Argument.of(beanType)
        def registry = context.getBean(SerdeRegistry)
        def specificSerializer = registry.findSerializer(type).createSpecific(registry.newEncoderContext(Object), type)
        def specificDeserializer = registry.findDeserializer(type).createSpecific(registry.newDecoderContext(Object), type)

        assert (specificSerializer.class.name == generatedClassName(beanType, 'Serializer')) == serializerGenerated
        assert (specificDeserializer.class.name == generatedClassName(beanType, 'Deserializer')) == deserializerGenerated
    }

    private static String generatedClassName(Class<?> type, String suffix) {
        String packageName = type.package.name
        String localName = type.name
        if (packageName) {
            localName = localName.substring(packageName.length() + 1)
        }
        "${packageName ? packageName + '.' : ''}Serde${localName.replace('.', '_').replace('$', '_')}${suffix}"
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
