/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde

import com.sun.source.util.JavacTask
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.config.annotation.SerdeConfig
import org.intellij.lang.annotations.Language

import javax.tools.JavaFileObject

abstract class AbstractJsonCompileSpec extends AbstractTypeElementSpec implements JsonSpec {

    JsonMapper jsonMapper
    Object beanUnderTest
    Argument<?> typeUnderTest

    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {
            @Override
            JavacTask getJavacTask(JavaFileObject... sources) {
                // TODO: remove horrible hack once micronaut-inject-java-test working in JDK 17
                def lastTask = ReflectionUtils.findField(JavaParser, "lastTask")
                        .get()
                def diagnosticCollector = ReflectionUtils.findField(JavaParser, "diagnosticCollector")
                        .get()
                diagnosticCollector.setAccessible(true)
                lastTask.setAccessible(true)
                lastTask.set(
                        this,
                        (JavacTask) compiler.getTask(
                                null, // explicitly use the default because old javac logs some output on stderr
                                fileManager,
                                diagnosticCollector.get(this),
                                Collections.emptySet(),
                                Collections.emptySet(),
                                Arrays.asList(sources)
                        )
                )

                return lastTask.get(this);

            }
        }
    }

    ApplicationContext buildContext(String className, @Language("java") String source, Map<String, Object> properties) {
        ApplicationContext context =
                buildContext(className, source, true)

        jsonMapper = context.getBean(getJsonMapperClass())

        def t = context.classLoader
                .loadClass(className)
        typeUnderTest = Argument.of(t)
        beanUnderTest = t.newInstance(properties)
        return context
    }

    Class<JsonMapper> getJsonMapperClass() {
        JsonMapper
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


        jsonMapper = context.getBean(getJsonMapperClass())
        return context
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.properties(
                "micronaut.serde.serialization.inclusion": SerdeConfig.SerInclude.ALWAYS
        )

    }
}
