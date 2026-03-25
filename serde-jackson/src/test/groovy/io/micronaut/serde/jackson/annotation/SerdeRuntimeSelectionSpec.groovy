package io.micronaut.serde.jackson.annotation

import io.micronaut.core.type.Argument
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.serde.jackson.JsonCompileSpec

class SerdeRuntimeSelectionSpec extends JsonCompileSpec {

    void 'test generated metadata prefers generated runtime serde when available'() {
        given:
        def context = buildContext('test.AutoShape', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected
public record AutoShape(String name, int count) {}
''')
        Class<?> beanType = context.classLoader.loadClass('test.AutoShape')
        def type = Argument.of(beanType)
        def introspections = context.getBean(SerdeIntrospections)
        def metadata = introspections.getSerializableIntrospection(type).annotationMetadata
        String generatedSerializerClass = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).orElse(null)
        String generatedDeserializerClass = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
        def serdeRegistry = jsonMapper.serdeRegistry

        when:
        def runtimeSerializer = serdeRegistry.findSerializer(type).createSpecific(serdeRegistry.newEncoderContext(Object), type)
        def runtimeDeserializer = serdeRegistry.findDeserializer(type).createSpecific(serdeRegistry.newDecoderContext(Object), type)
        def value = beanType.getDeclaredConstructor(String, int).newInstance('auto', 7)
        String json = jsonMapper.writeValueAsString(value)
        def decoded = jsonMapper.readValue(json, type)

        then:
        generatedSerializerClass != null
        generatedDeserializerClass != null
        context.findBean(context.classLoader.loadClass(generatedSerializerClass)).present
        context.findBean(context.classLoader.loadClass(generatedDeserializerClass)).present
        runtimeSerializer.class.name == generatedSerializerClass
        runtimeDeserializer.class.name == generatedDeserializerClass
        json == '{"name":"auto","count":7}'
        decoded == value

        cleanup:
        context.close()
    }

    void 'test ineligible serializer metadata falls back to introspection serializer while keeping generated deserializer'() {
        given:
        def context = buildContext('test.AnyGetterShape', '''
package test;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected
public class AnyGetterShape {
    private String name;
    private java.util.Map<String, Object> attributes = new java.util.LinkedHashMap<>();

    public AnyGetterShape() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonAnyGetter
    public java.util.Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(java.util.Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('test.AnyGetterShape')
        def type = Argument.of(beanType)
        def introspections = context.getBean(SerdeIntrospections)
        def metadata = introspections.getSerializableIntrospection(type).annotationMetadata
        String generatedSerializerClass = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).orElse(null)
        String generatedDeserializerClass = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
        def serdeRegistry = jsonMapper.serdeRegistry

        when:
        def runtimeSerializer = serdeRegistry.findSerializer(type).createSpecific(serdeRegistry.newEncoderContext(Object), type)
        def runtimeDeserializer = serdeRegistry.findDeserializer(type).createSpecific(serdeRegistry.newDecoderContext(Object), type)
        def value = beanType.getDeclaredConstructor().newInstance()
        beanType.getMethod('setName', String).invoke(value, 'fallback')
        def attrs = new LinkedHashMap<String, Object>()
        attrs.put('extra', 3)
        beanType.getMethod('setAttributes', Map).invoke(value, attrs)
        String json = jsonMapper.writeValueAsString(value)
        def decoded = jsonMapper.readValue(json, type)

        then:
        !metadata.booleanValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_ELIGIBLE).orElse(true)
        metadata.booleanValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_ELIGIBLE).orElse(false)
        generatedSerializerClass == null
        generatedDeserializerClass != null
        context.findBean(context.classLoader.loadClass(generatedDeserializerClass)).present
        runtimeSerializer.class.name.startsWith('io.micronaut.serde.support.')
        runtimeDeserializer.class.name == generatedDeserializerClass
        json == '{"name":"fallback","extra":3}'
        decoded != null

        cleanup:
        context.close()
    }
}
