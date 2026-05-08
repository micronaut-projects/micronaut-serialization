package io.micronaut.serde.jackson.annotation


import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.FormatConfiguration
import io.micronaut.serde.FormattedDeserializer
import io.micronaut.serde.FormattedSerializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.config.DeserializationConfiguration
import io.micronaut.serde.config.SerializationConfiguration
import io.micronaut.serde.jackson.JsonFormatSpec
import spock.lang.Unroll

import java.time.Instant
import java.time.LocalDateTime

class SerdeJsonFormatSpec extends JsonFormatSpec {

    protected void assertSpecificSerdeSelection(ApplicationContext context,
                                                String className,
                                                boolean serializerGenerated,
                                                boolean deserializerGenerated) {
        Class<?> beanType = context.classLoader.loadClass(className)
        def type = Argument.of(beanType)
        def registry = context.getBean(SerdeRegistry)
        def specificSerializer = registry.findSerializer(type).createSpecific(registry.newEncoderContext(Object), type)
        def specificDeserializer = registry.findDeserializer(type).createSpecific(registry.newDecoderContext(Object), type)
        if (specificSerializer.respondsTo('getSerializer')) {
            specificSerializer = specificSerializer.getSerializer()
        }
        if (specificDeserializer.respondsTo('getDeserializer')) {
            specificDeserializer = specificDeserializer.getDeserializer()
        }

        assert (specificSerializer.class.name == generatedClassName(beanType, 'Serializer')) == serializerGenerated
        assert (specificDeserializer.class.name == generatedClassName(beanType, 'Deserializer')) == deserializerGenerated
    }

    void "test generated enum serdes are format aware and fallback for format"() {
        given:
        def context = buildContext('test.Choice', """
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
enum Choice {
    ALPHA,
    BETA
}
""", true, [:])
        Class<?> enumType = context.classLoader.loadClass('test.Choice')
        def type = Argument.of(enumType)
        def registry = context.getBean(SerdeRegistry)
        def serializer = registry.findSerializer(type)
        def deserializer = registry.findDeserializer(type)
        def format = new FormatConfiguration(null, FormatConfiguration.Shape.NUMBER, null, null, null, FormatConfiguration.DEFAULT_RADIX)
        String generatedSerializerClass = generatedClassName(enumType, 'Serializer')
        String generatedDeserializerClass = generatedClassName(enumType, 'Deserializer')

        expect:
        serializer instanceof FormattedSerializer
        deserializer instanceof FormattedDeserializer
        serializer.createSpecific(registry.newEncoderContext(Object), type).class.name == generatedSerializerClass
        deserializer.createSpecific(registry.newDecoderContext(Object), type).class.name == generatedDeserializerClass
        ((FormattedSerializer) serializer).createSpecific(registry.newEncoderContext(Object), type, format).class.name != generatedSerializerClass
        ((FormattedDeserializer) deserializer).createSpecific(registry.newDecoderContext(Object), type, format).class.name != generatedDeserializerClass

        cleanup:
        context.close()
    }

    private static String generatedClassName(Class<?> type, String suffix) {
        String packageName = type.package.name
        String localName = type.name
        if (packageName) {
            localName = localName.substring(packageName.length() + 1)
        }
        "${packageName ? packageName + '.' : ''}Serde${localName.replace('.', '_').replace('$', '_')}${suffix}"
    }

    void "test generated serde fallback util with custom object serde"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.GeneratedSerdeFallbackUtil;
import jakarta.inject.Singleton;
import java.io.IOException;

@Serdeable
class Test {
    @Serdeable.Serializable(using = GeneratedLikePayloadSerde.class)
    @Serdeable.Deserializable(using = GeneratedLikePayloadSerde.class)
    private Payload payload;

    public Payload getPayload() {
        return payload;
    }

