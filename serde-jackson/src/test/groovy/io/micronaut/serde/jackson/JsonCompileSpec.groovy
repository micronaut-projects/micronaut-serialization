package io.micronaut.serde.jackson

import com.sun.source.util.JavacTask
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.core.reflect.InstantiationUtils
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.support.DefaultSerdeIntrospections
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.SerdeIntrospections
import org.intellij.lang.annotations.Language

import javax.tools.JavaFileObject
import java.util.function.Predicate
import java.util.stream.Collectors

class JsonCompileSpec extends AbstractTypeElementSpec implements JsonSpec {

    ObjectMapper jsonMapper
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
}
