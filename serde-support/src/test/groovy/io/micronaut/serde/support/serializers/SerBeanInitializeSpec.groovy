package io.micronaut.serde.support.serializers

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.config.DeserializationConfiguration
import io.micronaut.serde.config.SerializationConfiguration
import io.micronaut.serde.support.MyRecord
import io.micronaut.serde.support.deserializers.DeserBean
import io.micronaut.serde.support.deserializers.ObjectDeserializer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

@MicronautTest
class SerBeanInitializeSpec extends Specification {

    @Inject
    SerdeRegistry serdeRegistry

    @Inject
    SerdeIntrospections introspections

    @Inject
    ObjectDeserializer objectDeserializer

    @Inject
    SerializationConfiguration serializationConfiguration

    @Inject
    DeserializationConfiguration deserializationConfiguration

    void "initialize on an initialized SerBean does not query bean annotation metadata"() {
        given:
        def counter = new AtomicInteger()
        def type = Argument.of(MyRecord)
        def counting = countingIntrospection(introspections.getSerializableIntrospection(type), counter)
        def countingIntrospections = Stub(SerdeIntrospections) {
            getSerializableIntrospection(_) >> counting
        }
        def context = serdeRegistry.newEncoderContext(Object)
        def lock = new ReentrantLock()
        def serBean = new SerBean<MyRecord>(type, countingIntrospections, context, null, serializationConfiguration, null)
        serBean.initialize(lock, context)

        when:
        counter.set(0)
        serBean.initialize(lock, context)
        serBean.initialize(lock, context)

        then:
        counter.get() == 0
    }

    void "initialize on an initialized DeserBean does not query bean annotation metadata"() {
        given:
        def counter = new AtomicInteger()
        def type = Argument.of(MyRecord)
        BeanIntrospection<MyRecord> counting = countingIntrospection(introspections.getDeserializableIntrospection(type), counter)
        def context = serdeRegistry.newDecoderContext(Object)
        def lock = new ReentrantLock()
        def deserBean = new DeserBean<MyRecord>(deserializationConfiguration, [:], counting, context, objectDeserializer, null)
        deserBean.initialize(lock, context)

        when:
        counter.set(0)
        deserBean.initialize(lock, context)
        deserBean.initialize(lock, context)

        then:
        counter.get() == 0
    }

    private static <T> BeanIntrospection<T> countingIntrospection(BeanIntrospection<T> delegate, AtomicInteger counter) {
        (BeanIntrospection<T>) Proxy.newProxyInstance(
            SerBeanInitializeSpec.classLoader,
            [BeanIntrospection] as Class[],
            { proxy, method, args ->
                if (method.name == 'getAnnotationMetadata') {
                    counter.incrementAndGet()
                }
                method.invoke(delegate, args)
            } as InvocationHandler
        )
    }
}