    public void setPayload(Payload payload) {
        this.payload = payload;
    }
}

@Serdeable
class Payload {
    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

@Singleton
class GeneratedLikePayloadSerde implements Serializer<Payload>, Deserializer<Payload> {
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Serializer<Payload> createSpecific(EncoderContext context,
                                              Argument<? extends Payload> type) throws SerdeException {
        return (Serializer<Payload>) GeneratedSerdeFallbackUtil.withRuntimeObjectFallback((Serializer<?>) this, context, (Argument) type);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Deserializer<Payload> createSpecific(DecoderContext context,
                                                Argument<? super Payload> type) throws SerdeException {
        return (Deserializer<Payload>) GeneratedSerdeFallbackUtil.withRuntimeObjectFallback((Deserializer<?>) this, context, (Argument) type);
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends Payload> type,
                          Payload value) throws IOException {
        encoder.encodeString("generated:" + value.getValue());
    }

    @Override
    public Payload deserialize(Decoder decoder,
                               DecoderContext context,
                               Argument<? super Payload> type) throws IOException {
        throw new IOException("Generated-like deserializer should have fallen back");
    }
}
""", [:], ['micronaut.serde.deserialization.accept-case-insensitive-properties': true])
        beanUnderTest = newInstance(context, 'test.Test', [
            payload: newInstance(context, 'test.Payload', [value: 'alpha'])
        ])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"payload":"generated:alpha"}'
        jsonMapper.readValue('{"payload":{"VALUE":"beta"}}', typeUnderTest).payload.value == 'beta'

        cleanup:
        context.close()
    }

    void "test generated serde fallback util with custom enum serde"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.GeneratedSerdeFallbackUtil;
import jakarta.inject.Singleton;
import java.io.IOException;

@Serdeable
class Test {
    @JsonFormat(with = {
        JsonFormat.Feature.WRITE_SORTED_MAP_ENTRIES,
        JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES
    })
    @Serdeable.Serializable(using = GeneratedLikeChoiceSerde.class)
    @Serdeable.Deserializable(using = GeneratedLikeChoiceSerde.class)
    private Choice choice;

    public Choice getChoice() {
        return choice;
    }

    public void setChoice(Choice choice) {
        this.choice = choice;
    }
}

@Serdeable
enum Choice {
    ALPHA,
    BETA
}

@Singleton
class GeneratedLikeChoiceSerde implements Serializer<Choice>, Deserializer<Choice> {
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Serializer<Choice> createSpecific(EncoderContext context,
                                             Argument<? extends Choice> type) throws SerdeException {
        return (Serializer<Choice>) GeneratedSerdeFallbackUtil.withRuntimeEnumFallback((Serializer<?>) this, context, (Argument) type);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Deserializer<Choice> createSpecific(DecoderContext context,
                                               Argument<? super Choice> type) throws SerdeException {
        return (Deserializer<Choice>) GeneratedSerdeFallbackUtil.withRuntimeEnumFallback((Deserializer<?>) this, context, (Argument) type);
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends Choice> type,
                          Choice value) throws IOException {
        encoder.encodeString("generated:" + value.name());
    }

    @Override
    public Choice deserialize(Decoder decoder,
                              DecoderContext context,
                              Argument<? super Choice> type) throws IOException {
        throw new IOException("Generated-like enum deserializer should have fallen back");
    }
}
""", [:])
        beanUnderTest = newInstance(context, 'test.Test', [
            choice: getEnum(context, 'test.Choice.ALPHA')
        ])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"choice":"ALPHA"}'
        jsonMapper.readValue('{"choice":"beta"}', typeUnderTest).choice == getEnum(context, 'test.Choice.BETA')

