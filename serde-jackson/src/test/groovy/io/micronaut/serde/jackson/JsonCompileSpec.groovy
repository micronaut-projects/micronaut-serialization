package io.micronaut.serde.jackson


import io.micronaut.annotation.processing.JavaAnnotationMetadataBuilder
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.MockSerdeIntrospections
import io.micronaut.serde.ObjectMapper
import org.intellij.lang.annotations.Language

import java.nio.charset.StandardCharsets

class JsonCompileSpec extends AbstractTypeElementSpec implements JsonSpec {

    JsonMapper jsonMapper
    Object beanUnderTest
    Argument<?> typeUnderTest

    @Override
    protected JavaParser newJavaParser() {
        JavaAnnotationMetadataBuilder.clearCaches()
        return super.newJavaParser()
    }

    ApplicationContext buildContext(String className, @Language("java") String source, Map<String, Object> properties) {
        return buildContext(className, source, properties, [:])
    }

    ApplicationContext buildContext(String className, @Language("java") String source, Map<String, Object> properties, Map contextProperties) {
        ApplicationContext context =
                buildContext(className, source, true, contextProperties)

        jsonMapper = context.getBean(ObjectMapper)

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

    ApplicationContext buildContext(String className, @Language("java") String cls, boolean includeAllBeans, Map contextProperties) {
        def context = super.buildContext(className, cls, true, contextProperties)
        jsonMapper = context.getBean(JsonMapper)
        return context
    }

    @Override
    ApplicationContext buildContext(@Language("java") String source) {
        ApplicationContext context =
                buildContext("test.Source" + System.currentTimeMillis(), source, true)


        jsonMapper = context.getBean(JsonMapper)
        return context
    }

    @Override
    ApplicationContext buildContext(String className, @Language("java") String cls, boolean includeAllBeans) {
        return buildContext(className, cls, includeAllBeans, [:])
    }

    @Override
    ApplicationContext buildContext(String className, @Language("java") String cls) {
        def context = super.buildContext(className, cls, true)
        def t = context.classLoader
                .loadClass(className)
        typeUnderTest = Argument.of(t)
        jsonMapper = context.getBean(JsonMapper)
        return context
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.properties(
            (MockSerdeIntrospections.ENABLED):true
        )
    }

    static String serializeToString(JsonMapper jsonMapper, Object value, Class<?> view = Object.class) {
        return new String(jsonMapper.cloneWithViewClass(view).writeValueAsBytes(value), StandardCharsets.UTF_8)
    }

    static <T> T deserializeFromString(JsonMapper jsonMapper, Class<T> type, @Language("json") String json, Class<?> view = Object.class) {
        return jsonMapper.cloneWithViewClass(view).readValue(json, Argument.of(type))
    }

    static boolean validateJsonWithoutOrder(JsonMapper jsonMapper, String expected, String given) {
        return jsonMapper.readValue(expected, Map) == jsonMapper.readValue(given, Map)
    }

}
