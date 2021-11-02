package io.micronaut.serde.jackson

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.core.beans.exceptions.IntrospectionException
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.reflect.InstantiationUtils
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.SerdeIntrospections
import org.intellij.lang.annotations.Language

import java.lang.reflect.Field

class JsonCompileSpec extends AbstractTypeElementSpec implements JsonSpec {

    JsonMapper jsonMapper
    Object beanUnderTest
    Argument<?> typeUnderTest

    ApplicationContext buildContext(String className, @Language("java") String source, Map<String, Object> properties) {
        ApplicationContext context =
                buildContext(className, source, true)

        setupSerdeRegistry(context)
        jsonMapper = context.getBean(JsonMapper)

        def t = context.classLoader
                .loadClass(className)
        typeUnderTest = Argument.of(t)
        beanUnderTest = t.newInstance(properties)
        return context
    }

    Object newInstance(ApplicationContext context, String name, Map args) {
        return context.classLoader.loadClass(name).newInstance(args)
    }

    Object newInstance(ApplicationContext context, String name, Object[] args) {
        return context.classLoader.loadClass(name).newInstance(args)
    }

    Argument<Object> argumentOf(ApplicationContext context, String name) {
        return Argument.of(context.classLoader.loadClass(name))
    }

    @Override
    ApplicationContext buildContext(@Language("java") String source) {
        ApplicationContext context =
                buildContext("test.Source" + System.currentTimeMillis(), source, true)


        setupSerdeRegistry(context)
        jsonMapper = context.getBean(JsonMapper)
        return context
    }

    protected void setupSerdeRegistry(ApplicationContext context) {
        def classLoader = context.classLoader
        context.registerSingleton(SerdeIntrospections, new SerdeIntrospections() {

            @Override
            def <T> Collection<BeanIntrospection<? extends T>> findSubtypeDeserializables(@NonNull Class<T> type) {
                // horrible hack this
                def field = ReflectionUtils.findField(classLoader.getClass(), "classes")
                        .get()
                field.setAccessible(true)
                List<Class> types = field.get(classLoader)

                return types.findAll { BeanIntrospectionReference.class.isAssignableFrom(it)}
                    .collect() {
                        (BeanIntrospectionReference) InstantiationUtils.instantiate(it)
                    }.findAll { it.beanType != type && type.isAssignableFrom(it.beanType) }
                    .collect { it.load() }
            }

            @Override
            def <T> BeanIntrospection<T> getSerializableIntrospection(@NonNull Argument<T> type) {
                try {
                    return classLoader.loadClass(NameUtils.getPackageName(type.type.name) + ".\$" + type.type.simpleName + '$Introspection')
                            .newInstance()
                } catch (ClassNotFoundException e) {
                    throw new IntrospectionException("No introspection")
                }
            }

            @Override
            def <T> BeanIntrospection<T> getDeserializableIntrospection(@NonNull Argument<T> type) {
                try {
                    return classLoader.loadClass(NameUtils.getPackageName(type.type.name) + ".\$" + type.type.simpleName + '$Introspection')
                            .newInstance()
                } catch (ClassNotFoundException e) {
                    throw new IntrospectionException("No introspection for type $type")
                }
            }
        })
    }
}
