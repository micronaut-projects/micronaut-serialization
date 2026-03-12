package io.micronaut.serde.jackson.annotation

import io.micronaut.core.type.Argument
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.config.SerdeBackendMode
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.serde.jackson.JsonCompileSpec

class SerdeBackendModeSpec extends JsonCompileSpec {

    void 'test serde backend mode mapper propagation'() {
        given:
        def context = buildContext('test.Test', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.SerdeBackendMode;
import io.micronaut.core.annotation.Introspected;

@Serdeable(backend = SerdeBackendMode.INTROSPECTION)
@Serdeable.Serializable(backend = SerdeBackendMode.GENERATED)
@Serdeable.Deserializable(backend = SerdeBackendMode.AUTO)
@Introspected
record Test(String value) {}
''')

        when:
        def beanType = context.classLoader.loadClass('test.Test')
        def introspection = context.getBean(SerdeIntrospections).getSerializableIntrospection(Argument.of(beanType))

        then:
        introspection != null
        def metadata = introspection.annotationMetadata
        metadata.enumValue(SerdeConfig, SerdeConfig.BACKEND, SerdeBackendMode).orElse(null) == SerdeBackendMode.INTROSPECTION
        metadata.enumValue(SerdeConfig, SerdeConfig.SERIALIZE_BACKEND, SerdeBackendMode).orElse(null) == SerdeBackendMode.GENERATED
        metadata.enumValue(SerdeConfig, SerdeConfig.DESERIALIZE_BACKEND, SerdeBackendMode).orElse(null) == SerdeBackendMode.AUTO
        metadata.booleanValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_ELIGIBLE).orElse(false)
        metadata.booleanValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_ELIGIBLE).orElse(false)

        String serializerClassName = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).orElse(null)
        String deserializerClassName = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
        serializerClassName != null
        deserializerClassName != null
        context.classLoader.loadClass(serializerClassName) != null
        context.classLoader.loadClass(deserializerClassName) != null

        cleanup:
        context.close()
    }
}
