package io.micronaut.serde.jackson.annotation

import io.micronaut.core.type.Argument
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
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
        def serdeRegistry = context.getBean(SerdeRegistry)
        String generatedSerializerClass = generatedClassName(beanType, 'Serializer')
        String generatedDeserializerClass = generatedClassName(beanType, 'Deserializer')

        when:
        def runtimeSerializer = serdeRegistry.findSerializer(type).createSpecific(serdeRegistry.newEncoderContext(Object), type)
        def runtimeDeserializer = serdeRegistry.findDeserializer(type).createSpecific(serdeRegistry.newDecoderContext(Object), type)
        def value = beanType.getDeclaredConstructor(String, int).newInstance('auto', 7)
        String json = jsonMapper.writeValueAsString(value)
        def decoded = jsonMapper.readValue(json, type)

        then:
        context.getBeanDefinitions(Serializer).any { it.beanType.name == generatedSerializerClass }
        context.getBeanDefinitions(Deserializer).any { it.beanType.name == generatedDeserializerClass }
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
        def serdeRegistry = context.getBean(SerdeRegistry)
        String generatedSerializerClass = generatedClassName(beanType, 'Serializer')
        String generatedDeserializerClass = generatedClassName(beanType, 'Deserializer')

        when:
        def runtimeSerializer = serdeRegistry.findSerializer(type).createSpecific(serdeRegistry.newEncoderContext(Object), type)
        def runtimeDeserializer = serdeRegistry.findDeserializer(type).createSpecific(serdeRegistry.newDecoderContext(Object), type)
        def value = beanType.getDeclaredConstructor().newInstance()
        value.name = 'fallback'
        def attrs = new LinkedHashMap<String, Object>()
        attrs.put('extra', 3)
        value.attributes = attrs
        String json = jsonMapper.writeValueAsString(value)
        def decoded = jsonMapper.readValue(json, type)

        then:
        runtimeSerializer.class.name != generatedSerializerClass
        runtimeDeserializer.class.name == generatedDeserializerClass
        context.getBeanDefinitions(Deserializer).any { it.beanType.name == generatedDeserializerClass }
        json == '{"name":"fallback","extra":3}'
        decoded != null

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
}
