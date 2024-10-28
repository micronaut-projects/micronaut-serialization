/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.jackson

import io.micronaut.annotation.processing.JavaAnnotationMetadataBuilder
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import org.intellij.lang.annotations.Language

import java.nio.charset.StandardCharsets

abstract class JsonCompileSpec extends AbstractTypeElementSpec implements JsonSpec {

    JsonMapper jsonMapper
    Object beanUnderTest
    Argument<?> typeUnderTest

    @Override
    protected JavaParser newJavaParser() {
        JavaAnnotationMetadataBuilder.clearCaches()
        return super.newJavaParser()
    }

    ApplicationContext buildContext(String className, @Language("java") String source, Map<String, Object> properties) {
        ApplicationContext context =
                buildContext(className, source, true)

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


        jsonMapper = context.getBean(JsonMapper)
        return context
    }

    @Override
    ApplicationContext buildContext(String className, @Language("java") String cls, boolean includeAllBeans) {
        def context = super.buildContext(className, cls, true)
        Thread.currentThread().setContextClassLoader(context.classLoader)
        jsonMapper = context.getBean(JsonMapper)
        return context
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

    static String serializeToString(JsonMapper jsonMapper, Object value, Class<?> view = null) {
        if (view != null) {
            jsonMapper = jsonMapper.cloneWithViewClass(view)
        }
        return new String(jsonMapper.writeValueAsBytes(value), StandardCharsets.UTF_8)
    }

    static <T> T deserializeFromString(JsonMapper jsonMapper, Class<T> type, @Language("json") String json, Class<?> view = null) {
        if (view != null) {
            jsonMapper = jsonMapper.cloneWithViewClass(view)
        }
        return jsonMapper.readValue(json, Argument.of(type))
    }

    static boolean validateJsonWithoutOrder(JsonMapper jsonMapper, String expected, String given) {
        return jsonMapper.readValue(expected, Map) == jsonMapper.readValue(given, Map)
    }

}
