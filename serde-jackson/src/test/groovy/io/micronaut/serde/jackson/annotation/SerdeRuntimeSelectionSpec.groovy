package io.micronaut.serde.jackson.annotation

import io.micronaut.core.type.Argument
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.config.SerdeBackendMode
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.serde.jackson.JsonCompileSpec

class SerdeRuntimeSelectionSpec extends JsonCompileSpec {

    void 'test AUTO mode prefers generated runtime serde when available'() {
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
        runtimeSerializer.class.name == generatedSerializerClass
        runtimeDeserializer.class.name == generatedDeserializerClass
        json == '{"name":"auto","count":7}'
        decoded == value

        cleanup:
        context.close()
    }

    void 'test global INTROSPECTION backend mode rolls back generated runtime serde selection'() {
        given:
        def context = buildContext('test.RollbackShape', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected
public record RollbackShape(String name, int count) {}
''', true, ['micronaut.serde.backend-mode': 'INTROSPECTION'])
        Class<?> beanType = context.classLoader.loadClass('test.RollbackShape')
        def type = Argument.of(beanType)
        def introspections = context.getBean(SerdeIntrospections)
        def metadata = introspections.getSerializableIntrospection(type).annotationMetadata
        String generatedSerializerClass = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).orElse(null)
        String generatedDeserializerClass = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
        def serdeRegistry = jsonMapper.serdeRegistry

        when:
        def runtimeSerializer = serdeRegistry.findSerializer(type).createSpecific(serdeRegistry.newEncoderContext(Object), type)
        def runtimeDeserializer = serdeRegistry.findDeserializer(type).createSpecific(serdeRegistry.newDecoderContext(Object), type)
        def value = beanType.getDeclaredConstructor(String, int).newInstance('rollback', 3)
        String json = jsonMapper.writeValueAsString(value)
        def decoded = jsonMapper.readValue(json, type)

        then:
        generatedSerializerClass != null
        generatedDeserializerClass != null
        runtimeSerializer.class.name != generatedSerializerClass
        runtimeDeserializer.class.name != generatedDeserializerClass
        runtimeSerializer.class.name.startsWith('io.micronaut.serde.support.')
        runtimeDeserializer.class.name.startsWith('io.micronaut.serde.support.')
        json == '{"name":"rollback","count":3}'
        decoded == value

        cleanup:
        context.close()
    }

    void 'test directional backend overrides use generated serializer and introspection deserializer'() {
        given:
        def context = buildContext('test.DirectionShape', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.SerdeBackendMode;

@Serdeable
@Serdeable.Serializable(backend = SerdeBackendMode.GENERATED)
@Serdeable.Deserializable(backend = SerdeBackendMode.INTROSPECTION)
@Introspected
public record DirectionShape(String name, int count) {}
''')
        Class<?> beanType = context.classLoader.loadClass('test.DirectionShape')
        def type = Argument.of(beanType)
        def introspections = context.getBean(SerdeIntrospections)
        def metadata = introspections.getSerializableIntrospection(type).annotationMetadata
        String generatedSerializerClass = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).orElse(null)
        String generatedDeserializerClass = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
        def serdeRegistry = jsonMapper.serdeRegistry

        when:
        def runtimeSerializer = serdeRegistry.findSerializer(type).createSpecific(serdeRegistry.newEncoderContext(Object), type)
        def runtimeDeserializer = serdeRegistry.findDeserializer(type).createSpecific(serdeRegistry.newDecoderContext(Object), type)
        def value = beanType.getDeclaredConstructor(String, int).newInstance('direction', 9)
        String json = jsonMapper.writeValueAsString(value)
        def decoded = jsonMapper.readValue(json, type)

        then:
        metadata.enumValue(SerdeConfig, SerdeConfig.SERIALIZE_BACKEND, SerdeBackendMode).orElse(null) == SerdeBackendMode.GENERATED
        metadata.enumValue(SerdeConfig, SerdeConfig.DESERIALIZE_BACKEND, SerdeBackendMode).orElse(null) == SerdeBackendMode.INTROSPECTION
        generatedSerializerClass != null
        generatedDeserializerClass != null
        runtimeSerializer.class.name == generatedSerializerClass
        runtimeDeserializer.class.name != generatedDeserializerClass
        runtimeDeserializer.class.name.startsWith('io.micronaut.serde.support.')
        json == '{"name":"direction","count":9}'
        decoded == value

        cleanup:
        context.close()
    }
}
