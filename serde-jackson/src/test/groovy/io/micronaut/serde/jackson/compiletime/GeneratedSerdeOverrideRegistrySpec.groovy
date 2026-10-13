package io.micronaut.serde.jackson.compiletime

import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.jackson.JsonCompileSpec

/**
 * A serializer or deserializer bean written for a type replaces the serde generated at build time
 * for that type, as it replaced the runtime object serde before generated serdes existed.
 */
class GeneratedSerdeOverrideRegistrySpec extends JsonCompileSpec {

    void 'test a user defined serde bean for a generated type is selected'() {
        given:
        def context = buildContext('test.ResponseBody', '''
package test;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Map;

@Serdeable
class ResponseBody {
    private final Map<String, Object> specification;

    @JsonCreator
    ResponseBody(Map<String, Object> specification) {
        this.specification = specification;
    }

    @JsonAnyGetter
    @JsonInclude
    public Map<String, Object> getSpecification() {
        return specification;
    }
}

@Serdeable
class Point {
    private int x;

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }
}

@Singleton
class ResponseBodyDeserializer implements Deserializer<ResponseBody> {
    @Override
    public ResponseBody deserialize(Decoder decoder, DecoderContext context, Argument<? super ResponseBody> type) throws IOException {
        return new ResponseBody(Map.of("custom", decoder.decodeArbitrary()));
    }
}

@Singleton
class PointSerializer implements Serializer<Point> {
    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Point> type, Point value) throws IOException {
        encoder.encodeString("point:" + value.getX());
    }
}
''')
        def mapper = context.getBean(JsonMapper)
        def registry = context.getBean(SerdeRegistry)
        Class<?> responseBodyType = context.classLoader.loadClass('test.ResponseBody')
        Class<?> pointType = context.classLoader.loadClass('test.Point')
        context.classLoader.loadClass('test.SerdeResponseBodyDeserializer')
        context.classLoader.loadClass('test.SerdePointSerializer')

        when:
        def body = mapper.readValue('{"data":{"a":1}}', Argument.of(responseBodyType))
        def point = pointType.newInstance()
        point.x = 5

        then: 'the user defined deserializer and serializer replace the generated ones'
        body.specification == [custom: [data: [a: 1]]]
        mapper.writeValueAsString(Argument.of(pointType), point) == '"point:5"'

        and: 'the directions without a user defined bean are unaffected'
        mapper.writeValueAsString(Argument.of(responseBodyType), body) == '{"custom":{"data":{"a":1}}}'
        mapper.readValue('{"x":7}', Argument.of(pointType)).x == 7
        registry.findDeserializer(Argument.of(pointType))
            .createSpecific(registry.newDecoderContext(Object), Argument.of(pointType))
            .class.name == 'test.SerdePointDeserializer'

        cleanup:
        context.close()
    }
}
