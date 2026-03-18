package io.micronaut.serde.jackson.annotation

import io.micronaut.core.type.Argument
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.config.annotation.SerdeConfig
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
            true,
            ['CustomizedObjectSerializer'],
            []
        )
        assertRouting(
            context,
            'test.AnySetterBean',
            true,
            false,
            [],
            ['SpecificObjectDeserializer']
        )
        assertRouting(
            context,
            'test.UnwrappedBean',
            false,
            false,
            [],
            ['SpecificObjectDeserializer'],
            false,
            true
        )
        assertRouting(
            context,
            'test.SubtypedBean',
            false,
            false,
            ['RuntimeTypeSerializer'],
            ['SubtypedPropertyObjectDeserializer']
        )
        assertRouting(
            context,
            'test.DelegatingCreatorRecord',
            false,
            false,
            ['CustomizedObjectSerializer'],
            ['DelegatingObjectDeserializer']
        )
        assertRouting(
            context,
            'test.JsonValueEnum',
            false,
            false,
            [],
            ['EnumValueDeserializer']
        )
        assertRouting(
            context,
            'test.JsonIncludeBean',
            false,
            false,
            [],
            []
        )

        cleanup:
        context.close()
    }

    private void assertRouting(def context,
                               String className,
                               boolean serializerEligible,
                               boolean deserializerEligible,
                               List<String> fallbackSerializerMarkers,
                               List<String> fallbackDeserializerMarkers,
                               boolean assertSerializerSpecificClass = true,
                               boolean assertDeserializerSpecificClass = true) {
        Class<?> beanType = context.classLoader.loadClass(className)
        def type = Argument.of(beanType)
        def introspections = context.getBean(SerdeIntrospections)
        def serializableMetadata = introspections.getSerializableIntrospection(type).annotationMetadata
        def deserializableMetadata = introspections.getDeserializableIntrospection(type).annotationMetadata
        String generatedSerializerClass = serializableMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).orElse(null)
        String generatedDeserializerClass = deserializableMetadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)

        assert serializableMetadata.booleanValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_ELIGIBLE).orElse(false) == serializerEligible
        assert serializableMetadata.booleanValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_ELIGIBLE).orElse(false) == deserializerEligible
        assert deserializableMetadata.booleanValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_ELIGIBLE).orElse(false) == serializerEligible
        assert deserializableMetadata.booleanValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_ELIGIBLE).orElse(false) == deserializerEligible

        def serdeRegistry = jsonMapper.serdeRegistry
        def resolvedSerializer = serdeRegistry.findSerializer(type)
        def resolvedDeserializer = serdeRegistry.findDeserializer(type)

        if (serializerEligible) {
            assert generatedSerializerClass != null
            assert resolvedSerializer.class.name == generatedSerializerClass
        } else {
            assert generatedSerializerClass == null
            assert resolvedSerializer.class.name.startsWith('io.micronaut.serde.support.')
            if (assertSerializerSpecificClass) {
                def runtimeSerializer = resolvedSerializer.createSpecific(serdeRegistry.newEncoderContext(Object), type)
                List<String> serializerClassChain = unwrapClassChain(runtimeSerializer, ['serializer', 'outer'])
                assert serializerClassChain.any { it.startsWith('io.micronaut.serde.support.') }
                assert fallbackSerializerMarkers.every { marker -> serializerClassChain.any { classNameInChain -> classNameInChain.contains(marker) } } : "Unexpected serializer chain for ${className}: ${serializerClassChain}"
            }
        }

        if (deserializerEligible) {
            assert generatedDeserializerClass != null
            assert resolvedDeserializer.class.name == generatedDeserializerClass
        } else {
            assert generatedDeserializerClass == null
            assert resolvedDeserializer.class.name.startsWith('io.micronaut.serde.support.')
            if (assertDeserializerSpecificClass) {
                def runtimeDeserializer = resolvedDeserializer.createSpecific(serdeRegistry.newDecoderContext(Object), type)
                List<String> deserializerClassChain = unwrapClassChain(runtimeDeserializer, ['deserializer'])
                assert deserializerClassChain.any { it.startsWith('io.micronaut.serde.support.') }
                assert fallbackDeserializerMarkers.every { marker -> deserializerClassChain.any { classNameInChain -> classNameInChain.contains(marker) } } : "Unexpected deserializer chain for ${className}: ${deserializerClassChain}"
            }
        }
    }

    private static List<String> unwrapClassChain(Object candidate, List<String> fieldNames) {
        List<String> chain = []
        Set<String> visited = [] as Set
        Object current = candidate
        while (current != null) {
            String currentClassName = current.class.name
            chain.add(currentClassName)
            if (!visited.add(currentClassName + '@' + System.identityHashCode(current))) {
                break
            }
            Object next = null
            for (String fieldName : fieldNames) {
                next = readField(current, fieldName)
                if (next != null) {
                    break
                }
            }
            if (next == null || next.is(current)) {
                break
            }
            current = next
        }
        return chain
    }

    private static Object readField(Object instance, String fieldName) {
        Class<?> current = instance.class
        while (current != null && current != Object) {
            try {
                def field = current.getDeclaredField(fieldName)
                field.accessible = true
                return field.get(instance)
            } catch (NoSuchFieldException ignored) {
                current = current.superclass
            }
        }
        return null
    }
}