        cleanup:
        context.close()
    }

    @Unroll
    void "test json format #shape shape keeps micronaut char representation for #typeName"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.$shape)
    private $typeName value;
    public void setValue($typeName value) {
        this.value = value;
    }
    public $typeName getValue() {
        return value;
    }
}
""", [value: (char) 'a'])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":97}'
        jsonMapper.readValue('{"value":98}', typeUnderTest).value == (char) 'b'

        cleanup:
        context.close()

        where:
        typeName    | shape
        'char'      | 'OBJECT'
        'char'      | 'POJO'
        'Character' | 'OBJECT'
        'Character' | 'POJO'
    }

    void "test json format configuration passed to format-aware serde"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormattedDeserializer;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Set;

@Serdeable
class Test {
    @JsonFormat(
        pattern = "fmt",
        shape = JsonFormat.Shape.STRING,
        locale = "de_DE",
        timezone = "UTC",
        lenient = OptBoolean.FALSE,
        radix = 16,
        with = {
            JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
            JsonFormat.Feature.WRITE_DATES_WITH_ZONE_ID,
            JsonFormat.Feature.WRITE_SORTED_MAP_ENTRIES
        },
        without = {
            JsonFormat.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS,
            JsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS
        }
    )
    @Serdeable.Serializable(using = FormatAwareValueSerde.class)
    @Serdeable.Deserializable(using = FormatAwareValueSerde.class)
    private FormatAwareValue value;

    public Test() {
    }

    public Test(FormatAwareValue value) {
        this.value = value;
    }

    public FormatAwareValue getValue() {
        return value;
    }

    public void setValue(FormatAwareValue value) {
        this.value = value;
    }
}

@Serdeable
record FormatAwareValue(String value) {
}

@Singleton
class FormatAwareValueSerde implements FormattedSerializer<FormatAwareValue>, FormattedDeserializer<FormatAwareValue> {
    private final FormatConfiguration format;
    private final Set<?> features;

    FormatAwareValueSerde() {
        this(null, Set.of());
    }

    private FormatAwareValueSerde(FormatConfiguration format, Set<?> features) {
        this.format = format;
        this.features = features;
    }

    @Override
    public Serializer<FormatAwareValue> createSpecific(EncoderContext context,
                                                       Argument<? extends FormatAwareValue> type,
                                                       FormatConfiguration format) throws SerdeException {
        return new FormatAwareValueSerde(format, context.getFeatures());
    }

    @Override
    public Deserializer<FormatAwareValue> createSpecific(DecoderContext context,
                                                         Argument<? super FormatAwareValue> type,
                                                         FormatConfiguration format) throws SerdeException {
        return new FormatAwareValueSerde(format, context.getFeatures());
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends FormatAwareValue> type,
                          FormatAwareValue value) throws IOException {
        validateFormat();
        validateSerializationFeatures();
        encoder.encodeString("formatted:" + value.value());
    }

    @Override
    public FormatAwareValue deserialize(Decoder decoder,
                                        DecoderContext context,
                                        Argument<? super FormatAwareValue> type) throws IOException {
        validateFormat();
        validateDeserializationFeatures();
        return new FormatAwareValue(decoder.decodeString().replace("formatted:", ""));
    }

    private void validateFormat() throws IOException {
        if (format == null
            || !"fmt".equals(format.pattern())
            || format.shape() != FormatConfiguration.Shape.STRING
            || !"de_DE".equals(format.locale())
            || !"UTC".equals(format.timezone())
            || !Boolean.FALSE.equals(format.lenient())
            || format.radix() != 16) {
            throw new IOException("Unexpected format: " + format);
        }
    }

    private void validateSerializationFeatures() throws IOException {
        if (!features.equals(Set.of(
            SerializationConfiguration.Feature.WRITE_DATES_WITH_ZONE_ID,
            SerializationConfiguration.Feature.WRITE_SORTED_MAP_ENTRIES
        ))) {
            throw new IOException("Unexpected serialization features: " + features);
        }
    }

    private void validateDeserializationFeatures() throws IOException {
        if (!features.equals(Set.of(DeserializationConfiguration.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY))) {
            throw new IOException("Unexpected deserialization features: " + features);
        }
    }
}
""")
        beanUnderTest = newInstance(context, 'test.Test', [value: newInstance(context, 'test.FormatAwareValue', 'abc')])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":"formatted:abc"}'
        jsonMapper.readValue('{"value":"formatted:xyz"}', typeUnderTest).value.value() == 'xyz'

        cleanup:
        context.close()
    }

    void "test json format feature only annotation applies features without format configuration"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedDeserializer;
import io.micronaut.serde.FormattedSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Set;

@Serdeable
class Test {
    @JsonFormat(with = {
        JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
        JsonFormat.Feature.WRITE_DATES_WITH_ZONE_ID
    })
    @Serdeable.Serializable(using = FeatureOnlyValueSerde.class)
    @Serdeable.Deserializable(using = FeatureOnlyValueSerde.class)
    private FeatureOnlyValue value;

    public Test() {
    }

    public Test(FeatureOnlyValue value) {
        this.value = value;
    }

    public FeatureOnlyValue getValue() {
        return value;
    }

    public void setValue(FeatureOnlyValue value) {
        this.value = value;
    }
}

@Serdeable
record FeatureOnlyValue(String value) {
}

