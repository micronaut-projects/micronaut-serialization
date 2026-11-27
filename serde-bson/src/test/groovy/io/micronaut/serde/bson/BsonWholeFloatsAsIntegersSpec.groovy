package io.micronaut.serde.bson

import io.micronaut.core.type.Argument
import org.bson.BsonBinaryReader
import org.bson.BsonDocument
import org.bson.BsonDouble
import org.bson.BsonInt64
import org.bson.codecs.BsonDocumentCodec
import org.bson.codecs.DecoderContext
import spock.lang.Unroll

import java.nio.ByteBuffer

/**
 * {@code SerializationConfiguration.Feature.WRITE_WHOLE_FLOATS_AS_INTEGERS} acts in the serializer, before the format:
 * with BSON it changes the type of the stored value.
 */
class BsonWholeFloatsAsIntegersSpec extends BsonCompileSpec {

    @Unroll
    void "a whole double is stored as #stored when the feature is #feature"() {
        given:
        def context = buildContext('test.Test', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonRepresentation;

@Serdeable
record Test(double whole, Double boxed, double fraction, @BsonRepresentation(BsonType.DOUBLE) double represented) {
}
''', true, configuration)
        setupSerdeRegistry(context)
        def mapper = context.getBean(BsonBinaryMapper)
        def bean = newInstance(context, 'test.Test', [900d, 900d, 0.5d, 900d] as Object[])

        when:
        byte[] bytes = mapper.writeValueAsBytes(bean)
        BsonDocument document = new BsonDocumentCodec().decode(new BsonBinaryReader(ByteBuffer.wrap(bytes)), DecoderContext.builder().build())
        def read = mapper.readValue(bytes, Argument.of(bean.getClass()))

        then:
        document.get('whole') == whole
        document.get('boxed') == whole
        document.get('fraction') == new BsonDouble(0.5d)

        and: 'a representation is written by its own serializer'
        document.get('represented') == new BsonDouble(900d)

        and: 'the value read is the value written'
        read == bean

        cleanup:
        context.close()

        where:
        feature | configuration                                                        | whole
        'off'   | [:]                                                                  | new BsonDouble(900d)
        'on'    | ['micronaut.serde.serialization.write-whole-floats-as-integers': true] | new BsonInt64(900L)

        stored = whole.bsonType.name().toLowerCase()
    }
}
