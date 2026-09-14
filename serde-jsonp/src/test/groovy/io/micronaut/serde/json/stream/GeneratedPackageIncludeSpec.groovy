package io.micronaut.serde.json.stream

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.json.stream.includes.PackageIncludeRecord
import spock.lang.Specification

/**
 * A package-level inclusion is resolved when the serializer is generated, the same way the runtime
 * serializer inherits it through the annotation visitor.
 */
class GeneratedPackageIncludeSpec extends Specification {

    void 'test the generated serializer applies a package-level inclusion'() {
        given:
        def generatedContext = ApplicationContext.run(['micronaut.serde.serialization.inclusion': 'ALWAYS'])
        def runtimeContext = ApplicationContext.run([
            'micronaut.serde.serialization.inclusion'                   : 'ALWAYS',
            'micronaut.serde.serialization.disable-generated-serializer': true
        ])
        Argument argument = Argument.of(PackageIncludeRecord)

        when:
        String generatedJson = new String(generatedContext.getBean(JsonMapper).writeValueAsBytes(new PackageIncludeRecord(null, 'l')), 'UTF-8')
        String runtimeJson = new String(runtimeContext.getBean(JsonMapper).writeValueAsBytes(new PackageIncludeRecord(null, 'l')), 'UTF-8')

        then:
        isGenerated(generatedContext.getBean(SerdeRegistry), argument)
        !isGenerated(runtimeContext.getBean(SerdeRegistry), argument)
        generatedJson == '{"label":"l"}'
        generatedJson == runtimeJson

        cleanup:
        generatedContext.close()
        runtimeContext.close()
    }

    private static boolean isGenerated(SerdeRegistry registry, Argument argument) {
        Serializer serializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument)
        serializer.class.name == 'io.micronaut.serde.json.stream.includes.SerdePackageIncludeRecordSerializer'
    }
}