@Singleton
class FeatureOnlyValueSerde implements FormattedSerializer<FeatureOnlyValue>, FormattedDeserializer<FeatureOnlyValue> {
    private final Set<?> features;

    FeatureOnlyValueSerde() {
        this(Set.of());
    }

    private FeatureOnlyValueSerde(Set<?> features) {
        this.features = features;
    }

    @Override
    public Serializer<FeatureOnlyValue> createSpecific(EncoderContext context,
                                                       Argument<? extends FeatureOnlyValue> type) {
        return new FeatureOnlyValueSerde(context.getFeatures());
    }

    @Override
    public Deserializer<FeatureOnlyValue> createSpecific(DecoderContext context,
                                                         Argument<? super FeatureOnlyValue> type) {
        return new FeatureOnlyValueSerde(context.getFeatures());
    }

    @Override
    public Serializer<FeatureOnlyValue> createSpecific(EncoderContext context,
                                                       Argument<? extends FeatureOnlyValue> type,
                                                       FormatConfiguration format) throws SerdeException {
        throw new SerdeException("Feature-only metadata must not create a format configuration: " + format);
    }

    @Override
    public Deserializer<FeatureOnlyValue> createSpecific(DecoderContext context,
                                                         Argument<? super FeatureOnlyValue> type,
                                                         FormatConfiguration format) throws SerdeException {
        throw new SerdeException("Feature-only metadata must not create a format configuration: " + format);
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends FeatureOnlyValue> type,
                          FeatureOnlyValue value) throws IOException {
        if (!features.contains(SerializationConfiguration.Feature.WRITE_DATES_WITH_ZONE_ID)) {
            throw new IOException("Unexpected serialization features: " + features);
        }
        encoder.encodeString(value.value());
    }

    @Override
    public FeatureOnlyValue deserialize(Decoder decoder,
                                        DecoderContext context,
                                        Argument<? super FeatureOnlyValue> type) throws IOException {
        if (!features.contains(DeserializationConfiguration.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)) {
            throw new IOException("Unexpected deserialization features: " + features);
        }
        return new FeatureOnlyValue(decoder.decodeString());
    }
}
""")
        beanUnderTest = newInstance(context, 'test.Test', [value: newInstance(context, 'test.FeatureOnlyValue', 'abc')])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":"abc"}'
        jsonMapper.readValue('{"value":"xyz"}', typeUnderTest).value.value() == 'xyz'

        cleanup:
        context.close()
    }

    void "test serde configurations expose active format features"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
}
""", [:], [
            'micronaut.serde.serialization.write-date-timestamps-as-nanoseconds': false,
            'micronaut.serde.serialization.write-dates-with-zone-id': true,
            'micronaut.serde.serialization.write-single-elem-arrays-unwrapped': true,
            'micronaut.serde.serialization.write-sorted-map-entries': true,
            'micronaut.serde.deserialization.accept-single-value-as-array': true,
            'micronaut.serde.deserialization.accept-case-insensitive-properties': true,
            'micronaut.serde.deserialization.read-unknown-enum-values-as-null': true,
            'micronaut.serde.deserialization.read-unknown-enum-values-using-default-value': true,
            'micronaut.serde.deserialization.read-date-timestamps-as-nanoseconds': false,
            'micronaut.serde.deserialization.accept-case-insensitive-enums': true,
            'micronaut.serde.deserialization.adjust-dates-to-context-time-zone': true
        ])

        when:
        def serializationFeatures = SerializationConfiguration.features(context.getBean(SerializationConfiguration))
        def deserializationFeatures = context.getBean(DeserializationConfiguration).features()

        then:
        serializationFeatures == Set.of(
            SerializationConfiguration.Feature.WRITE_DATES_WITH_ZONE_ID,
            SerializationConfiguration.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED,
            SerializationConfiguration.Feature.WRITE_SORTED_MAP_ENTRIES
        )
        deserializationFeatures == Set.of(
            DeserializationConfiguration.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
            DeserializationConfiguration.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES,
            DeserializationConfiguration.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL,
            DeserializationConfiguration.Feature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE,
            DeserializationConfiguration.Feature.ACCEPT_CASE_INSENSITIVE_VALUES,
            DeserializationConfiguration.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
        )

        cleanup:
        context.close()
    }

    void "test global json format write single element arrays unwrapped and annotation override"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
