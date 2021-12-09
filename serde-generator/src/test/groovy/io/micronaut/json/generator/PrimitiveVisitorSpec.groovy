package io.micronaut.json.generator

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.json.GenericTypeFactory
import io.micronaut.json.SerializerLocator

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

@jakarta.inject.Singleton
class MockObjectSerializer implements Serializer<Object>, Deserializer<Object> {
    @Override
    public Object deserialize(Decoder decoder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void serialize(Encoder encoder, Object value) {
        throw new UnsupportedOperationException();
    }
}
''', true)
        def locator = ctx.getBean(SerializerLocator)

        expect:
        locator.findInvariantDeserializer(Integer) != null
        locator.findInvariantDeserializer(String) != null
        locator.findInvariantDeserializer(GenericTypeFactory.makeParameterizedTypeWithOwner(null, List, Object)) != null
        locator.findInvariantDeserializer(GenericTypeFactory.makeParameterizedTypeWithOwner(null, Map, String, Object)) != null
        locator.findInvariantDeserializer(GenericTypeFactory.makeParameterizedTypeWithOwner(null, Optional, Object)) != null
        locator.findInvariantDeserializer(String[].class) != null
    }
}
