package io.micronaut.serde.jackson.features

import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.SerdeRegistry
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@MicronautTest
class BeanFeatureAnnotationsSpec extends Specification {

    @Inject
    JsonMapper jsonMapper

    @Inject
    SerdeRegistry serdeRegistry

    void "bean-level features still apply after repeated runtime-typed writes and reads"() {
        given:
        def values = new LinkedHashMap<String, Integer>()
        values.put("c", 3)
        values.put("a", 1)
        values.put("b", 2)
        def bean = new SortedFeatureBean(values, ["x"])

        expect:
        (1..5).every {
            def out = new ByteArrayOutputStream()
            jsonMapper.writeValue(out, bean)
            new String(out.toByteArray(), StandardCharsets.UTF_8) == '{"values":{"a":1,"b":2,"c":3},"names":["x"]}'
        }
        (1..5).every {
            def read = jsonMapper.readValue('{"values":{"a":1},"names":"single"}', Argument.of(SortedFeatureBean))
            read.names() == ["single"] && read.values() == [a: 1]
        }
    }

    void "bean-level features still apply after repeated createSpecific calls"() {
        given:
        def type = Argument.of(SortedFeatureBean)
        def values = new LinkedHashMap<String, Integer>()
        values.put("z", 26)
        values.put("m", 13)

        when:
        (1..3).each {
            serdeRegistry.findSerializer(type).createSpecific(serdeRegistry.newEncoderContext(Object), type)
            serdeRegistry.findDeserializer(type).createSpecific(serdeRegistry.newDecoderContext(Object), type)
        }

        then:
        jsonMapper.writeValueAsString(new SortedFeatureBean(values, ["n"])) == '{"values":{"m":13,"z":26},"names":["n"]}'
        jsonMapper.readValue('{"values":{},"names":"one"}', type).names() == ["one"]
    }
}
