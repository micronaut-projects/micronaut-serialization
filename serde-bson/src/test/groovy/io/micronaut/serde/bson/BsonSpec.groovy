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

    def "validate arrays"() {
        given:
            def json = """{"vals": [{"val": "A"}, {"val": "B"}]}"""
        when:
            def obj = encodeAsBinaryDecodeAsObject(BsonDocument.parse(json), ObjectWithArray)
        then:
            obj
            obj.vals.size() == 2
            obj.vals[0].val == "A"
            obj.vals[1].val == "B"
        and:
            asBsonJsonString(obj) == json
            encodeAsBinaryDecodeJson(obj) == json
            encodeAsBinaryDecodeAsObject(obj) == obj
    }

    def "validate empty arrays"() {
        given:
            def json = """{"vals": []}"""
        when:
            def obj = encodeAsBinaryDecodeAsObject(BsonDocument.parse(json), ObjectWithArray)
        then:
            obj
            obj.vals.size() == 0
        and:
            asBsonJsonString(obj) == json
            encodeAsBinaryDecodeJson(obj) == json
            encodeAsBinaryDecodeAsObject(obj) == obj
    }

    def "validate arrays with nulls"() {
        given:
            def json = """{"vals": [{"val": "A"}, null, {"val": "B"}]}"""
        when:
            def obj = encodeAsBinaryDecodeAsObject(BsonDocument.parse(json), ObjectWithArray)
        then:
            obj
            obj.vals.size() == 3
            obj.vals[0].val == "A"
            obj.vals[1] == null
            obj.vals[2].val == "B"
        and:
            asBsonJsonString(obj) == json
            encodeAsBinaryDecodeJson(obj) == json
            encodeAsBinaryDecodeAsObject(obj) == obj
    }

    def "validate arrays of arrays"() {
        given:
            def json = """{"vals": [[{"val": "A"}, null, {"val": "B"}]]}"""
        when:
            def obj = encodeAsBinaryDecodeAsObject(BsonDocument.parse(json), ObjectWithArrayOfArray)
        then:
            obj
            obj.vals.size() == 1
            obj.vals[0].size() == 3
            obj.vals[0][0].val == "A"
            obj.vals[0][1] == null
            obj.vals[0][2].val == "B"
        and:
            asBsonJsonString(obj) == json
            encodeAsBinaryDecodeJson(obj) == json
            encodeAsBinaryDecodeAsObject(obj) == obj
    }

    def "validate empty arrays of arrays"() {
        given:
            def json = """{"vals": [[]]}"""
        when:
            def obj = encodeAsBinaryDecodeAsObject(BsonDocument.parse(json), ObjectWithArrayOfArray)
        then:
            obj
            obj.vals.size() == 1
            obj.vals[0].size() == 0
        and:
            asBsonJsonString(obj) == json
            encodeAsBinaryDecodeJson(obj) == json
            encodeAsBinaryDecodeAsObject(obj) == obj
    }

    def "validate null arrays of arrays"() {
        given:
            def json = """{"vals": [null]}"""
        when:
            def obj = encodeAsBinaryDecodeAsObject(BsonDocument.parse(json), ObjectWithArrayOfArray)
        then:
            obj
            obj.vals.size() == 1
            obj.vals[0] == null
        and:
            asBsonJsonString(obj) == json
            encodeAsBinaryDecodeJson(obj) == json
            encodeAsBinaryDecodeAsObject(obj) == obj
    }

    def "validate arrays as null"() {
        given:
            def json = """{"vals": null}"""
        when:
            def obj = encodeAsBinaryDecodeAsObject(BsonDocument.parse(json), ObjectWithArray)
        then:
            obj
            obj.vals == null
        and:
            asBsonJsonString(obj) == json
            encodeAsBinaryDecodeJson(obj) == json
            encodeAsBinaryDecodeAsObject(obj) == obj
    }

    def "should deser all null types bean"() {
        when:
            encodeAsBinaryDecodeAsObject(new BsonDocument(), AllTypesBean)
        then:
            noExceptionThrown()
    }

    def "validate all types bean"() {
        when:
            def json = """
{
    someBool: true,
    someInt: 123,
    someLong: 234,
    someByte: 34,
    someShort: 567,
    someFloat: 11.22,
    someDouble: 123.234,
    someString: "Hello",
    someBoolean: true,
    someInteger: 444,
    someLongObj: 555,
    someDoubleObj: 666.77,
    someShortObj: 777,
    someFloatObj: 888.99,
    someByteObj: 99,
    bigDecimal: 12345.12345
    bigInteger: 123456789,
}
"""
            def all = encodeAsBinaryDecodeAsObject(BsonDocument.parse(json), AllTypesBean)
        then:
            all.someBool
            all.someInt == 123
            all.someLong == 234
            all.someByte == (byte) 34
            all.someShort == (short) 567
            all.someFloat == 11.22f
            all.someDouble == 123.234D
            all.someString == "Hello"
            all.someBoolean == Boolean.TRUE
            all.someInteger == 444
            all.someLongObj == 555
            all.someDoubleObj == 666.77d
            all.someShortObj == 777
            all.someFloatObj == 888.99f
            all.someByteObj == 99
            all.bigDecimal == BigDecimal.valueOf(12345.12345)
            all.bigInteger == BigInteger.valueOf(123456789)
            all.decimal128 == null
        when:
            def bsonAsJson = asBsonJsonString(all)
        then:
            bsonAsJson == '''{"someInt": 123, "someLong": 234, "someDouble": 123.234, "someShort": 567, "someFloat": 11.220000267028809, "someByte": 34, "someString": "Hello", "someInteger": 444, "someLongObj": 555, "someDoubleObj": 666.77, "someShortObj": 777, "someFloatObj": 888.989990234375, "someByteObj": 99, "bigDecimal": {"$numberDecimal": "12345.12345"}, "bigInteger": {"$numberDecimal": "123456789"}, "decimal128": null, "someBoolean": true, "someBool": true, "objectId": null}'''
            encodeAsBinaryDecodeAsObject(all) == all
    }

    def "validate decimal128"() {
        given:
            def document = new BsonDocument()
            def decimal = BigDecimal.valueOf(123456.999)
            document.put("decimal128", new BsonDecimal128(new Decimal128(decimal)))
        when:
            def all = encodeAsBinaryDecodeAsObject(document, AllTypesBean)
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
            def all = encodeAsBinaryDecodeAsObject(document, AllTypesBean)
        then:
            all.objectId == objectId
            encodeAsBinaryDecodeAsObject(all).objectId == objectId
    }

    def "should skip unknown values"() {
        given:
            def document = new BsonDocument()
            document.put("unknown", new BsonString("A"))
        when:
            encodeAsBinaryDecodeAsObject(document, AllTypesBean)
        then:
            noExceptionThrown()
    }

    def "should decode null"() {
        given:
            def document = new BsonDocument()
            document.put("someBool", BsonNull.VALUE)
            document.put("someInt", BsonNull.VALUE)
            document.put("decimal128", BsonNull.VALUE)
            document.put("bigDecimal", BsonNull.VALUE)
        when:
            def value = encodeAsBinaryDecodeAsObject(document, AllTypesBean)
        then:
            value.someInt == 0
            !value.someBool
            value.decimal128 == null
            value.bigDecimal == null
    }

}
