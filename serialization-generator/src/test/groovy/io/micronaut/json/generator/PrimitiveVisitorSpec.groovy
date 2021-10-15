package io.micronaut.json.generator

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.type.Argument
import io.micronaut.json.SerdeRegistry

class PrimitiveVisitorSpec extends AbstractTypeElementSpec {
    def test() {
        given:
        def ctx = buildContext('io.micronaut.json.generated.serializer.Test', '''
package io.micronaut.json.generated.serializer;

import io.micronaut.json.Decoder;
import io.micronaut.json.Deserializer;
import io.micronaut.json.Encoder;
import io.micronaut.json.Serializer;
import java.lang.annotation.*;

@GeneratePrimitiveSerializers
class PrimitiveGenerators {
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@interface GeneratePrimitiveSerializers {
}

''', true)
        def locator = ctx.getBean(SerdeRegistry)

        expect:
        locator.findDeserializer(Integer) != null
        locator.findDeserializer(String) != null
        locator.findDeserializer(Argument.listOf(Object)) != null
        locator.findDeserializer(Argument.mapOf(String, Object)) != null
        locator.findDeserializer(Argument.of(Optional, Object)) != null
        locator.findDeserializer(String[].class) != null
    }
}
