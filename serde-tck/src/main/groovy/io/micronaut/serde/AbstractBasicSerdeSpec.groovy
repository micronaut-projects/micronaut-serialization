/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde

import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.annotation.SerdeConfig
import io.micronaut.test.support.TestPropertyProvider
import spock.lang.Specification

import java.nio.charset.StandardCharsets

abstract class AbstractBasicSerdeSpec extends Specification implements JsonSpec, TestPropertyProvider {

    abstract JsonMapper getJsonMapper()

    void "test write simple"() {
        when:
        def bean = new Simple(name: "Test")
        def result = writeJson(jsonMapper, bean)

        then:
        jsonMatches(result, '{"name":"Test"}')
    }

    void "test read/write constructor args"() {
        when:
        def bean = new ConstructorArgs("test", 100)
        bean.author = "Bob"
        bean.other = "Something"
        def result = writeJson(jsonMapper, bean)

        then:
        jsonMatches(result, '{"title":"test","pages":100,"author":"Bob","other":"Something"}')

        when:
        bean = jsonMapper.readValue(jsonAsBytes(result), Argument.of(ConstructorArgs))

        then:
        bean.title == 'test'
        bean.pages == 100
        bean.other == 'Something'
        bean.author == 'Bob'

        when:
        bean = jsonMapper.readValue(jsonAsBytes('{"other":"Something","author":"Bob", "title":"test","pages":100}'), Argument.of(ConstructorArgs))

        then:
        bean.title == 'test'
        bean.pages == 100
        bean.other == 'Something'
        bean.author == 'Bob'
    }

    def "validate arrays"() {
        given:
            def json = """{"vals": [{"val": "A"}, {"val": "B"}]}"""
        when:
            def obj = jsonMapper.readValue(jsonAsBytes(json), Argument.of(ObjectWithArray))
        then:
            obj
            obj.vals.size() == 2
            obj.vals[0].val == "A"
            obj.vals[1].val == "B"
            objRepresentationMatches(obj, json)
    }

    def "validate empty arrays"() {
        given:
            def json = """{"vals": []}"""
        when:
            def obj = jsonMapper.readValue(jsonAsBytes(json), Argument.of(ObjectWithArray))
        then:
            obj
            obj.vals.size() == 0
            objRepresentationMatches(obj, json)
    }

    def "validate arrays with nulls"() {
        given:
            def json = """{"vals": [{"val": "A"}, null, {"val": "B"}]}"""
        when:
            def obj = jsonMapper.readValue(jsonAsBytes(json), Argument.of(ObjectWithArray))
        then:
            obj
            obj.vals.size() == 3
            obj.vals[0].val == "A"
            obj.vals[1] == null
            obj.vals[2].val == "B"
            objRepresentationMatches(obj, json)
    }

    def "validate arrays of arrays"() {
        given:
            def json = """{"vals": [[{"val": "A"}, null, {"val": "B"}]]}"""
        when:
            def obj = jsonMapper.readValue(jsonAsBytes(json), Argument.of(ObjectWithArrayOfArray))
        then:
            obj
            obj.vals.size() == 1
            obj.vals[0].size() == 3
            obj.vals[0][0].val == "A"
            obj.vals[0][1] == null
            obj.vals[0][2].val == "B"
            objRepresentationMatches(obj, json)
    }

    def "validate empty arrays of arrays"() {
        given:
            def json = """{"vals": [[]]}"""
        when:
            def obj = jsonMapper.readValue(jsonAsBytes(json), Argument.of(ObjectWithArrayOfArray))
        then:
            obj
            obj.vals.size() == 1
            obj.vals[0].size() == 0
            objRepresentationMatches(obj, json)
    }

    def "validate null arrays of arrays"() {
        given:
            def json = """{"vals": [null]}"""
        when:
            def obj = jsonMapper.readValue(jsonAsBytes(json), Argument.of(ObjectWithArrayOfArray))
        then:
            obj
            obj.vals.size() == 1
            obj.vals[0] == null
            objRepresentationMatches(obj, json)
    }

    def "validate arrays as null"() {
        given:
            def json = """{"vals": null}"""
        when:
            def obj = jsonMapper.readValue(jsonAsBytes(json), Argument.of(ObjectWithArray))
        then:
            obj
            obj.vals == null
            objRepresentationMatches(obj, json)
    }

    def "should deser all null types bean"() {
        when:
            def obj = jsonMapper.readValue(jsonAsBytes("{}"), Argument.of(AllTypesBean))
        then:
            noExceptionThrown()
    }

    def "validate all types bean"() {
        given:
            def all = new AllTypesBean()
            all.someBool = true
            all.someInt = 123
            all.someLong = 234
            all.someByte = (byte) 34
            all.someShort = (short) 567
            all.someFloat = 11.22f
            all.someDouble = 123.234D
            all.someString = "Hello"
            all.someBoolean = Boolean.TRUE
            all.someInteger = 444
            all.someLongObj = 555
            all.someDoubleObj = 666.77d
            all.someShortObj = 777
            all.someFloatObj = 888.99f
            all.someByteObj = 99
            all.bigDecimal = BigDecimal.valueOf(12345.12345)
            all.bigInteger = BigInteger.valueOf(123456789)
        when:
            def result = serializeDeserialize(all)
        then:
            result.someBool
            result.someInt == 123
            result.someLong == 234
            result.someByte == (byte) 34
            result.someShort == (short) 567
            result.someFloat == 11.22f
            result.someDouble == 123.234D
            result.someString == "Hello"
            result.someBoolean == Boolean.TRUE
            result.someInteger == 444
            result.someLongObj == 555
            result.someDoubleObj == 666.77d
            result.someShortObj == 777
            result.someFloatObj == 888.99f
            result.someByteObj == 99
            result.bigDecimal == BigDecimal.valueOf(12345.12345)
            result.bigInteger == BigInteger.valueOf(123456789)
    }

    def "should skip unknown values"() {
        when:
            def all = jsonMapper.readValue(jsonAsBytes("""{"unknown":"ABC"}"""), Argument.of(AllTypesBean))
        then:
            noExceptionThrown()
    }

    def "should decode null"() {
        when:
            def value = jsonMapper.readValue(jsonAsBytes("""{"someBool":null, "someInt": null, "bigDecimal": null}"""), Argument.of(AllTypesBean))
        then:
            value.someInt == 0
            !value.someBool
            value.bigDecimal == null
    }

    def <T> T serializeDeserialize(T obj) {
        def output = jsonMapper.writeValueAsBytes(obj)
        return jsonMapper.readValue(output, Argument.of(obj.getClass())) as T
    }

    byte[] jsonAsBytes(String json) {
        json.getBytes(StandardCharsets.UTF_8)
    }

    boolean jsonMatches(String result, String expected) {
        result == expected
    }

    boolean objRepresentationMatches(Object obj, String json) {
        def expected = jsonMapper.readValue(json, Argument.of(obj.getClass()))
        assert obj == expected
        obj == expected
    }

    @Override
    Map<String, String> getProperties() {
        ["micronaut.serde.serialization.inclusion": SerdeConfig.SerInclude.ALWAYS.name()]
    }
}