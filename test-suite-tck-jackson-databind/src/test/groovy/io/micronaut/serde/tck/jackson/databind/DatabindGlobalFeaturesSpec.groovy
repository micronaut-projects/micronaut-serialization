package io.micronaut.serde.tck.jackson.databind

import io.micronaut.serde.jackson.GlobalFeaturesSpec

class DatabindGlobalFeaturesSpec extends GlobalFeaturesSpec {

    @Override
    protected Map<String, Object> writeSingleElementArraysUnwrappedConfig() {
        ['jackson.serialization-features.write-single-elem-arrays-unwrapped': true]
    }

    @Override
    protected Map<String, Object> acceptSingleValueAsArrayConfig() {
        ['jackson.deserialization-features.accept-single-value-as-array': true]
    }

    @Override
    protected Map<String, Object> writeSortedMapEntriesConfig() {
        ['jackson.serialization-features.order-map-entries-by-keys': true]
    }

    @Override
    protected Map<String, Object> acceptCaseInsensitivePropertiesConfig() {
        [
            'jackson.mapper-features.accept-case-insensitive-properties': true,
            'jackson.deserialization-features.fail-on-unknown-properties': true
        ]
    }

    @Override
    protected Map<String, Object> readUnknownEnumValuesAsNullConfig() {
        ['jackson.enum-features.read-unknown-enum-values-as-null': true]
    }

    @Override
    protected Map<String, Object> readUnknownEnumValuesUsingDefaultValueConfig() {
        ['jackson.enum-features.read-unknown-enum-values-using-default-value': true]
    }

    @Override
    protected Map<String, Object> acceptCaseInsensitiveEnumValuesConfig() {
        ['jackson.mapper-features.accept-case-insensitive-enums': true]
    }

    @Override
    protected Map<String, Object> dateTimestampNanosecondsDisabledConfig() {
        [
            'jackson.date-time-features.write-dates-as-timestamps': true,
            'jackson.date-time-features.write-date-timestamps-as-nanoseconds': false,
            'jackson.date-time-features.read-date-timestamps-as-nanoseconds': false
        ]
    }

    @Override
    protected Map<String, Object> writeDatesWithZoneIdConfig() {
        [
            'jackson.date-time-features.write-dates-as-timestamps': false,
            'jackson.date-time-features.write-dates-with-zone-id': true
        ]
    }

    @Override
    protected Map<String, Object> adjustDatesToContextTimeZoneConfig() {
        [
            'jackson.date-time-features.adjust-dates-to-context-time-zone': true,
            'jackson.time-zone': 'UTC'
        ]
    }
}
