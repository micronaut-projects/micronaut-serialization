package io.micronaut.serde.bson

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.bson.*
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import spock.lang.Specification

@MicronautTest
class BsonSpec extends Specification implements BsonJsonSpec, BsonBinarySpec {

    @Inject
    BsonBinaryMapper bsonBinaryMapper

    @Inject
    BsonJsonMapper bsonJsonMapper

    def "validate decimal128"() {
        given:
            def document = new BsonDocument()
            def decimal = BigDecimal.valueOf(123456.999)
            document.put("decimal128", new BsonDecimal128(new Decimal128(decimal)))
        when:
            def all = encodeAsBinaryDecodeAsObject(document, CustomTypes)
        then:
            all.decimal128.bigDecimalValue() == decimal
            encodeAsBinaryDecodeAsObject(all).decimal128.bigDecimalValue() == decimal
    }

    def "validate objectId"() {
        given:
            def document = new BsonDocument()
            def objectId = new ObjectId()
            document.put("objectId", new BsonObjectId(objectId))
        when:
            def all = encodeAsBinaryDecodeAsObject(document, CustomTypes)
        then:
            all.objectId == objectId
            encodeAsBinaryDecodeAsObject(all).objectId == objectId
    }

    def "validate binary"() {
        given:
            def document = new BsonDocument()
            def data = new byte[]{1, 2, 3}
            def uuid = UUID.randomUUID()
            document.put("binary", new BsonBinary(data))
            document.put("bytes", new BsonBinary(data))
            document.put("uuid", new BsonBinary(uuid))
        when:
            def all = encodeAsBinaryDecodeAsObject(document, CustomTypes)
        then:
            all.binary.data == data
            all.bytes == data
            all.uuid == uuid
            encodeAsBinaryDecodeAsObject(all).binary.data == data
            encodeAsBinaryDecodeAsObject(all).bytes == data
            encodeAsBinaryDecodeAsObject(all).uuid == uuid
    }

    def "validate regular exp"() {
        given:
            def document = new BsonDocument()
            document.put("regularExpression", new BsonRegularExpression(".*"))
        when:
            def all = encodeAsBinaryDecodeAsObject(document, CustomTypes)
        then:
            all.regularExpression.pattern == ".*"
            encodeAsBinaryDecodeAsObject(all).regularExpression.pattern == ".*"
    }

    def "validate db pointer"() {
        given:
            def document = new BsonDocument()
            def objectId = new ObjectId()
            document.put("dbPointer", new BsonDbPointer("my", objectId))
        when:
            def all = encodeAsBinaryDecodeAsObject(document, CustomTypes)
        then:
            all.dbPointer.namespace == "my"
            all.dbPointer.id == objectId
            encodeAsBinaryDecodeAsObject(all).dbPointer.namespace == "my"
            encodeAsBinaryDecodeAsObject(all).dbPointer.id == objectId
    }

    def "validate datetime"() {
        given:
            def document = new BsonDocument()
            def date = new Date()
            document.put("dateTime", new BsonDateTime(date.getTime()))
        when:
            def all = encodeAsBinaryDecodeAsObject(document, CustomTypes)
        then:
            all.dateTime == date.getTime()
            encodeAsBinaryDecodeAsObject(all).dateTime == date.getTime()
    }

    def "should skip unknown values"() {
        given:
            def document = new BsonDocument()
            document.put("unknown", new BsonString("A"))
        when:
            encodeAsBinaryDecodeAsObject(document, CustomTypes)
        then:
            noExceptionThrown()
    }

    def "should decode null"() {
        given:
            def document = new BsonDocument()
            document.put("decimal128", BsonNull.VALUE)
            document.put("objectId", BsonNull.VALUE)
        when:
            def value = encodeAsBinaryDecodeAsObject(document, CustomTypes)
        then:
            value.decimal128 == null
            value.objectId == null
    }

}
