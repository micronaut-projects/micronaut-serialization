package io.micronaut.serde.jackson.annotation

import io.micronaut.core.type.Argument
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.jackson.JsonCompileSpec

class SerdeSourceGenEligibilitySpec extends JsonCompileSpec {

    void 'test simple shapes are sourcegen eligible'() {
        given:
        def context = buildContext('test.TestRecord', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

@Serdeable
@Introspected
public record TestRecord(String value) {}

@Serdeable
@Introspected
class TestBean {
    private String value;

    public TestBean() {
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

@Serdeable
@Introspected
enum TestEnum {
    A,
    B
}
''')

        expect:
        assertRegistrySelection(context, 'test.TestRecord', true, true)
        assertRegistrySelection(context, 'test.TestBean', true, true)
        assertRegistrySelection(context, 'test.TestEnum', true, true)

        cleanup:
        context.close()
    }

    void 'test camel case names and declaration order do not affect eligibility'() {
        given:
        def context = buildContext('test.CamelCaseBean', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

@Serdeable
@Introspected
class CamelCaseBean {
    private String firstName;
    private String lastName;

    public CamelCaseBean() {
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
}

@Serdeable
@Introspected
record UnorderedRecord(int c, int a, int b) {
}
''')

        expect:
        assertRegistrySelection(context, 'test.CamelCaseBean', true, true)
        assertRegistrySelection(context, 'test.UnorderedRecord', true, true)

        cleanup:
        context.close()
    }

    void 'test any-getter and any-setter are directional fallback reasons'() {
        given:
        def context = buildContext('test.AnyGetterBean', '''
package test;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

import java.util.LinkedHashMap;
import java.util.Map;

@Serdeable
@Introspected
class AnyGetterBean {
    private String value;
    private Map<String, Object> additional = new LinkedHashMap<>();

    public AnyGetterBean() {
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditional() {
        return additional;
    }

    public void setAdditional(Map<String, Object> additional) {
        this.additional = additional;
    }
}

@Serdeable
@Introspected
class AnySetterBean {
    private String value;

    public AnySetterBean() {
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @JsonAnySetter
    public void putAdditional(String name, Object value) {
    }
}
''')

        expect:
        assertRegistrySelection(context, 'test.AnyGetterBean', false, true)
        assertRegistrySelection(context, 'test.AnySetterBean', true, false)

        cleanup:
        context.close()
    }

    void 'test unwrapped subtyped and delegating creator fallback reasons are emitted'() {
        given:
        def context = buildContext('test.UnwrappedBean', '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

@Serdeable
@Introspected
class UnwrappedBean {
    @JsonUnwrapped
    private Nested nested;

    public UnwrappedBean() {
    }

    public Nested getNested() {
        return nested;
    }

    public void setNested(Nested nested) {
        this.nested = nested;
    }

    @Serdeable
    @Introspected
    static class Nested {
        private String value;

        public Nested() {
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}

@Serdeable
@Introspected
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(@JsonSubTypes.Type(value = SubtypedBeanImpl.class, name = "impl"))
class SubtypedBean {
    private String value;

    public SubtypedBean() {
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

@Serdeable
@Introspected
class SubtypedBeanImpl extends SubtypedBean {
}

@Serdeable
@Introspected
record DelegatingCreatorRecord(String value) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public DelegatingCreatorRecord {
    }
}
''')

        expect:
        assertRegistrySelection(context, 'test.UnwrappedBean', false, false)
        assertRegistrySelection(context, 'test.SubtypedBean', false, false)
        assertRegistrySelection(context, 'test.DelegatingCreatorRecord', false, false)

        cleanup:
        context.close()
    }

    void 'test enum json value customization falls back from sourcegen fast path'() {
        given:
        def context = buildContext('test.JsonValueEnum', '''
package test;

import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

@Serdeable
@Introspected
enum JsonValueEnum {
    A,
    B;

    @JsonValue
    public String value() {
        return name().toLowerCase();
    }
}
''')

        expect:
        assertRegistrySelection(context, 'test.JsonValueEnum', false, false)

        cleanup:
        context.close()
    }

    void 'test json format annotations fall back from sourcegen fast path where required'() {
        given:
        def context = buildContext('test.JsonFormatBean', '''
package test;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;
import java.util.List;

@Serdeable
@Introspected
@JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
class JsonFormatBean {
    private String value;

    public JsonFormatBean() {
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

@Serdeable
@Introspected
@JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
record JsonFormatRecord(String value) {
}

@Serdeable
@Introspected
@JsonFormat(shape = JsonFormat.Shape.NUMBER)
enum JsonFormatEnum {
    A,
    B
}

@Serdeable
@Introspected
enum PlainEnum {
    A,
    B
}

@Serdeable
@Introspected
class JsonFormatPropertyHolder {
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private PlainEnum value;
    @JsonFormat(with = JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
    private List<String> values;

    public JsonFormatPropertyHolder() {
    }

    public PlainEnum getValue() {
        return value;
    }

    public void setValue(PlainEnum value) {
        this.value = value;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }
}
''')

        expect:
        assertRegistrySelection(context, 'test.JsonFormatBean', false, false)
        assertRegistrySelection(context, 'test.JsonFormatRecord', false, false)
        assertRegistrySelection(context, 'test.JsonFormatEnum', false, false)
        assertRegistrySelection(context, 'test.PlainEnum', true, true)
        assertRegistrySelection(context, 'test.JsonFormatPropertyHolder', false, false)

        cleanup:
        context.close()
    }

    void 'test json include usage falls back from sourcegen fast path'() {
        given:
        def context = buildContext('test.JsonIncludeTypeBean', '''
package test;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Serdeable
@Introspected
@JsonInclude(NON_NULL)
class JsonIncludeTypeBean {
    private String value;

    public JsonIncludeTypeBean() {
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

@Serdeable
@Introspected
class JsonIncludePropertyBean {
    private String value;

    public JsonIncludePropertyBean() {
    }

    @JsonInclude(NON_NULL)
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
''')

        expect:
        assertRegistrySelection(context, 'test.JsonIncludeTypeBean', false, false)
        assertRegistrySelection(context, 'test.JsonIncludePropertyBean', false, false)

        cleanup:
        context.close()
    }

    void 'test supported jackson dataformat xml property annotations use sourcegen'() {
        given:
        def context = buildContext('test.XmlWrapperBean', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import tools.jackson.dataformat.xml.annotation.JacksonXmlCData;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;
import java.util.List;

@Serdeable
class XmlWrapperBean {
    @JacksonXmlElementWrapper(localName = "items")
    private List<String> items;

    public XmlWrapperBean() {}

    public List<String> getItems() { return items; }

    public void setItems(List<String> items) { this.items = items; }
}

@Serdeable
class XmlPropertyBean {
    @JacksonXmlProperty(localName = "username", isAttribute = true)
    private String name;

    public XmlPropertyBean() {}

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }
}

@Serdeable
@JacksonXmlRootElement(localName = "product", namespace = "http://example.com/products")
class XmlRootBean {
    private String title;

    public XmlRootBean() {}

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }
}

@Serdeable
class XmlTextBean {
    @JacksonXmlText
    private String content;
    private String label;

    public XmlTextBean() {}

    public String getContent() { return content; }

    public void setContent(String content) { this.content = content; }

    public String getLabel() { return label; }

    public void setLabel(String label) { this.label = label; }
}

@Serdeable
class XmlCDataBean {
    @JacksonXmlCData
    private String content;

    public XmlCDataBean() {}

    public String getContent() { return content; }

    public void setContent(String content) { this.content = content; }
}

@Serdeable
record XmlTextRecord(
    @JacksonXmlText @JacksonXmlCData String content
) {}

@Serdeable
record XmlWrapperRecord(
    @JacksonXmlElementWrapper(localName = "items", namespace = "urn:wrapper") List<String> items
) {}
''')

        expect:
        assertRegistrySelection(context, 'test.XmlWrapperBean', true, true)
        assertRegistrySelection(context, 'test.XmlPropertyBean', true, true)
        assertRegistrySelection(context, 'test.XmlRootBean', false, false)
        assertRegistrySelection(context, 'test.XmlTextBean', true, true)
        assertRegistrySelection(context, 'test.XmlCDataBean', true, true)
        assertRegistrySelection(context, 'test.XmlTextRecord', true, true)
        assertRegistrySelection(context, 'test.XmlWrapperRecord', true, true)

        cleanup:
        context.close()
    }

    void 'test custom serializer and deserializer declared on the type are not sourcegen eligible'() {
        given:
        def context = buildContext('test.CustomSerdePayload', '''
package test;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;
import java.io.IOException;

@Serdeable
@Introspected
@Serdeable.Serializable(using = CustomSerde.class)
@Serdeable.Deserializable(using = CustomSerde.class)
class CustomSerdePayload {
    private String value;

    public CustomSerdePayload() {
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

@Singleton
class CustomSerde implements Serializer<CustomSerdePayload>, Deserializer<CustomSerdePayload> {
    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends CustomSerdePayload> type, CustomSerdePayload value) throws IOException {
        encoder.encodeString("custom");
    }

    @Override
    public CustomSerdePayload deserialize(Decoder decoder, DecoderContext context, Argument<? super CustomSerdePayload> type) throws IOException {
        decoder.skipValue();
        CustomSerdePayload payload = new CustomSerdePayload();
        payload.setValue("custom");
        return payload;
    }
}
''')

        expect:
        assertRegistrySelection(context, 'test.CustomSerdePayload', false, false)

        cleanup:
        context.close()
    }

    void 'test custom serializer and deserializer inherited from an interface are not sourcegen eligible'() {
        given:
        def context = buildContext('test.VersionedPayload', '''
package test;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;
import java.io.IOException;

@Serdeable
@Serdeable.Serializable(using = VersionedSerde.class)
@Serdeable.Deserializable(using = VersionedSerde.class)
interface Versioned {
    String getValue();
}

@Serdeable
@Introspected
class VersionedPayload implements Versioned {
    private String value;

    public VersionedPayload() {
    }

    @Override
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

@Singleton
class VersionedSerde<T extends Versioned> implements Serializer<T>, Deserializer<T> {
    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        encoder.encodeString("custom");
    }

    @Override
    public T deserialize(Decoder decoder, DecoderContext context, Argument<? super T> type) throws IOException {
        decoder.skipValue();
        VersionedPayload payload = new VersionedPayload();
        payload.setValue("custom");
        return (T) payload;
    }
}
''')

        expect:
        assertRegistrySelection(context, 'test.VersionedPayload', false, false)

        when: 'the custom serde declared on the interface is applied to the implementation'
        def customSerde = context.classLoader.loadClass('test.VersionedSerde')
        Class<?> beanType = context.classLoader.loadClass('test.VersionedPayload')
        Argument argument = Argument.of(beanType)
        SerdeRegistry registry = context.getBean(SerdeRegistry)
        Serializer serializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument)
        Deserializer deserializer = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)

        then:
        customSerde.isInstance(serializer)
        customSerde.isInstance(deserializer)

        cleanup:
        context.close()
    }

    private static void assertRegistrySelection(def context,
                                                String className,
                                                boolean serializerGenerated,
                                                boolean deserializerGenerated) {
        Class<?> beanType = context.classLoader.loadClass(className)
        Argument argument = Argument.of(beanType)
        SerdeRegistry registry = context.getBean(SerdeRegistry)
        Serializer serializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument)
        Deserializer deserializer = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)

        assert (serializer.class.name == generatedClassName(beanType, 'Serializer')) == serializerGenerated
        assert (deserializer.class.name == generatedClassName(beanType, 'Deserializer')) == deserializerGenerated
    }

    private static String generatedClassName(Class<?> type, String suffix) {
        String packageName = type.package.name
        String localName = type.name
        if (packageName) {
            localName = localName.substring(packageName.length() + 1)
        }
        "${packageName ? packageName + '.' : ''}Serde${localName.replace('.', '_').replace('$', '_')}${suffix}"
    }
}