class Test {
    private List<String> value;
    @JsonFormat(without = JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
    private List<String> explicitArray;
    public List<String> getValue() {
        return value;
    }
    public void setValue(List<String> value) {
        this.value = value;
    }
    public List<String> getExplicitArray() {
        return explicitArray;
    }
    public void setExplicitArray(List<String> explicitArray) {
        this.explicitArray = explicitArray;
    }
}
""", [
            value: ['alpha'],
            explicitArray: ['beta']
        ], ['micronaut.serde.serialization.write-single-elem-arrays-unwrapped': true])

        expect:
        validateJsonWithoutOrder(jsonMapper, '{"value":"alpha","explicitArray":["beta"]}', writeJson(jsonMapper, beanUnderTest))

        cleanup:
        context.close()
    }

    void "test object fallback property serialization feature overrides do not leak"() {
        given:
        def context = buildContext('test.Holder', """
package test;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
class Holder {
    @JsonFormat(with = JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
    private List<String> aUnwrapped;
    private List<String> zArray;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String zzMarker;
    public List<String> getAUnwrapped() {
        return aUnwrapped;
    }
    public void setAUnwrapped(List<String> aUnwrapped) {
        this.aUnwrapped = aUnwrapped;
    }
    public List<String> getZArray() {
        return zArray;
    }
    public void setZArray(List<String> zArray) {
        this.zArray = zArray;
    }
    public String getZzMarker() {
        return zzMarker;
    }
    public void setZzMarker(String zzMarker) {
        this.zzMarker = zzMarker;
    }
}
""", [
            aUnwrapped: ['alpha'],
            zArray: ['beta']
        ])

        expect:
        validateJsonWithoutOrder(jsonMapper, '{"aUnwrapped":"alpha","zArray":["beta"]}', writeJson(jsonMapper, beanUnderTest))

        cleanup:
        context.close()
    }

    void "test global json format accept single value as array and annotation override"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
class Test {
    private List<String> value;
    @JsonFormat(without = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> explicitArray;
    public List<String> getValue() {
        return value;
    }
    public void setValue(List<String> value) {
        this.value = value;
    }
    public List<String> getExplicitArray() {
        return explicitArray;
    }
    public void setExplicitArray(List<String> explicitArray) {
        this.explicitArray = explicitArray;
    }
}
""", [:], ['micronaut.serde.deserialization.accept-single-value-as-array': true])

        expect:
        jsonMapper.readValue('{"value":"alpha"}', typeUnderTest).value == ['alpha']

        when:
        jsonMapper.readValue('{"explicitArray":"beta"}', typeUnderTest)

        then:
        thrown(Exception)

        cleanup:
        context.close()
    }

    void "test global json format sorted map entries and annotation override"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@Serdeable
class Test {
    private Map<Integer, String> value;
    @JsonFormat(without = JsonFormat.Feature.WRITE_SORTED_MAP_ENTRIES)
    private Map<Integer, String> insertionOrder;
    public Map<Integer, String> getValue() {
        return value;
    }
    public void setValue(Map<Integer, String> value) {
        this.value = value;
    }
    public Map<Integer, String> getInsertionOrder() {
        return insertionOrder;
    }
    public void setInsertionOrder(Map<Integer, String> insertionOrder) {
        this.insertionOrder = insertionOrder;
    }
}
""", [
            value: [(2): 'two', (10): 'ten'],
            insertionOrder: [(2): 'two', (1): 'one']
        ], ['micronaut.serde.serialization.write-sorted-map-entries': true])

        when:
        def json = writeJson(jsonMapper, beanUnderTest)

        then:
        json.contains('"value":{"2":"two","10":"ten"}')
        json.contains('"insertionOrder":{"2":"two","1":"one"}')

        cleanup:
        context.close()
    }

    void "test global json format case insensitive properties and annotation override"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private String value;
    @JsonFormat(without = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    private Strict strict;
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }
    public Strict getStrict() {
        return strict;
    }
    public void setStrict(Strict strict) {
        this.strict = strict;
    }
}

@Serdeable
class Strict {
    private String name;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}
""", [:], [
            'micronaut.serde.deserialization.accept-case-insensitive-properties': true,
            'micronaut.serde.deserialization.ignore-unknown': false
        ])

        expect:
        jsonMapper.readValue('{"VALUE":"alpha"}', typeUnderTest).value == 'alpha'

        when:
        jsonMapper.readValue('{"strict":{"NAME":"beta"}}', typeUnderTest)

        then:
        thrown(Exception)

        cleanup:
        context.close()
    }

    void "test global json format unknown enum values as null"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

