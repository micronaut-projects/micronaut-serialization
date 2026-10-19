package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.SerdeRegistry
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import spock.lang.Specification

class SourceGenGeneratedSpec extends Specification {

    void 'generated #direction for a #shape retains its generated marker in bytecode'() {
        given:
        String className = "${type.packageName}.Serde${type.simpleName}${direction}"
        String resource = className.replace('.', '/') + '.class'
        def annotations = [:]

        when:
        type.classLoader.getResourceAsStream(resource).withCloseable { stream ->
            assert stream != null : "Generated class not found: ${className}"
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    annotations[descriptor] = visible
                    return null
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
        }

        then:
        // CLASS retention must survive javac even though reflection cannot see it.
        annotations['Lio/micronaut/serde/annotation/GeneratedSerde;'] == false
        // Core's marker would exclude the implementation from bean processing.
        !annotations.containsKey('Lio/micronaut/core/annotation/Generated;')
        !annotations.containsKey('Ljavax/annotation/processing/Generated;')

        where:
        [shape, type, direction] << [
            [
                ['record', SourceGenIndexedShapeRecord],
                ['bean', SourceGenIndexedShapeBean],
                ['enum', SourceGenFeatureEnum]
            ],
            ['Serializer', 'Deserializer']
        ].combinations().collect { row -> [row[0][0], row[0][1], row[1]] }
    }

    void 'generated serdes for a #shape remain discoverable and round trip'() {
        given:
        def context = ApplicationContext.run()
        def mapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)
        def argument = Argument.of(type)

        expect:
        registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument).class.simpleName == "Serde${type.simpleName}Serializer"
        registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument).class.simpleName == "Serde${type.simpleName}Deserializer"
        mapper.writeValueAsString(mapper.readValue(json, argument)) == json

        cleanup:
        context.close()

        where:
        shape    | type                        | json
        'record' | SourceGenIndexedShapeRecord | '{"value":"hello","tags":["a"]}'
        'bean'   | SourceGenIndexedShapeBean   | '{"value":"hello","tags":["a"]}'
        'enum'   | SourceGenFeatureEnum        | '"ALPHA"'
    }
}
