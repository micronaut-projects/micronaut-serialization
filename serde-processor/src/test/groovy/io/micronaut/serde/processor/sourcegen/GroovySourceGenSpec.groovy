package io.micronaut.serde.processor.sourcegen

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.sourcegen.GroovyPoetSourceGenerator
import io.micronaut.sourcegen.model.ClassDef
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases

/**
 * The serializers and deserializers generated for Groovy classes are Groovy sources compiled with the
 * classes, so the source rendered for them has to be valid Groovy.
 */
class GroovySourceGenSpec extends AbstractBeanDefinitionSpec {

    void "the serdes generated for a Groovy #shape compile with Groovy"() {
        when:
        List<ClassDef> classDefs = generate(className, source)

        then:
        classDefs*.name.toSet() == expectedClasses.toSet()

        when:
        compileWithGroovy(source, classDefs)

        then:
        noExceptionThrown()

        where:
        shape              | className     | expectedClasses                                  | source
        'bean'             | 'test.Author' | ['test.SerdeAuthorSerializer', 'test.SerdeAuthorDeserializer'] | '''
package test

import io.micronaut.serde.annotation.Serdeable

@Serdeable
class Author {
    String username
    String name
}
'''
        'nullable bean'    | 'test.Book'   | ['test.SerdeBookSerializer', 'test.SerdeBookDeserializer']     | '''
package test

import io.micronaut.core.annotation.Nullable
import io.micronaut.serde.annotation.Serdeable

@Serdeable
class Book {
    @Nullable
    Long id
    String title
    String author
    int pages
}
'''
        'constructor bean' | 'test.Book'   | ['test.SerdeBookSerializer', 'test.SerdeBookDeserializer']     | '''
package test

import io.micronaut.core.annotation.Creator
import io.micronaut.core.annotation.Nullable
import io.micronaut.serde.annotation.Serdeable

@Serdeable
class Book {
    final @Nullable Long id
    final String title
    final int pages

    @Creator
    Book(@Nullable Long id, String title, int pages) {
        this.id = id
        this.title = title
        this.pages = pages
    }
}
'''
        'record'           | 'test.Book'   | ['test.SerdeBookSerializer', 'test.SerdeBookDeserializer']     | '''
package test

import io.micronaut.core.annotation.Nullable
import io.micronaut.serde.annotation.Serdeable

@Serdeable
record Book(@Nullable Long id, String title, int pages) {
}
'''
        'enum'             | 'test.Genre'  | ['test.SerdeGenreSerializer', 'test.SerdeGenreDeserializer']   | '''
package test

import io.micronaut.serde.annotation.Serdeable

@Serdeable
enum Genre {
    FICTION, POETRY
}
'''
    }

    private List<ClassDef> generate(String className, String source) {
        def element = buildClassElement(className, source)
        return new SerdeSourceGenVisitor().generate(element, VisitorContext.Language.GROOVY)
    }

    /**
     * Compiles the rendered sources with the model, without the Micronaut transformations that would
     * generate the serdes again.
     */
    private void compileWithGroovy(String source, List<ClassDef> classDefs) {
        def configuration = new CompilerConfiguration()
        configuration.disabledGlobalASTTransformations = [
            'io.micronaut.ast.groovy.InjectTransform',
            'io.micronaut.ast.groovy.TypeElementVisitorTransform',
            'io.micronaut.ast.groovy.PackageElementVisitorTransform',
            'io.micronaut.ast.groovy.TypeElementVisitorStart',
            'io.micronaut.ast.groovy.TypeElementVisitorEnd'
        ] as Set
        def unit = new CompilationUnit(configuration, null, new GroovyClassLoader(getClass().classLoader))
        unit.addSource('Model.groovy', source)
        for (ClassDef classDef : classDefs) {
            def writer = new StringWriter()
            new GroovyPoetSourceGenerator().write(classDef, writer)
            unit.addSource(classDef.simpleName + '.groovy', writer.toString())
        }
        unit.compile(Phases.CLASS_GENERATION)
    }
}
