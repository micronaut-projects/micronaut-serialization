package io.micronaut.serde.jackson

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

class JsonCompileSpec extends AbstractTypeElementSpec implements JsonSpec {

    JsonMapper jsonMapper
    Object beanUnderTest

    ApplicationContext buildContext(String className, @Language("java") String source, Map<String, Object> properties) {
        ApplicationContext context =
                buildContext(className, source, true)


        def classLoader = context.classLoader
        context.registerSingleton(SerdeIntrospections, new SerdeIntrospections() {

            @Override
            def <T> BeanIntrospection<T> getSerializableIntrospection(@NonNull Argument<T> type) {
                if (type.type.name == className) {
                    return classLoader.loadClass(NameUtils.getPackageName(className) + ".\$" + NameUtils.getSimpleName(className) + '$Introspection')
                        .newInstance()
                }
                throw new IntrospectionException("No introspection")
            }

            @Override
            def <T> BeanIntrospection<T> getDeserializableIntrospection(@NonNull Argument<T> type) {
                throw new IntrospectionException("No introspection")
            }
        })
        jsonMapper = context.getBean(JsonMapper)
        beanUnderTest = context.classLoader
                                    .loadClass(className)
                                    .newInstance(properties)
        return context
    }
}