@Serdeable
enum Choice {
    ALPHA,
    BETA
}

@Serdeable
class Test {
    @Nullable
    private Choice value;
    public Choice getValue() {
        return value;
    }
    public void setValue(Choice value) {
        this.value = value;
    }
}
""", [:], ['micronaut.serde.deserialization.read-unknown-enum-values-as-null': true])

        expect:
        jsonMapper.readValue('{"value":"GAMMA"}', typeUnderTest).value == null

        cleanup:
        context.close()
    }

    void "test global json format unknown enum values using default value"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
enum Choice {
    ALPHA,
    BETA,
    @JsonEnumDefaultValue
    UNKNOWN
}

@Serdeable
class Test {
    private Choice value;
    public Choice getValue() {
        return value;
    }
    public void setValue(Choice value) {
        this.value = value;
    }
}
""", [:], ['micronaut.serde.deserialization.read-unknown-enum-values-using-default-value': true])
        def defaultValue = getEnum(context, 'test.Choice.UNKNOWN')

        expect:
        jsonMapper.readValue('{"value":"GAMMA"}', typeUnderTest).value == defaultValue

        cleanup:
        context.close()
    }

    void "test global json format enum features apply when property format is present"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
enum Choice {
    ALPHA,
    BETA,
    @JsonEnumDefaultValue
    UNKNOWN
}

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Choice value;
    public Choice getValue() {
        return value;
    }
    public void setValue(Choice value) {
        this.value = value;
    }
}
""", [:], [
            'micronaut.serde.deserialization.accept-case-insensitive-enums': true,
            'micronaut.serde.deserialization.read-unknown-enum-values-using-default-value': true
        ])
        def beta = getEnum(context, 'test.Choice.BETA')
        def defaultValue = getEnum(context, 'test.Choice.UNKNOWN')

        expect:
        jsonMapper.readValue('{"value":"beta"}', typeUnderTest).value == beta
        jsonMapper.readValue('{"value":"GAMMA"}', typeUnderTest).value == defaultValue

        cleanup:
        context.close()
    }

    void "test global json format date timestamp nanoseconds features"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

@Serdeable
class Test {
    private Instant value;
    public Instant getValue() {
        return value;
    }
    public void setValue(Instant value) {
        this.value = value;
    }
}
""", [value: Instant.ofEpochSecond(123, 456789123)], [
            'micronaut.serde.time-write-shape': 'INTEGER',
            'micronaut.serde.serialization.write-date-timestamps-as-nanoseconds': false,
            'micronaut.serde.deserialization.read-date-timestamps-as-nanoseconds': false
        ])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":123456}'
        jsonMapper.readValue('{"value":123456}', typeUnderTest).value == Instant.ofEpochMilli(123456)

        cleanup:
        context.close()
    }

    void "test json format enum case insensitive values overrides global config"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
enum Choice {
    ALPHA,
    BETA
}

@Serdeable
class Test {
    @JsonFormat(without = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)
    private Choice value;
    public void setValue(Choice value) {
        this.value = value;
    }
    public Choice getValue() {
        return value;
    }
}
""", [:], ['micronaut.serde.deserialization.accept-case-insensitive-enums': true])

        when:
        jsonMapper.readValue('{"value":"beta"}', typeUnderTest)

        then:
        thrown(Exception)

        cleanup:
        context.close()
    }

    void "test json format write date timestamps nanos overrides global numeric unit"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

@Serdeable
class Test {
    @JsonFormat(with = JsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
    private Instant nanos;
    @JsonFormat(without = JsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
    private Instant millis;
    public void setNanos(Instant nanos) {
        this.nanos = nanos;
    }
    public Instant getNanos() {
        return nanos;
    }
    public void setMillis(Instant millis) {
        this.millis = millis;
    }
    public Instant getMillis() {
        return millis;
    }
}
""", [nanos: Instant.ofEpochSecond(123, 456789123), millis: Instant.ofEpochSecond(123, 456789123)],
            [
                'micronaut.serde.time-write-shape': 'INTEGER',
                'micronaut.serde.numeric-time-unit': 'MILLISECONDS'
            ])

        expect:
        validateJsonWithoutOrder(jsonMapper, '{"nanos":123,"millis":123456}', writeJson(jsonMapper, beanUnderTest))

        cleanup:
        context.close()
    }

    void "test json format read date timestamps nanos overrides global numeric unit"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

