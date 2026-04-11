package io.micronaut.serde.xml.tck

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.exceptions.IntrospectionException
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.config.annotation.SerdeConfig
import org.intellij.lang.annotations.Language
import org.jspecify.annotations.NonNull

abstract class AbstractXmlCompileSpec extends AbstractTypeElementSpec{

    JsonMapper xmlMapper
    Object beanUnderTest
    Argument<?> typeUnderTest


    ApplicationContext buildContext(String className, @Language("java") String source, Map<String, Object> properties) {
        ApplicationContext context =
                buildContext(className, source, true)

        xmlMapper = context.getBean(getXmlMapperClass())

        def t = context.classLoader
                .loadClass(className)
        typeUnderTest = Argument.of(t)
        beanUnderTest = t.newInstance(properties)
        return context
    }

    Class<JsonMapper> getXmlMapperClass() {
        JsonMapper
    }

    Argument<Object> argumentOf(ApplicationContext context, String name) {
        return Argument.of(context.classLoader.loadClass(name))
    }

    @Override
    ApplicationContext buildContext(@Language("java") String source) {
        ApplicationContext context =
                buildContext("test.Source" + System.currentTimeMillis(), source, true)


        xmlMapper = context.getBean(getXmlMapperClass())
        return context
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.properties(
                "micronaut.serde.serialization.inclusion": SerdeConfig.SerInclude.ALWAYS,
                (MockSerdeIntrospections.ENABLED):true
        )

    }

}
