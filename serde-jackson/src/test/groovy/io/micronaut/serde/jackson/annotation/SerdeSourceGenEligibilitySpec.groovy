package io.micronaut.serde.jackson.annotation

import io.micronaut.core.type.Argument
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.config.annotation.SerdeConfig
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
        assertEligibility(context, 'test.TestRecord', 'RECORD', true, true, [] as Set, [] as Set)
        assertEligibility(context, 'test.TestBean', 'DEFAULT_CONSTRUCTOR_BEAN', true, true, [] as Set, [] as Set)
        assertEligibility(context, 'test.TestEnum', 'ENUM', true, true, [] as Set, [] as Set)

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
        assertEligibility(context, 'test.AnyGetterBean', 'DEFAULT_CONSTRUCTOR_BEAN', false, true, ['ANY_GETTER'] as Set, [] as Set)
        assertEligibility(context, 'test.AnySetterBean', 'DEFAULT_CONSTRUCTOR_BEAN', true, false, [] as Set, ['ANY_SETTER'] as Set)

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
        assertEligibility(context, 'test.UnwrappedBean', 'DEFAULT_CONSTRUCTOR_BEAN', false, false, ['UNWRAPPED'] as Set, ['UNWRAPPED'] as Set)
        assertEligibility(context, 'test.SubtypedBean', 'DEFAULT_CONSTRUCTOR_BEAN', false, false, ['SUBTYPED'] as Set, ['SUBTYPED'] as Set)
        assertEligibility(context, 'test.DelegatingCreatorRecord', 'RECORD', false, false, ['COMPLEX_CREATOR'] as Set, ['COMPLEX_CREATOR'] as Set)

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
        assertEligibility(context, 'test.JsonValueEnum', 'ENUM', false, false, ['COMPLEX_ENUM'] as Set, ['COMPLEX_ENUM'] as Set)

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
        assertEligibility(context, 'test.JsonIncludeTypeBean', 'DEFAULT_CONSTRUCTOR_BEAN', false, false, ['INCLUDE'] as Set, ['INCLUDE'] as Set)
        assertEligibility(context, 'test.JsonIncludePropertyBean', 'DEFAULT_CONSTRUCTOR_BEAN', false, false, ['INCLUDE'] as Set, ['INCLUDE'] as Set)

        cleanup:
        context.close()
    }

    private void assertEligibility(def context,
                                   String className,
                                   String shape,
                                   boolean serializerEligible,
                                   boolean deserializerEligible,
                                   Set<String> serializerReasons,
                                   Set<String> deserializerReasons) {
        Class<?> beanType = context.classLoader.loadClass(className)
        def serdeIntrospections = context.getBean(SerdeIntrospections)
        def serializableMetadata = serdeIntrospections
            .getSerializableIntrospection(Argument.of(beanType))
            .annotationMetadata
        def deserializableMetadata = serdeIntrospections
            .getDeserializableIntrospection(Argument.of(beanType))
            .annotationMetadata

        assert serializableMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SHAPE).orElse(null) == shape
        assert serializableMetadata.booleanValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_ELIGIBLE).orElse(false) == serializerEligible
        assert serializableMetadata.booleanValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_ELIGIBLE).orElse(false) == deserializerEligible
        assert serializableMetadata.stringValues(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_FALLBACK_REASONS).toSet() == serializerReasons
        assert serializableMetadata.stringValues(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_FALLBACK_REASONS).toSet() == deserializerReasons
        assert deserializableMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SHAPE).orElse(null) == shape
        assert deserializableMetadata.booleanValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_ELIGIBLE).orElse(false) == serializerEligible
        assert deserializableMetadata.booleanValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_ELIGIBLE).orElse(false) == deserializerEligible
        assert deserializableMetadata.stringValues(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_FALLBACK_REASONS).toSet() == serializerReasons
        assert deserializableMetadata.stringValues(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_FALLBACK_REASONS).toSet() == deserializerReasons

        if (serializerEligible) {
            String serializerClassName = serializableMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).orElse(null)
            assert serializerClassName != null
            assert context.classLoader.loadClass(serializerClassName) != null
        } else {
            assert !serializableMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).present
        }

        if (deserializerEligible) {
            String deserializerClassName = serializableMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
            assert deserializerClassName != null
            assert context.classLoader.loadClass(deserializerClassName) != null
        } else {
            assert !serializableMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).present
        }
    }
}
