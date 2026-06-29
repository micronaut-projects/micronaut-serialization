package io.micronaut.serde.yaml

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.exceptions.IntrospectionException
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.type.Argument
import io.micronaut.serde.SerdeIntrospections
import org.intellij.lang.annotations.Language

class YamlCompileSpec extends AbstractTypeElementSpec implements YamlSpec {

    YamlObjectMapper yamlObjectMapper
    Object beanUnderTest
    Argument<?> typeUnderTest

    ApplicationContext buildContext(String className, @Language("java") String source, Map<String, Object> properties) {
        return buildContext(className, source, properties, [:])
    }

    ApplicationContext buildContext(String className, @Language("java") String source, Map<String, Object> properties, Map contextProperties) {
        ApplicationContext context = buildContext(className, source, true, contextProperties)


        def t = context.classLoader.loadClass(className)
        typeUnderTest = Argument.of(t)
        beanUnderTest = properties.isEmpty() ? null : t.newInstance(properties)
        return context
    }

    Object newInstance(ApplicationContext context, String name, Map args) {
        return context.classLoader.loadClass(name).newInstance(args)
    }

    Object newInstance(ApplicationContext context, String name, Object[] args) {
        return context.classLoader.loadClass(name).newInstance(args)
    }

    @Override
    ApplicationContext buildContext(String className, @Language("java") String source) {
        ApplicationContext context = buildContext("test.Source" + System.currentTimeMillis(), source, true)

        setupSerdeRegistry(context)
        yamlObjectMapper = context.getBean(YamlObjectMapper)

        def t = context.classLoader.loadClass(className)
        typeUnderTest = Argument.of(t)
        return context
    }

    ApplicationContext buildContext(String className, @Language("java") String source, boolean includeAllBeans, Map contextProperties) {
        def context = super.buildContext(className, source, includeAllBeans, contextProperties)
        setupSerdeRegistry(context)
        yamlObjectMapper = context.getBean(YamlObjectMapper)
        return context
    }

    protected void setupSerdeRegistry(ApplicationContext context) {
        def classLoader = context.classLoader
        context.registerSingleton(SerdeIntrospections, new SerdeIntrospections() {

            @Override
            def <T> BeanIntrospection<T> getSerializableIntrospection(@NonNull Argument<T> type) {
                try {
                    return classLoader.loadClass(NameUtils.getPackageName(type.type.name) + ".\$" + type.type.simpleName + '$Introspection')
                            .newInstance() as BeanIntrospection<T>
                } catch (ClassNotFoundException e) {
                    throw new IntrospectionException("No introspection")
                }
            }

            @Override
            def <T1> BeanIntrospection<T1> getDeserializableIntrospection(@NonNull Argument<T1> type) {
                try {
                    return classLoader.loadClass(NameUtils.getPackageName(type.type.name) + ".\$" + type.type.simpleName + '$Introspection')
                            .newInstance() as BeanIntrospection<T1>
                } catch (ClassNotFoundException e) {
                    throw new IntrospectionException("No introspection for type $type")
                }
            }
        })
    }

    @Override
    YamlObjectMapper getYamlMapper() {
        return yamlObjectMapper
    }
}
