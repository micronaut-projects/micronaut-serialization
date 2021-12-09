package io.micronaut.json.generator.bean

import groovy.transform.Immutable
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.json.generator.SerializerUtils
import io.micronaut.json.generator.symbol.ProblemReporter
import io.micronaut.json.generator.symbol.SerializerLinker
import io.micronaut.json.generator.symbol.SingletonSerializerGenerator
import io.micronaut.serde.Serializer
import org.intellij.lang.annotations.Language

import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import java.nio.charset.Charset

import static javax.tools.JavaFileObject.Kind.SOURCE

class AbstractBeanSerializerSpec extends AbstractTypeElementSpec implements SerializerUtils {
    CompiledBean<?> buildSerializer(@Language("java") String cls, ProblemReporter problemReporter = null) {
        def classElement = buildClassElement(cls)

        def linker = new SerializerLinker(null)
        SingletonSerializerGenerator.GenerationResult result = SingletonSerializerGenerator.create(classElement)
                .problemReporter(problemReporter)
                .symbol(linker.inlineBean)
                .generateSingle()

        if (problemReporter != null && problemReporter.failed) {
            return null
        }

        def files = newJavaParser().generate(
                new StringSourceJavaFileObject(classElement.name, cls),
                new StringSourceJavaFileObject(result.serializerClassName.reflectionName(), result.generatedFile.toString())
        )

        ClassLoader classLoader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                String fileName = name.replace('.', '/') + '.class'
                JavaFileObject generated = files.find { it.name.endsWith(fileName) }
                if (generated != null) {
                    def bytes = generated.openInputStream().bytes
                    return defineClass(name, bytes, 0, bytes.length)
                }
                return super.findClass(name)
            }
        }

        def beanClass = classLoader.loadClass(classElement.name)
        def serializerClass = classLoader.loadClass(classElement.packageName + '.$' + classElement.simpleName + '$Serializer')
        def serializerInstance = (Serializer<?>) serializerClass.newInstance()
        return new CompiledBean(beanClass, serializerInstance)
    }

    @Immutable(knownImmutableClasses = [Serializer])
    class CompiledBean<T> {
        Class<T> beanClass
        Serializer<T> serializer

        def newInstance(List<?> params = []) {
            return beanClass.newInstance(params.toArray(Object[]::new))
        }
    }

    /**
     * stolen from micronaut-inject
     */
    private static final class StringSourceJavaFileObject extends SimpleJavaFileObject {
        final String source;
        final long lastModified;

        /**
         * Default constructor.
         * @param fullyQualifiedName the fully qualified name
         * @param source The source
         */
        StringSourceJavaFileObject(String fullyQualifiedName, String source) {
            super(createUri(fullyQualifiedName), SOURCE);
            this.source = source;
            this.lastModified = System.currentTimeMillis();
        }

        private static URI createUri(String fullyQualifiedClassName) {
            return URI.create(fullyQualifiedClassName.replace('.', '/')
                    + SOURCE.extension);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }

        @Override
        public OutputStream openOutputStream() {
            throw new IllegalStateException();
        }

        @Override
        public InputStream openInputStream() {
            return new ByteArrayInputStream(source.getBytes(Charset.defaultCharset()));
        }

        @Override
        public Writer openWriter() {
            throw new IllegalStateException();
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) {
            return new StringReader(source);
        }

        @Override
        public long getLastModified() {
            return lastModified;
        }
    }
}