@Serdeable
class Test {
    @JsonFormat(with = JsonFormat.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
    private Instant nanos;
    @JsonFormat(without = JsonFormat.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
    private Instant millis;
    public void setNanos(Instant nanos) {
        this.nanos = nanos;
    }
    public Instant getNanos() {
        return nanos;
    }
    public void setMillis(Instant millis) {
        this.millis = millis;
    }
    public Instant getMillis() {
        return millis;
    }
}
""", [:], ['micronaut.serde.numeric-time-unit': 'MILLISECONDS'])

        when:
        def read = jsonMapper.readValue('{"nanos":123,"millis":123456}', typeUnderTest)

        then:
        read.nanos == Instant.ofEpochSecond(123)
        read.millis == Instant.ofEpochMilli(123456)

        cleanup:
        context.close()
    }

    void "test disable validation"() {
        when:
        def i = buildBeanIntrospection('jsongetterrecord.Test', """
package jsongetterrecord;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;


@Serdeable(validate=false)
record Test(
    @JsonFormat(pattern="bunch 'o junk")
    int value) {
}
""")

        then:
        i != null
    }

    @Unroll
    void "test fail compilation when invalid format applied to number for type #type"() {
        when:
        buildBeanIntrospection('jsongetterrecord.Test', """
package jsongetterrecord;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;


@Serdeable
record Test(
    @JsonFormat(pattern="bunch 'o junk")
    $type.name value) {
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Specified pattern [bunch 'o junk] is not a valid decimal format. See the javadoc for DecimalFormat: Malformed pattern \"bunch 'o junk\"")

        where:
        type << [Integer, int.class]
    }

    @Unroll
    void "test fail compilation when invalid format applied to date for type #type"() {
        when:
        buildBeanIntrospection('jsongetterrecord.Test', """
package jsongetterrecord;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;


@Serdeable
record Test(
    @JsonFormat(pattern="bunch 'o junk")
    $type.name value) {
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Specified pattern [bunch 'o junk] is not a valid date format. See the javadoc for DateTimeFormatter: Unknown pattern letter: b")

        where:
        type << [LocalDateTime]
    }

    @Unroll
    void "test json format for #type and settings #settings with record"() {
        expect:
        assertJsonFormatForNumberSettingsWithRecord(type, value, settings, result)

        where:
        variation << jsonFormatNumberSettings()
        type = variation.type
        value = variation.value
        settings = variation.settings
        result = variation.result
    }

    @Unroll
    void "test json format for #type and settings #settings"() {
        expect:
        assertJsonFormatForNumberSettings(type, value, settings, result)

        where:
        variation << jsonFormatNumberSettings()
        type = variation.type
        value = variation.value
        settings = variation.settings
        result = variation.result
    }

    @Unroll
    void "INSTANT + SQL DATE test json format for date #type and settings #settings"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;

@Serdeable
class Test {
    @JsonFormat(${settings.collect { "$it.key=\"$it.value\"" }.join(",")})
    private $type.name value;
    public void setValue($type.name value) {
        this.value = value;
    }
    public $type.name getValue() {
        return value;
    }
}
""", [value: value])
        def result = writeJson(jsonMapper, beanUnderTest)
        def read = jsonMapper.readValue(result, typeUnderTest)

        expect:
        result.startsWith('{"value":"') // was serialized as string, not long
        typeUnderTest.type.isInstance(read)
        resolver(read.value) == resolver(value)

        cleanup:
        context.close()

        where:
        type           | value                                     | settings                                | resolver
        Instant        | Instant.now()                             | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ"] | { Instant i -> i.toEpochMilli() }
        java.sql.Date  | new java.sql.Date(2021, 9, 15)            | [pattern: "yyyy-MM-dd"]                 | { java.sql.Date d -> d }
    }

}
