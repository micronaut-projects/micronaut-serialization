package io.micronaut.serde.bson

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.exceptions.SerdeException
import org.bson.BsonDocument
import org.bson.BsonBinaryWriter
import org.bson.io.BasicOutputBuffer
import org.bson.codecs.BsonDocumentCodec
import org.bson.codecs.EncoderContext
import spock.lang.Specification

class BsonCoercionPolicySpec extends Specification {

    private static byte[] bson(String json) {
        def buffer = new BasicOutputBuffer()
        def writer = new BsonBinaryWriter(buffer)
        new BsonDocumentCodec().encode(writer, BsonDocument.parse(json), EncoderContext.builder().build())
        writer.flush()
        return buffer.toByteArray()
    }

    def 'the bson decoder honours the strict mode'() {
        given:
        def ctx = ApplicationContext.run(['micronaut.serde.deserialization.coercion-mode': 'STRICT'])
        def mapper = ctx.getBean(BsonBinaryMapper)

        when: 'a bson double read into an integer property'
        mapper.readValue(bson('{"number": 42.5}'), Plain)

        then:
        thrown SerdeException

        when: 'a bson string read into an integer property'
        mapper.readValue(bson('{"number": "42"}'), Plain)

        then:
        thrown SerdeException

        and: 'well shaped values still read'
        mapper.readValue(bson('{"number": 42}'), Plain).number == 42
        mapper.readValue(bson('{"text": "a"}'), Plain).text == 'a'

        cleanup:
        ctx.close()
    }

    def 'the default configuration is unchanged'() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = ctx.getBean(BsonBinaryMapper)

        expect:
        mapper.readValue(bson('{"number": 42.5}'), Plain).number == 42
        mapper.readValue(bson('{"number": "42"}'), Plain).number == 42
        mapper.readValue(bson('{"text": 1234}'), Plain).text == '1234'

        cleanup:
        ctx.close()
    }

    @Serdeable
    static class Plain {
        Integer number
        String text
    }
}
