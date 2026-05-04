package io.micronaut.serde.jackson.annotation

import io.micronaut.core.type.Argument
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.jackson.JsonCompileSpec

class SerdeSourceGenRoutingSpec extends JsonCompileSpec {

    private static final String COMPLEX_FIXTURES = '''
package test;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

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

@Serdeable
@Introspected
@JsonInclude(NON_NULL)
class JsonIncludeBean {
    private String value;

    public JsonIncludeBean() {
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
'''

    void 'test sourcegen routing picks generated or introspection backend per direction for complex fixtures'() {
        given:
        def context = buildContext('test.AnyGetterBean', COMPLEX_FIXTURES)

        expect:
        assertRouting(
            context,
            'test.AnyGetterBean',
            false,
            true
        )
        assertRouting(
            context,
            'test.AnySetterBean',
            true,
            false
        )
        assertRouting(
            context,
            'test.UnwrappedBean',
            false,
            false
        )
        assertRouting(
            context,
            'test.SubtypedBean',
            false,
            false
        )
        assertRouting(
            context,
            'test.DelegatingCreatorRecord',
            false,
            false
        )
        assertRouting(
            context,
            'test.JsonValueEnum',
            false,
            false
        )
        assertRouting(
            context,
            'test.JsonIncludeBean',
            false,
            false
        )

        cleanup:
        context.close()
    }

    private void assertRouting(def context,
                               String className,
                               boolean serializerGenerated,
                               boolean deserializerGenerated) {
        Class<?> beanType = context.classLoader.loadClass(className)
        def type = Argument.of(beanType)
        SerdeRegistry serdeRegistry = context.getBean(SerdeRegistry)
        Serializer resolvedSerializer = serdeRegistry.findSerializer(type).createSpecific(serdeRegistry.newEncoderContext(Object), type)
        Deserializer resolvedDeserializer = serdeRegistry.findDeserializer(type).createSpecific(serdeRegistry.newDecoderContext(Object), type)

        assert (resolvedSerializer.class.name == generatedClassName(beanType, 'Serializer')) == serializerGenerated
        assert (resolvedDeserializer.class.name == generatedClassName(beanType, 'Deserializer')) == deserializerGenerated
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
