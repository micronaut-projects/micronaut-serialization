package io.micronaut.serde.bson

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.exceptions.IntrospectionException
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.SerdeIntrospections
import org.intellij.lang.annotations.Language

class BsonCompileSpec extends AbstractTypeElementSpec implements BsonJsonSpec {

    BsonJsonMapper bsonJsonMapper
    Object beanUnderTest
    Argument<?> typeUnderTest

    ApplicationContext buildContext(String className, @Language("java") String source, Map<String, Object> properties) {
        ApplicationContext context = buildContext(className, source, true)

        setupSerdeRegistry(context)
        bsonJsonMapper = context.getBean(BsonJsonMapper)

        def t = context.classLoader.loadClass(className)
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

    @Override
    ApplicationContext buildContext(String className, @Language("java") String source) {
        ApplicationContext context = buildContext("test.Source" + System.currentTimeMillis(), source, true)

        setupSerdeRegistry(context)
        bsonJsonMapper = context.getBean(BsonJsonMapper)
        def t = context.classLoader.loadClass(className)
        typeUnderTest = Argument.of(t)
        return context
    }

    protected void setupSerdeRegistry(ApplicationContext context) {
        def classLoader = context.classLoader
        context.registerSingleton(SerdeIntrospections, new SerdeIntrospections() {

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
