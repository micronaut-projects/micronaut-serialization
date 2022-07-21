package io.micronaut.serde

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.reflect.InstantiationUtils
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.core.type.Argument
import io.micronaut.serde.support.DefaultSerdeIntrospections
import jakarta.inject.Singleton

import javax.tools.JavaFileObject

@Singleton
@Replaces(DefaultSerdeIntrospections.class)
class MockSerdeIntrospections extends DefaultSerdeIntrospections {
    private final ClassLoader classLoader

    MockSerdeIntrospections(ApplicationContext context) {
        this.classLoader = context.getClassLoader()
    }

    @Override
    def <T> Collection<BeanIntrospection<? extends T>> findSubtypeDeserializables(@NonNull Class<T> type) {
        // horrible hack this
        if (isInvalidClassLoader()) {
            return super.findSubtypeDeserializables(type)
        }
        def f = ReflectionUtils.getRequiredField(classLoader.getClass(), "files")
        f.setAccessible(true)
        def files = f.get(classLoader)
        def resolvedTypes = files.findAll({ JavaFileObject jfo ->
            jfo.name.endsWith('$IntrospectionRef.class')
        }).collect( { JavaFileObject jfo ->
            String className = jfo.name.substring(14, jfo.name.length() - 6).replace('/', '.')
            classLoader.loadClass(className)
        })


        def list = resolvedTypes
                .collect() {
                    (BeanIntrospectionReference) InstantiationUtils.instantiate(it)
                }.findAll { it.beanType != type && type.isAssignableFrom(it.beanType) }
                .collect { it.load() }
        if (list) {
            return list
        } else {
            return super.findSubtypeDeserializables(type)
        }
    }

    @Override
    def <T> BeanIntrospection<T> getSerializableIntrospection(@NonNull Argument<T> type) {
        if (isInvalidClassLoader()) {
            return super.getSerializableIntrospection(type)
        }
        try {
            return classLoader.loadClass(NameUtils.getPackageName(type.type.name) + ".\$" + type.type.simpleName + '$Introspection')
                    .newInstance()
        } catch (ClassNotFoundException e) {
            return super.getSerializableIntrospection(type)
        }
    }

    private boolean isInvalidClassLoader() {
        !(classLoader.getClass().getSimpleName().equals("JavaFileObjectClassLoader"))
    }

    @Override
    def <T> BeanIntrospection<T> getDeserializableIntrospection(@NonNull Argument<T> type) {
        if (isInvalidClassLoader()) {
            return super.getDeserializableIntrospection(type)
        }
        try {
            return classLoader.loadClass(NameUtils.getPackageName(type.type.name) + ".\$" + type.type.simpleName + '$Introspection')
                    .newInstance()
        } catch (ClassNotFoundException e) {
            return super.getDeserializableIntrospection(type)
        }
    }
}
