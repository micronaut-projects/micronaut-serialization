/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.serde.jackson

import com.fasterxml.jackson.annotation.JsonFormat
import io.micronaut.core.type.Argument
import io.micronaut.serde.config.SerdeConfiguration.NumericTimeUnit
import io.micronaut.serde.jackson.shape.EnumObjectShapeBean
import spock.lang.IgnoreIf
import spock.lang.Unroll

import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.Year
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

abstract class JsonFormatSpec extends JsonCompileSpec {
    private static final String LITERAL_Z_TIMESTAMP = '2026-05-07T08:22:23Z'
    private static final String LITERAL_Z_TIMESTAMP_JSON = '{"creationTimestamp":"' + LITERAL_Z_TIMESTAMP + '"}'
    private static final List<Map<String, String>> SHAPE_PROPERTIES = [
        [shape: 'BINARY', field: 'binaryValue'],
        [shape: 'BOOLEAN', field: 'booleanValue'],
        [shape: 'NUMBER', field: 'numberValue'],
        [shape: 'NUMBER_FLOAT', field: 'numberFloatValue'],
        [shape: 'NUMBER_INT', field: 'numberIntValue'],
        [shape: 'STRING', field: 'stringValue'],
        [shape: 'SCALAR', field: 'scalarValue'],
        [shape: 'ARRAY', field: 'arrayValue'],
        [shape: 'OBJECT', field: 'objectValue'],
        [shape: 'ANY', field: 'anyValue'],
        [shape: 'NATURAL', field: 'naturalValue'],
        [shape: 'POJO', field: 'pojoValue']
    ].asImmutable()

    protected boolean supportsClassLevelJsonFormatPropagation() {
        true
    }

    protected List<Map<String, Object>> jsonFormatLiteralZPatternTemporalCases() {
        [
                [
                        typeName: 'java.util.Date',
                        expectedValue: Date.from(Instant.parse(LITERAL_Z_TIMESTAMP)),
                        resolver: { Date d -> d.time }
                ],
                [
                        typeName: 'java.sql.Timestamp',
                        expectedValue: Timestamp.from(Instant.parse(LITERAL_Z_TIMESTAMP)),
                        resolver: { Timestamp t -> t.toInstant() }
                ],
                [
                        typeName: 'java.time.Instant',
                        expectedValue: Instant.parse(LITERAL_Z_TIMESTAMP),
                        resolver: { Instant i -> i }
                ],
                [
                        typeName: 'java.time.OffsetDateTime',
                        expectedValue: OffsetDateTime.parse(LITERAL_Z_TIMESTAMP),
                        resolver: { OffsetDateTime t -> t }
                ],
                [
                        typeName: 'java.time.ZonedDateTime',
                        expectedValue: ZonedDateTime.parse(LITERAL_Z_TIMESTAMP),
                        resolver: { ZonedDateTime t -> t.toInstant() }
                ]
        ]
    }

    void "test json format string shape for number"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private int value;
    public void setValue(int value) {
        this.value = value;
    }
    public int getValue() {
        return value;
    }
}
""", [value: 42])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":"42"}'
        jsonMapper.readValue('{"value":"43"}', typeUnderTest).value == 43

        cleanup:
        context.close()
    }

    void "test json format string shape for number preserves default value checks"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

@Serdeable
class Test {
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private int value;
    public void setValue(int value) {
        this.value = value;
    }
    public int getValue() {
        return value;
    }
}
""")

        expect:
        writeJson(jsonMapper, newInstance(context, 'test.Test', [value: 0])) == '{}'
        writeJson(jsonMapper, newInstance(context, 'test.Test', [value: 42])) == '{"value":"42"}'

        cleanup:
        context.close()
    }

    void "test json format string shape for record number"() {
        given:
        def context = buildContext("""
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
record Test(
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    int value
) {
}
""")
        beanUnderTest = newInstance(context, 'test.Test', [42] as Object[])
        typeUnderTest = argumentOf(context, 'test.Test')

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":"42"}'
        jsonMapper.readValue('{"value":"43"}', typeUnderTest).value == 43

        cleanup:
        context.close()
    }

    protected void assertJsonFormatForNumberSettingsWithRecord(Class<?> type, Object value, Map<String, String> settings, String result) {
        def context = buildContext("""
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;

@Serdeable
record Test(
    @JsonFormat(${settings.collect { "$it.key=\"$it.value\"" }.join(",")})
    $type.name value
) {}
""")
        def beanUnderTest = newInstance(context, 'test.Test', [value] as Object[])
        def typeUnderTest = argumentOf(context, 'test.Test')

        try {
            assert writeJson(jsonMapper, beanUnderTest) == result
            def read = jsonMapper.readValue(result, typeUnderTest)
            assert typeUnderTest.type.isInstance(read)
            assert read.value == value
        } finally {
            context.close()
        }
    }

    protected void assertJsonFormatForNumberSettings(Class<?> type, Object value, Map<String, String> settings, String result) {
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;

@Serdeable
class Test {
    @JsonFormat(${settings.collect { "$it.key=\"$it.value\"" }.join(",")})
    private $type.name value;
    public void setValue($type.name value) {
        this.value = value;
    }
    public $type.name getValue() {
        return value;
    }
}
""", [value: value])

        try {
            assert writeJson(jsonMapper, beanUnderTest) == result
            def read = jsonMapper.readValue(result, typeUnderTest)
            assert typeUnderTest.type.isInstance(read)
            assert read.value == value
        } finally {
            context.close()
        }
    }

    protected static List<Map<String, Object>> jsonFormatNumberSettings() {
        [
            // locale
            [type: Double, value: 100000.12d, settings: [pattern: '$###,###.###', locale: 'de_DE'], result: '{"value":"$100.000,12"}'],

            // without locale
            [type: Byte.TYPE, value: 10 as byte, settings: [pattern: '$###,###.###'], result: '{"value":"$10"}'],
            [type: Byte, value: 10 as byte, settings: [pattern: '$###,###.###'], result: '{"value":"$10"}'],
            [type: Integer.TYPE, value: 10, settings: [pattern: '$###,###.###'], result: '{"value":"$10"}'],
            [type: Integer, value: 10, settings: [pattern: '$###,###.###'], result: '{"value":"$10"}'],
            [type: Long.TYPE, value: 100000l, settings: [pattern: '$###,###.###'], result: '{"value":"$100,000"}'],
            [type: Long, value: 100000l, settings: [pattern: '$###,###.###'], result: '{"value":"$100,000"}'],
            [type: Short.TYPE, value: 10000 as short, settings: [pattern: '$###,###.###'], result: '{"value":"$10,000"}'],
            [type: Short, value: 10000 as short, settings: [pattern: '$###,###.###'], result: '{"value":"$10,000"}'],
            [type: Double.TYPE, value: 100000.12d, settings: [pattern: '$###,###.###'], result: '{"value":"$100,000.12"}'],
            [type: Double, value: 100000.12d, settings: [pattern: '$###,###.###'], result: '{"value":"$100,000.12"}'],
            [type: Float.TYPE, value: 100000.12f, settings: [pattern: '$###,###.###'], result: '{"value":"$100,000.117"}'],
            [type: Float, value: 100000.12f, settings: [pattern: '$###,###.###'], result: '{"value":"$100,000.117"}'],
            [type: BigDecimal, value: new BigDecimal("100000.12"), settings: [pattern: '$###,###.###'], result: '{"value":"$100,000.12"}'],
            [type: BigDecimal, value: new BigDecimal("100000.12"), settings: [pattern: '$###,###.###'], result: '{"value":"$100,000.12"}'],
            [type: BigInteger, value: new BigInteger("100000"), settings: [pattern: '$###,###.###'], result: '{"value":"$100,000"}'],
            [type: BigInteger, value: new BigInteger("100000"), settings: [pattern: '$###,###.###'], result: '{"value":"$100,000"}']
        ]
    }

    @Unroll
    void "test json format string shape for #typeName precision number"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private $typeName value;
    public void setValue($typeName value) {
        this.value = value;
    }
    public $typeName getValue() {
        return value;
    }
}
""", [value: value])

        expect:
        writeJson(jsonMapper, beanUnderTest) == result
        jsonMapper.readValue(result, typeUnderTest).value == value

        cleanup:
        context.close()

        where:
        typeName     | value                                 | result
        'long'       | 9007199254740993L                     | '{"value":"9007199254740993"}'
        'BigDecimal' | new BigDecimal('1234567890.123456789') | '{"value":"1234567890.123456789"}'
    }

    @Unroll
    void "test json format string shape with radix for #typeName"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigInteger;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.STRING, radix = $radix)
    private $typeName value;
    public void setValue($typeName value) {
        this.value = value;
    }
    public $typeName getValue() {
        return value;
    }
}
""", [value: value])

        expect:
        writeJson(jsonMapper, beanUnderTest) == result
        jsonMapper.readValue(result, typeUnderTest).value == value

        cleanup:
        context.close()

        where:
        typeName     | value                  | radix | result
        'byte'       | (byte) 5               | 2     | '{"value":"101"}'
        'Byte'       | Byte.valueOf((byte) 6) | 2     | '{"value":"110"}'
        'short'      | (short) 7              | 2     | '{"value":"111"}'
        'Short'      | Short.valueOf((short) 8) | 2   | '{"value":"1000"}'
        'int'        | 10                     | 16    | '{"value":"a"}'
        'Integer'    | Integer.valueOf(11)    | 16    | '{"value":"b"}'
        'long'       | 12L                    | 16    | '{"value":"c"}'
        'Long'       | Long.valueOf(13L)      | 16    | '{"value":"d"}'
        'BigInteger' | new BigInteger('9')    | 2     | '{"value":"1001"}'
    }

    @Unroll
    void "test json format #shape shape keeps natural number representation"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.$shape)
    private int value;
    public void setValue(int value) {
        this.value = value;
    }
    public int getValue() {
        return value;
    }
}
""", [value: 42])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":42}'
        jsonMapper.readValue('{"value":43}', typeUnderTest).value == 43

        cleanup:
        context.close()

        where:
        shape << ['ANY', 'NATURAL', 'SCALAR', 'NUMBER', 'NUMBER_INT', 'NUMBER_FLOAT', 'BOOLEAN', 'ARRAY', 'OBJECT', 'BINARY', 'POJO']
    }

    @Unroll
    void "test json format #shape shape keeps natural string representation"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.$shape)
    private String value;
    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }
}
""", [value: 'abc'])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":"abc"}'
        jsonMapper.readValue('{"value":"def"}', typeUnderTest).value == 'def'

        cleanup:
        context.close()

        where:
        shape << ['STRING', 'ANY', 'NATURAL', 'SCALAR', 'NUMBER', 'NUMBER_INT', 'NUMBER_FLOAT', 'BOOLEAN', 'ARRAY', 'OBJECT', 'BINARY', 'POJO']
    }

    @Unroll
    void "test json format #shape shape keeps natural scalar representation for #typeName"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.$shape)
    private $typeName value;
    public void setValue($typeName value) {
        this.value = value;
    }
    public $typeName getValue() {
        return value;
    }
}
""", [value: value])

        expect:
        writeJson(jsonMapper, beanUnderTest) == result
        jsonMapper.readValue(readJson, typeUnderTest).value == readValue

        cleanup:
        context.close()

        where:
        typeName       | value | readValue | result            | readJson          | shape
        'CharSequence' | 'abc' | 'def'     | '{"value":"abc"}' | '{"value":"def"}' | 'OBJECT'
        'CharSequence' | 'abc' | 'def'     | '{"value":"abc"}' | '{"value":"def"}' | 'POJO'
    }

    @Unroll
    void "test json format #shape shape for boolean #value"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.$shape)
    private boolean value;
    public void setValue(boolean value) {
        this.value = value;
    }
    public boolean getValue() {
        return value;
    }
}
""", [value: value])

        expect:
        writeJson(jsonMapper, beanUnderTest) == result
        jsonMapper.readValue(result, typeUnderTest).value == value

        cleanup:
        context.close()

        where:
        shape          | value | result
        'STRING'       | true  | '{"value":"true"}'
        'STRING'       | false | '{"value":"false"}'
        'NUMBER'       | true  | '{"value":1}'
        'NUMBER'       | false | '{"value":0}'
        'NUMBER_INT'   | true  | '{"value":1}'
        'NUMBER_INT'   | false | '{"value":0}'
        'NUMBER_FLOAT' | true  | '{"value":1}'
        'NUMBER_FLOAT' | false | '{"value":0}'
        'ANY'          | true  | '{"value":true}'
        'NATURAL'      | true  | '{"value":true}'
        'SCALAR'       | true  | '{"value":true}'
        'BOOLEAN'      | true  | '{"value":true}'
        'ARRAY'        | true  | '{"value":true}'
        'OBJECT'       | true  | '{"value":true}'
        'BINARY'       | true  | '{"value":true}'
        'POJO'         | true  | '{"value":true}'
    }

    void "test json format number shape for primitive and boxed boolean properties"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@Serdeable
@JsonPropertyOrder({ "b1", "b2", "b3" })
class Test {
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private boolean b1;
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Boolean b2;
    private boolean b3;
    public void setB1(boolean b1) {
        this.b1 = b1;
    }
    public boolean isB1() {
        return b1;
    }
    public void setB2(Boolean b2) {
        this.b2 = b2;
    }
    public Boolean getB2() {
        return b2;
    }
    public void setB3(boolean b3) {
        this.b3 = b3;
    }
    public boolean isB3() {
        return b3;
    }
}
""", [b1: true, b2: false, b3: true])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"b1":1,"b2":0,"b3":true}'
        def read = jsonMapper.readValue('{"b1":1,"b2":0,"b3":true}', typeUnderTest)
        read.b1
        read.b2 == false
        read.b3

        cleanup:
        context.close()
    }

    @Unroll
    void "test json format #shape shape for enum"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
enum Choice {
    ALPHA,
    BETA,
    GAMMA
}

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.$shape)
    private Choice value;
    public void setValue(Choice value) {
        this.value = value;
    }
    public Choice getValue() {
        return value;
    }
}
""")
        def value = getEnum(context, 'test.Choice.BETA')
        beanUnderTest = newInstance(context, 'test.Test', [value: value])

        expect:
        assertSpecificSerdeSelection(context, 'test.Choice', true, true)
        writeJson(jsonMapper, beanUnderTest) == result
        jsonMapper.readValue(result, typeUnderTest).value == value

        cleanup:
        context.close()

        where:
        shape          | result
        'STRING'       | '{"value":"BETA"}'
        'NATURAL'      | '{"value":"BETA"}'
        'ANY'          | '{"value":"BETA"}'
        'SCALAR'       | '{"value":"BETA"}'
        'NUMBER'       | '{"value":1}'
        'NUMBER_INT'   | '{"value":1}'
        'NUMBER_FLOAT' | '{"value":1}'
        'ARRAY'        | '{"value":1}'
    }

    @Unroll
    void "test json format enum matrix covers shape #shape"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
enum Choice {
    ALPHA,
    BETA,
    GAMMA
}

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.$shape)
    private Choice value;
    public void setValue(Choice value) {
        this.value = value;
    }
    public Choice getValue() {
        return value;
    }
}
""")
        def value = getEnum(context, 'test.Choice.BETA')
        beanUnderTest = newInstance(context, 'test.Test', [value: value])

        when:
        String serialized = null
        Exception serializationFailure = null
        try {
            serialized = writeJson(jsonMapper, beanUnderTest)
        } catch (Exception e) {
            serializationFailure = e
        }

        then:
        if (serializationFails) {
            assert serializationFailure != null
        } else {
            assert serializationFailure == null
            assert serialized == expectedJson
        }

        when:
        Object deserialized = null
        Exception deserializationFailure = null
        try {
            deserialized = jsonMapper.readValue(readJson, typeUnderTest).value
        } catch (Exception e) {
            deserializationFailure = e
        }

        then:
        if (deserializationFails) {
            assert deserializationFailure != null
        } else {
            assert deserializationFailure == null
            assert deserialized == value
        }

        cleanup:
        context.close()

        where:
        variation << enumFormatShapeVariations()
        shape = variation.shape
        expectedJson = variation.expectedJson
        serializationFails = variation.serializationFails
        readJson = variation.readJson
        deserializationFails = variation.deserializationFails
    }

    @Unroll
    void "test json format unsupported #shape shape for enum serialization"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
enum Choice {
    ALPHA,
    BETA,
    GAMMA
}

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.$shape)
    private Choice value;
    public void setValue(Choice value) {
        this.value = value;
    }
    public Choice getValue() {
        return value;
    }
}
""")
        def value = getEnum(context, 'test.Choice.BETA')
        beanUnderTest = newInstance(context, 'test.Test', [value: value])

        expect:
        jsonMapper.readValue('{"value":"BETA"}', typeUnderTest).value == value

        when:
        writeJson(jsonMapper, beanUnderTest)

        then:
        thrown(Exception)

        cleanup:
        context.close()

        where:
        shape << ['BOOLEAN', 'BINARY']
    }

    @Unroll
    void "test json format #shape shape honors enum json value"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;

@Serdeable
enum State {
    OFF(17),
    ON(31),
    UNKNOWN(99);

    private final int value;
    State(int value) {
        this.value = value;
    }
    @JsonValue
    public int value() {
        return value;
    }
}

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.$shape)
    private State state;
    public void setState(State state) {
        this.state = state;
    }
    public State getState() {
        return state;
    }
}
""")
        def value = getEnum(context, 'test.State.ON')
        beanUnderTest = newInstance(context, 'test.Test', [state: value])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"state":31}'
        jsonMapper.readValue('{"state":31}', typeUnderTest).state == value

        when:
        jsonMapper.readValue('{"state":1}', typeUnderTest)

        then:
        thrown(Exception)

        cleanup:
        context.close()

        where:
        shape << ['NUMBER', 'NUMBER_INT']
    }

    @Unroll
    void "test json format #shape shape serializes enum as object"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
enum Choice {
    ALPHA,
    BETA,
    GAMMA
}

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.$shape)
    private Choice value;
    public void setValue(Choice value) {
        this.value = value;
    }
    public Choice getValue() {
        return value;
    }
}
""")
        def value = getEnum(context, 'test.Choice.BETA')
        beanUnderTest = newInstance(context, 'test.Test', [value: value])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":{}}'
        jsonMapper.readValue('{"value":"BETA"}', typeUnderTest).value == value

        when:
        jsonMapper.readValue('{"value":{}}', typeUnderTest)

        then:
        thrown(Exception)

        cleanup:
        context.close()

        where:
        shape << ['OBJECT', 'POJO']
    }

    void "test json format object shape serializes enum properties and creator reads object"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
}
""")
        def bean = new EnumObjectShapeBean(EnumObjectShapeBean.Status.BETA)

        expect:
        validateJsonWithoutOrder(jsonMapper, '{"value":{"code":2,"label":"Beta"}}', writeJson(jsonMapper, bean))
        jsonMapper.readValue('{"value":{"code":1,"label":"Alpha"}}', Argument.of(EnumObjectShapeBean)).value == EnumObjectShapeBean.Status.ALPHA

        cleanup:
        context.close()
    }

    void "test json format accept case insensitive values for enum"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
enum Choice {
    ALPHA,
    BETA
}

@Serdeable
class Test {
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)
    private Choice value;
    public void setValue(Choice value) {
        this.value = value;
    }
    public Choice getValue() {
        return value;
    }
}
""")
        def value = getEnum(context, 'test.Choice.BETA')

        expect:
        assertSpecificSerdeSelection(context, 'test.Choice', true, true)
        jsonMapper.readValue('{"value":"beta"}', typeUnderTest).value == value

        cleanup:
        context.close()
    }

    void "test json format read unknown enum values as null"() {
        given:
        def context = buildContext('test.Test', """
package test;

import org.jspecify.annotations.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
enum Choice {
    ALPHA,
    BETA
}

@Serdeable
class Test {
    @Nullable
    @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
    private Choice value;
    public void setValue(Choice value) {
        this.value = value;
    }
    public Choice getValue() {
        return value;
    }
}
""")

        expect:
        assertSpecificSerdeSelection(context, 'test.Choice', true, true)
        jsonMapper.readValue('{"value":"GAMMA"}', typeUnderTest).value == null

        cleanup:
        context.close()
    }

    void "test json format read unknown enum values using default value"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
enum Choice {
    ALPHA,
    BETA,
    @JsonEnumDefaultValue
    UNKNOWN
}

@Serdeable
class Test {
    @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
    private Choice value;
    public void setValue(Choice value) {
        this.value = value;
    }
    public Choice getValue() {
        return value;
    }
}
""")
        def defaultValue = getEnum(context, 'test.Choice.UNKNOWN')

        expect:
        jsonMapper.readValue('{"value":"GAMMA"}', typeUnderTest).value == defaultValue

        cleanup:
        context.close()
    }

    void "test json format features select runtime serdes for simple bean and record"() {
        given:
        def context = buildContext('test.FormatBean', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import java.util.Map;

@Serdeable
class FormatBean {
    @JsonFormat(with = {
        JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
        JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED
    })
    private List<String> values;
    @JsonFormat(with = JsonFormat.Feature.WRITE_SORTED_MAP_ENTRIES)
    private Map<Integer, String> numbers;
    public List<String> getValues() {
        return values;
    }
    public void setValues(List<String> values) {
        this.values = values;
    }
    public Map<Integer, String> getNumbers() {
        return numbers;
    }
    public void setNumbers(Map<Integer, String> numbers) {
        this.numbers = numbers;
    }
}

@Serdeable
record FormatRecord(
    @JsonFormat(with = {
        JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
        JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED
    })
    List<String> values,
    @JsonFormat(with = JsonFormat.Feature.WRITE_SORTED_MAP_ENTRIES)
    Map<Integer, String> numbers
) {
}
""")
        def numbers = new LinkedHashMap<Integer, String>()
        numbers.put(10, 'ten')
        numbers.put(2, 'two')
        def bean = newInstance(context, 'test.FormatBean', [
                values: ['alpha'],
                numbers: numbers
        ])
        Class<?> recordType = context.classLoader.loadClass('test.FormatRecord')
        def recordConstructor = recordType.getDeclaredConstructor(List, Map)
        recordConstructor.accessible = true
        def record = recordConstructor.newInstance(['alpha'], numbers)

        expect:
        assertSpecificSerdeSelection(context, 'test.FormatBean', false, false)
        assertSpecificSerdeSelection(context, 'test.FormatRecord', false, false)

        writeJson(jsonMapper, bean).contains('"values":"alpha"')
        writeJson(jsonMapper, bean).contains('"numbers":{"2":"two","10":"ten"}')
        writeJson(jsonMapper, record).contains('"values":"alpha"')
        writeJson(jsonMapper, record).contains('"numbers":{"2":"two","10":"ten"}')

        def readBean = jsonMapper.readValue('{"values":"alpha","numbers":{"10":"ten","2":"two"}}', typeUnderTest)
        readBean.values == ['alpha']
        def readRecord = jsonMapper.readValue('{"values":"alpha","numbers":{"10":"ten","2":"two"}}', Argument.of(recordType))
        readRecord.values() == ['alpha']

        cleanup:
        context.close()
    }

    @IgnoreIf(
        reason = "The runtime mapper does not propagate class-level @JsonFormat to properties",
        value = { !instance.supportsClassLevelJsonFormatPropagation() }
    )
    void "test json format class features propagate to all properties for simple bean and record"() {
        given:
        def context = buildContext('test.ClassFormatBean', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import java.util.Map;

@Serdeable
@JsonFormat(with = {
    JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
    JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED,
    JsonFormat.Feature.WRITE_SORTED_MAP_ENTRIES
})
class ClassFormatBean {
    private List<String> values;
    private List<String> otherValues;
    private Map<Integer, String> numbers;
    private Map<Integer, String> otherNumbers;
    public List<String> getValues() {
        return values;
    }
    public void setValues(List<String> values) {
        this.values = values;
    }
    public List<String> getOtherValues() {
        return otherValues;
    }
    public void setOtherValues(List<String> otherValues) {
        this.otherValues = otherValues;
    }
    public Map<Integer, String> getNumbers() {
        return numbers;
    }
    public void setNumbers(Map<Integer, String> numbers) {
        this.numbers = numbers;
    }
    public Map<Integer, String> getOtherNumbers() {
        return otherNumbers;
    }
    public void setOtherNumbers(Map<Integer, String> otherNumbers) {
        this.otherNumbers = otherNumbers;
    }
}

@Serdeable
@JsonFormat(with = {
    JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
    JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED,
    JsonFormat.Feature.WRITE_SORTED_MAP_ENTRIES
})
record ClassFormatRecord(
    List<String> values,
    List<String> otherValues,
    Map<Integer, String> numbers,
    Map<Integer, String> otherNumbers
) {
}
""")
        def numbers = new LinkedHashMap<Integer, String>()
        numbers.put(10, 'ten')
        numbers.put(2, 'two')
        def otherNumbers = new LinkedHashMap<Integer, String>()
        otherNumbers.put(3, 'three')
        otherNumbers.put(1, 'one')
        def bean = newInstance(context, 'test.ClassFormatBean', [
                values: ['alpha'],
                otherValues: ['beta'],
                numbers: numbers,
                otherNumbers: otherNumbers
        ])
        Class<?> recordType = context.classLoader.loadClass('test.ClassFormatRecord')
        def recordConstructor = recordType.getDeclaredConstructor(List, List, Map, Map)
        recordConstructor.accessible = true
        def record = recordConstructor.newInstance(['alpha'], ['beta'], numbers, otherNumbers)

        when:
        def beanJson = writeJson(jsonMapper, bean)
        def recordJson = writeJson(jsonMapper, record)

        then:
        assertSpecificSerdeSelection(context, 'test.ClassFormatBean', false, false)
        assertSpecificSerdeSelection(context, 'test.ClassFormatRecord', false, false)

        beanJson.contains('"values":"alpha"')
        beanJson.contains('"otherValues":"beta"')
        beanJson.contains('"numbers":{"2":"two","10":"ten"}')
        beanJson.contains('"otherNumbers":{"1":"one","3":"three"}')
        recordJson.contains('"values":"alpha"')
        recordJson.contains('"otherValues":"beta"')
        recordJson.contains('"numbers":{"2":"two","10":"ten"}')
        recordJson.contains('"otherNumbers":{"1":"one","3":"three"}')

        def readBean = jsonMapper.readValue('{"values":"alpha","otherValues":"beta","numbers":{"10":"ten","2":"two"},"otherNumbers":{"3":"three","1":"one"}}', typeUnderTest)
        readBean.values == ['alpha']
        readBean.otherValues == ['beta']
        def readRecord = jsonMapper.readValue('{"values":"alpha","otherValues":"beta","numbers":{"10":"ten","2":"two"},"otherNumbers":{"3":"three","1":"one"}}', Argument.of(recordType))
        readRecord.values() == ['alpha']
        readRecord.otherValues() == ['beta']

        cleanup:
        context.close()
    }

    @IgnoreIf(
        reason = "The runtime mapper does not propagate class-level @JsonFormat to properties",
        value = { !instance.supportsClassLevelJsonFormatPropagation() }
    )
    void "test json format class shape propagates to all numeric properties for simple bean and record"() {
        given:
        def context = buildContext('test.ClassShapeBean', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
@JsonFormat(shape = JsonFormat.Shape.STRING)
class ClassShapeBean {
    private Integer number;
    private Long otherNumber;
    public Integer getNumber() {
        return number;
    }
    public void setNumber(Integer number) {
        this.number = number;
    }
    public Long getOtherNumber() {
        return otherNumber;
    }
    public void setOtherNumber(Long otherNumber) {
        this.otherNumber = otherNumber;
    }
}

@Serdeable
@JsonFormat(shape = JsonFormat.Shape.STRING)
record ClassShapeRecord(Integer number, Long otherNumber) {
}
""")
        def bean = newInstance(context, 'test.ClassShapeBean', [
                number: 12,
                otherNumber: 34L
        ])
        Class<?> recordType = context.classLoader.loadClass('test.ClassShapeRecord')
        def recordConstructor = recordType.getDeclaredConstructor(Integer, Long)
        recordConstructor.accessible = true
        def record = recordConstructor.newInstance(12, 34L)

        expect:
        assertSpecificSerdeSelection(context, 'test.ClassShapeBean', false, false)
        assertSpecificSerdeSelection(context, 'test.ClassShapeRecord', false, false)

        writeJson(jsonMapper, bean).contains('"number":"12"')
        writeJson(jsonMapper, bean).contains('"otherNumber":"34"')
        writeJson(jsonMapper, record).contains('"number":"12"')
        writeJson(jsonMapper, record).contains('"otherNumber":"34"')

        def readBean = jsonMapper.readValue('{"number":"12","otherNumber":"34"}', typeUnderTest)
        readBean.number == 12
        readBean.otherNumber == 34L
        def readRecord = jsonMapper.readValue('{"number":"12","otherNumber":"34"}', Argument.of(recordType))
        readRecord.number() == 12
        readRecord.otherNumber() == 34L

        cleanup:
        context.close()
    }

    @IgnoreIf(
        reason = "The runtime mapper does not propagate class-level @JsonFormat to properties",
        value = { !instance.supportsClassLevelJsonFormatPropagation() }
    )
    void "test json format class pattern propagates to all date properties for simple bean and record"() {
        given:
        def context = buildContext('test.ClassPatternBean', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

@Serdeable
@JsonFormat(pattern = "dd/MM/yyyy", timezone = "UTC")
class ClassPatternBean {
    private Date firstDate;
    private Date secondDate;
    public Date getFirstDate() {
        return firstDate;
    }
    public void setFirstDate(Date firstDate) {
        this.firstDate = firstDate;
    }
    public Date getSecondDate() {
        return secondDate;
    }
    public void setSecondDate(Date secondDate) {
        this.secondDate = secondDate;
    }
}

@Serdeable
@JsonFormat(pattern = "dd/MM/yyyy", timezone = "UTC")
record ClassPatternRecord(Date firstDate, Date secondDate) {
}
""")
        def firstDate = new java.util.Date(0L)
        def secondDate = new java.util.Date(86_400_000L)
        def bean = newInstance(context, 'test.ClassPatternBean', [
                firstDate: firstDate,
                secondDate: secondDate
        ])
        Class<?> recordType = context.classLoader.loadClass('test.ClassPatternRecord')
        def recordConstructor = recordType.getDeclaredConstructor(java.util.Date, java.util.Date)
        recordConstructor.accessible = true
        def record = recordConstructor.newInstance(firstDate, secondDate)

        expect:
        assertSpecificSerdeSelection(context, 'test.ClassPatternBean', false, false)
        assertSpecificSerdeSelection(context, 'test.ClassPatternRecord', false, false)

        writeJson(jsonMapper, bean).contains('"firstDate":"01/01/1970"')
        writeJson(jsonMapper, bean).contains('"secondDate":"02/01/1970"')
        writeJson(jsonMapper, record).contains('"firstDate":"01/01/1970"')
        writeJson(jsonMapper, record).contains('"secondDate":"02/01/1970"')

        def readBean = jsonMapper.readValue('{"firstDate":"01/01/1970","secondDate":"02/01/1970"}', typeUnderTest)
        readBean.firstDate == firstDate
        readBean.secondDate == secondDate
        def readRecord = jsonMapper.readValue('{"firstDate":"01/01/1970","secondDate":"02/01/1970"}', Argument.of(recordType))
        readRecord.firstDate() == firstDate
        readRecord.secondDate() == secondDate

        cleanup:
        context.close()
    }

    void "test json format enum feature selects runtime serdes for simple enum"() {
        given:
        def context = buildContext('test.EnumHolder', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
@JsonFormat(shape = JsonFormat.Shape.STRING)
enum Choice {
    ALPHA,
    BETA
}

@Serdeable
class EnumHolder {
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)
    private Choice value;
    public Choice getValue() {
        return value;
    }
    public void setValue(Choice value) {
        this.value = value;
    }
}
""")
        def beta = getEnum(context, 'test.Choice.BETA')

        expect:
        assertSpecificSerdeSelection(context, 'test.Choice', false, false)
        assertSpecificSerdeSelection(context, 'test.EnumHolder', false, false)
        writeJson(jsonMapper, newInstance(context, 'test.EnumHolder', [value: beta])) == '{"value":"BETA"}'
        jsonMapper.readValue('{"value":"beta"}', typeUnderTest).value == beta

        cleanup:
        context.close()
    }

    void "test json format accept single value as array for collection property"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

@Serdeable
class Test {
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> value;
    public void setValue(List<String> value) {
        this.value = value;
    }
    public List<String> getValue() {
        return value;
    }
}
""")

        expect:
        jsonMapper.readValue('{"value":"alpha"}', typeUnderTest).value == ['alpha']
        jsonMapper.readValue('{"value":["alpha","beta"]}', typeUnderTest).value == ['alpha', 'beta']

        cleanup:
        context.close()
    }

    void "test json format without accept single value as array for collection property"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

@Serdeable
class Test {
    @JsonFormat(without = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> value;
    public void setValue(List<String> value) {
        this.value = value;
    }
    public List<String> getValue() {
        return value;
    }
}
""")

        when:
        jsonMapper.readValue('{"value":"alpha"}', typeUnderTest)

        then:
        thrown(Exception)

        cleanup:
        context.close()
    }

    void "test json format write single element arrays unwrapped for collection property"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

@Serdeable
class Test {
    @JsonFormat(with = JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
    private List<String> value;
    public void setValue(List<String> value) {
        this.value = value;
    }
    public List<String> getValue() {
        return value;
    }
}
""")

        expect:
        writeJson(jsonMapper, newInstance(context, 'test.Test', [value: ['alpha']])) == '{"value":"alpha"}'
        writeJson(jsonMapper, newInstance(context, 'test.Test', [value: ['alpha', 'beta']])) == '{"value":["alpha","beta"]}'

        cleanup:
        context.close()
    }

    void "test json format without write single element arrays unwrapped for collection property"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

@Serdeable
class Test {
    @JsonFormat(without = JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
    private List<String> value;
    public void setValue(List<String> value) {
        this.value = value;
    }
    public List<String> getValue() {
        return value;
    }
}
""")

        expect:
        writeJson(jsonMapper, newInstance(context, 'test.Test', [value: ['alpha']])) == '{"value":["alpha"]}'

        cleanup:
        context.close()
    }

    void "test json format accept single value as array for array properties"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
class Test {
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private String[] strings;
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private int[] numbers;
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private Integer[] boxedNumbers;
    public void setStrings(String[] strings) {
        this.strings = strings;
    }
    public String[] getStrings() {
        return strings;
    }
    public void setNumbers(int[] numbers) {
        this.numbers = numbers;
    }
    public int[] getNumbers() {
        return numbers;
    }
    public void setBoxedNumbers(Integer[] boxedNumbers) {
        this.boxedNumbers = boxedNumbers;
    }
    public Integer[] getBoxedNumbers() {
        return boxedNumbers;
    }
}
""")

        expect:
        def read = jsonMapper.readValue('{"strings":"alpha","numbers":7,"boxedNumbers":9}', typeUnderTest)
        read.strings.toList() == ['alpha']
        read.numbers.toList() == [7]
        read.boxedNumbers.toList() == [9]

        def readArrays = jsonMapper.readValue('{"strings":["alpha","beta"],"numbers":[7,8],"boxedNumbers":[9,10]}', typeUnderTest)
        readArrays.strings.toList() == ['alpha', 'beta']
        readArrays.numbers.toList() == [7, 8]
        readArrays.boxedNumbers.toList() == [9, 10]

        cleanup:
        context.close()
    }

    void "test json format write single element arrays unwrapped for array properties"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
class Test {
    @JsonFormat(with = JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
    private String[] strings;
    @JsonFormat(with = JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
    private int[] numbers;
    @JsonFormat(with = JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
    private Integer[] boxedNumbers;
    public void setStrings(String[] strings) {
        this.strings = strings;
    }
    public String[] getStrings() {
        return strings;
    }
    public void setNumbers(int[] numbers) {
        this.numbers = numbers;
    }
    public int[] getNumbers() {
        return numbers;
    }
    public void setBoxedNumbers(Integer[] boxedNumbers) {
        this.boxedNumbers = boxedNumbers;
    }
    public Integer[] getBoxedNumbers() {
        return boxedNumbers;
    }
}
""")

        expect:
        validateJsonWithoutOrder(jsonMapper, '{"strings":"alpha","numbers":7,"boxedNumbers":9}', writeJson(jsonMapper, newInstance(context, 'test.Test', [
                strings: ['alpha'] as String[],
                numbers: [7] as int[],
                boxedNumbers: [9] as Integer[]
        ])))
        validateJsonWithoutOrder(jsonMapper, '{"strings":["alpha","beta"],"numbers":[7,8],"boxedNumbers":[9,10]}', writeJson(jsonMapper, newInstance(context, 'test.Test', [
                strings: ['alpha', 'beta'] as String[],
                numbers: [7, 8] as int[],
                boxedNumbers: [9, 10] as Integer[]
        ])))

        cleanup:
        context.close()
    }

    void "test json format write sorted map entries"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Map;

@Serdeable
class Test {
    @JsonFormat(with = JsonFormat.Feature.WRITE_SORTED_MAP_ENTRIES)
    private Map<String, Integer> value;
    public void setValue(Map<String, Integer> value) {
        this.value = value;
    }
    public Map<String, Integer> getValue() {
        return value;
    }
}
""")
        def value = new LinkedHashMap<String, Integer>()
        value.put('b', 2)
        value.put('a', 1)
        beanUnderTest = newInstance(context, 'test.Test', [value: value])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":{"a":1,"b":2}}'

        cleanup:
        context.close()
    }

    void "test json format write sorted map entries for comparable non string keys"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Map;

@Serdeable
class Test {
    @JsonFormat(with = JsonFormat.Feature.WRITE_SORTED_MAP_ENTRIES)
    private Map<Integer, String> value;
    public void setValue(Map<Integer, String> value) {
        this.value = value;
    }
    public Map<Integer, String> getValue() {
        return value;
    }
}
""")
        def value = new LinkedHashMap<Integer, String>()
        value.put(10, 'ten')
        value.put(2, 'two')
        beanUnderTest = newInstance(context, 'test.Test', [value: value])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":{"2":"two","10":"ten"}}'

        cleanup:
        context.close()
    }

    void "test json format without write sorted map entries"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Map;

@Serdeable
class Test {
    @JsonFormat(without = JsonFormat.Feature.WRITE_SORTED_MAP_ENTRIES)
    private Map<String, Integer> value;
    public void setValue(Map<String, Integer> value) {
        this.value = value;
    }
    public Map<String, Integer> getValue() {
        return value;
    }
}
""")
        def value = new LinkedHashMap<String, Integer>()
        value.put('b', 2)
        value.put('a', 1)
        beanUnderTest = newInstance(context, 'test.Test', [value: value])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":{"b":2,"a":1}}'

        cleanup:
        context.close()
    }

    void "test json format accept case insensitive properties for bean type"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
class Test {
    private Value value;
    public void setValue(Value value) {
        this.value = value;
    }
    public Value getValue() {
        return value;
    }
}

@Serdeable
@JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
class Value {
    private String text;
    public void setText(String text) {
        this.text = text;
    }
    public String getText() {
        return text;
    }
}
""", [:], [
            'micronaut.serde.deserialization.ignore-unknown': false,
            'jackson.deserialization-features.fail-on-unknown-properties': true
        ])

        expect:
        assertSpecificSerdeSelection(context, 'test.Value', false, false)
        jsonMapper.readValue('{"value":{"TEXT":"alpha"}}', typeUnderTest).value.text == 'alpha'

        cleanup:
        context.close()
    }

    void "test json format accept case insensitive properties for record type"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
class Test {
    private Value value;
    public void setValue(Value value) {
        this.value = value;
    }
    public Value getValue() {
        return value;
    }
}

@Serdeable
@JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
record Value(String text) {
}
""", [:], [
            'micronaut.serde.deserialization.ignore-unknown': false,
            'jackson.deserialization-features.fail-on-unknown-properties': true
        ])

        expect:
        assertSpecificSerdeSelection(context, 'test.Value', false, false)
        jsonMapper.readValue('{"value":{"TEXT":"alpha"}}', typeUnderTest).value.text() == 'alpha'

        cleanup:
        context.close()
    }

    void "test json format without accept case insensitive properties for bean type"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
class Test {
    private Value value;
    public void setValue(Value value) {
        this.value = value;
    }
    public Value getValue() {
        return value;
    }
}

@Serdeable
@JsonFormat(without = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
class Value {
    private String text;
    public void setText(String text) {
        this.text = text;
    }
    public String getText() {
        return text;
    }
}
""", [:], [
            'micronaut.serde.deserialization.ignore-unknown': false,
            'jackson.deserialization-features.fail-on-unknown-properties': true
        ])

        when:
        jsonMapper.readValue('{"value":{"TEXT":"alpha"}}', typeUnderTest)

        then:
        thrown(Exception)

        cleanup:
        context.close()
    }

    void "test json format pojo shape for collection property"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.POJO)
    private CollectionAsPojo value;
    public void setValue(CollectionAsPojo value) {
        this.value = value;
    }
    public CollectionAsPojo getValue() {
        return value;
    }
}

@Serdeable
@JsonFormat(shape = JsonFormat.Shape.POJO)
@JsonPropertyOrder({ "size", "values" })
@JsonIgnoreProperties({ "empty", "first", "last" })
class CollectionAsPojo extends ArrayList<String> {
    public int getSize() {
        return size();
    }
    public List<String> getValues() {
        return new ArrayList<>(this);
    }
    public void setValues(List<String> values) {
        addAll(values);
    }
    public void setSize(int size) {
    }
}
""")
        def value = newInstance(context, 'test.CollectionAsPojo')
        value.add('a')
        value.add('b')
        beanUnderTest = newInstance(context, 'test.Test', [value: value])

        expect:
        validateJsonWithoutOrder(jsonMapper, '{"value":{"size":2,"values":["a","b"]}}', writeJson(jsonMapper, beanUnderTest))
        def read = jsonMapper.readValue('{"value":{"size":2,"values":["c","d"]}}', typeUnderTest)
        read.value.size() == 2
        read.value[0] == 'c'
        read.value[1] == 'd'

        cleanup:
        context.close()
    }

    void "test json format pojo shape for map property"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.LinkedHashMap;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.POJO)
    private MapAsPojo value;
    public void setValue(MapAsPojo value) {
        this.value = value;
    }
    public MapAsPojo getValue() {
        return value;
    }
}

@Serdeable
@JsonFormat(shape = JsonFormat.Shape.POJO)
class MapAsPojo extends LinkedHashMap<String, Integer> {
    private int extra = 13;
    public int getExtra() {
        return extra;
    }
    public void setExtra(int extra) {
        this.extra = extra;
    }
    @Override
    public boolean isEmpty() {
        return super.isEmpty();
    }
}
""")
        def value = newInstance(context, 'test.MapAsPojo')
        value.put('natural', 3)
        beanUnderTest = newInstance(context, 'test.Test', [value: value])

        expect:
        validateJsonWithoutOrder(jsonMapper, '{"value":{"extra":13,"empty":false}}', writeJson(jsonMapper, beanUnderTest))
        def read = jsonMapper.readValue('{"value":{"extra":42}}', typeUnderTest)
        read.value.extra == 42
        read.value.size() == 0

        cleanup:
        context.close()
    }

    void "test json format natural shape overrides pojo map type"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.LinkedHashMap;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.NATURAL)
    private MapAsPojo value;
    public void setValue(MapAsPojo value) {
        this.value = value;
    }
    public MapAsPojo getValue() {
        return value;
    }
}

@Serdeable
@JsonFormat(shape = JsonFormat.Shape.POJO)
class MapAsPojo extends LinkedHashMap<String, Integer> {
    private int extra = 13;
    public int getExtra() {
        return extra;
    }
    public void setExtra(int extra) {
        this.extra = extra;
    }
}
""")
        def value = newInstance(context, 'test.MapAsPojo')
        value.put('natural', 3)
        beanUnderTest = newInstance(context, 'test.Test', [value: value])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":{"natural":3}}'

        cleanup:
        context.close()
    }

    void "test json format natural shape for map entry property"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Map;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.NATURAL)
    private Map.Entry<String, String> entry;
    public void setEntry(Map.Entry<String, String> entry) {
        this.entry = entry;
    }
    public Map.Entry<String, String> getEntry() {
        return entry;
    }
}
""", [entry: new AbstractMap.SimpleEntry<String, String>('foo', 'bar')])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"entry":{"foo":"bar"}}'
        def read = jsonMapper.readValue('{"entry":{"foo":"bar"}}', typeUnderTest)
        read.entry.key == 'foo'
        read.entry.value == 'bar'

        cleanup:
        context.close()
    }

    void "test map entry default shape"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@Serdeable
class Test {
    private Map.Entry<Integer, String> entry;
    public void setEntry(Map.Entry<Integer, String> entry) {
        this.entry = entry;
    }
    public Map.Entry<Integer, String> getEntry() {
        return entry;
    }
}
""", [entry: new AbstractMap.SimpleEntry<Integer, String>(10, 'ten')])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"entry":{"10":"ten"}}'
        def read = jsonMapper.readValue('{"entry":{"10":"ten"}}', typeUnderTest)
        read.entry.key == 10
        read.entry.value == 'ten'

        cleanup:
        context.close()
    }

    void "test json format pojo shape for complex map entry property"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import java.util.Map;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.POJO)
    private Map.Entry<List<Integer>, String[]> entry;
    public void setEntry(Map.Entry<List<Integer>, String[]> entry) {
        this.entry = entry;
    }
    public Map.Entry<List<Integer>, String[]> getEntry() {
        return entry;
    }
}
""", [entry: new AbstractMap.SimpleEntry<List<Integer>, String[]>([42], ['answer'] as String[])])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"entry":{"key":[42],"value":["answer"]}}'
        def read = jsonMapper.readValue('{"entry":{"key":[42],"value":["answer"]}}', typeUnderTest)
        read.entry.key == [42]
        read.entry.value.toList() == ['answer']

        cleanup:
        context.close()
    }

    void "test json format temporal shape matrix covers all shapes"() {
        expect:
        SHAPE_PROPERTIES*.shape as Set == JsonFormat.Shape.values()*.name() as Set
    }

    void "test json format temporal shape matrix covers all temporal types"() {
        expect:
        temporalShapeVariations()*.typeName as Set == [
            'java.util.Date',
            'java.sql.Date',
            'java.sql.Timestamp',
            'java.time.Instant',
            'java.time.LocalDate',
            'java.time.LocalTime',
            'java.time.LocalDateTime',
            'java.time.OffsetDateTime',
            'java.time.ZonedDateTime',
            'java.time.Year'
        ] as Set
    }

    @Unroll
    void "test json format all shapes for java temporal #typeName"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
class Test {
${temporalShapeFields(typeName)}
    public Test() {
    }
    public Test($typeName value) {
${temporalShapeConstructorAssignments()}
    }
${temporalShapeAccessors(typeName)}
}
""")
        beanUnderTest = newInstance(context, 'test.Test', [writeValue] as Object[])
        typeUnderTest = argumentOf(context, 'test.Test')
        def expectedWriteJson = temporalShapeJson(writeExpectedByShape)
        def readJson = temporalShapeJson(readByShape)

        expect:
        validateJsonWithoutOrder(jsonMapper, expectedWriteJson, writeJson(jsonMapper, beanUnderTest))
        temporalShapePropertiesMatch(jsonMapper.readValue(readJson, typeUnderTest), readExpectedValue, resolver)

        cleanup:
        context.close()

        where:
        variation << temporalShapeVariations()
        typeName = variation.typeName
        writeValue = variation.writeValue
        writeExpectedByShape = variation.writeExpectedByShape
        readByShape = variation.readByShape
        readExpectedValue = variation.readExpectedValue
        resolver = variation.resolver
    }

    @Unroll
    void "test json format #shape shape for instant serialization"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.$shape)
    private Instant value;
    public void setValue(Instant value) {
        this.value = value;
    }
    public Instant getValue() {
        return value;
    }
}
""", [value: Instant.ofEpochSecond(123, 456789123)])

        expect:
        writeJson(jsonMapper, beanUnderTest) == result

        cleanup:
        context.close()

        where:
        shape          | result
        'STRING'       | '{"value":"1970-01-01T00:02:03.456789123Z"}'
        'NUMBER'       | '{"value":123.456789123}'
        'NUMBER_FLOAT' | '{"value":123.456789123}'
        'NUMBER_INT'   | '{"value":123456}'
        'ARRAY'        | '{"value":123.456789123}'
        'ANY'          | '{"value":"1970-01-01T00:02:03.456789123Z"}'
        'NATURAL'      | '{"value":"1970-01-01T00:02:03.456789123Z"}'
        'SCALAR'       | '{"value":"1970-01-01T00:02:03.456789123Z"}'
        'BOOLEAN'      | '{"value":"1970-01-01T00:02:03.456789123Z"}'
        'OBJECT'       | '{"value":"1970-01-01T00:02:03.456789123Z"}'
        'BINARY'       | '{"value":"1970-01-01T00:02:03.456789123Z"}'
        'POJO'         | '{"value":"1970-01-01T00:02:03.456789123Z"}'
    }

    @Unroll
    void "test json format #shape shape for instant deserialization"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.$shape)
    private Instant value;
    public void setValue(Instant value) {
        this.value = value;
    }
    public Instant getValue() {
        return value;
    }
}
""", [value: Instant.EPOCH])

        expect:
        jsonMapper.readValue(json, typeUnderTest).value == expected

        cleanup:
        context.close()

        where:
        shape          | json                              | expected
        'STRING'       | '{"value":"1970-01-01T00:02:03Z"}' | Instant.ofEpochSecond(123)
        'NUMBER'       | '{"value":123.456789123}'          | Instant.ofEpochSecond(123, 456789123)
        'NUMBER_FLOAT' | '{"value":123.456789123}'          | Instant.ofEpochSecond(123, 456789123)
        'NUMBER_INT'   | '{"value":123456}'                 | Instant.ofEpochSecond(123456)
        'ARRAY'        | '{"value":123.456789123}'          | Instant.ofEpochSecond(123, 456789123)
    }

    @Unroll
    void "test json format string shape for java time #typeName"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private $typeName value;
    public void setValue($typeName value) {
        this.value = value;
    }
    public $typeName getValue() {
        return value;
    }
}
""", [value: value])

        expect:
        writeJson(jsonMapper, beanUnderTest) == result
        jsonMapper.readValue(result, typeUnderTest).value == value

        cleanup:
        context.close()

        where:
        typeName                  | value                                                                           | result
        'java.time.LocalDate'     | LocalDate.of(2024, 9, 8)                                                        | '{"value":"2024-09-08"}'
        'java.time.LocalTime'     | LocalTime.of(12, 30, 45, 123000000)                                             | '{"value":"12:30:45.123"}'
        'java.time.LocalDateTime' | LocalDateTime.of(2024, 9, 8, 12, 30, 45, 123000000)                             | '{"value":"2024-09-08T12:30:45.123"}'
        'java.time.OffsetDateTime' | OffsetDateTime.of(2024, 9, 8, 12, 30, 45, 123000000, ZoneOffset.UTC)            | '{"value":"2024-09-08T12:30:45.123Z"}'
        'java.time.ZonedDateTime' | ZonedDateTime.of(2024, 9, 8, 12, 30, 45, 123000000, ZoneOffset.UTC)              | '{"value":"2024-09-08T12:30:45.123Z"}'
        'java.time.Year'          | Year.of(2024)                                                                   | '{"value":"2024"}'
    }

    void "test json format string shape overrides global numeric time shape"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant value;
    public void setValue(Instant value) {
        this.value = value;
    }
    public Instant getValue() {
        return value;
    }
}
""", [value: Instant.ofEpochSecond(1640995200)], ['micronaut.serde.time-write-shape': 'INTEGER'])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":"2022-01-01T00:00:00Z"}'
        jsonMapper.readValue('{"value":"2022-01-01T00:00:00Z"}', typeUnderTest).value == Instant.ofEpochSecond(1640995200)

        cleanup:
        context.close()
    }

    void "test json format string shape with pattern for local date"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
    private LocalDate value;
    public void setValue(LocalDate value) {
        this.value = value;
    }
    public LocalDate getValue() {
        return value;
    }
}
""", [value: LocalDate.of(2024, 9, 8)])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":"08/09/2024"}'
        jsonMapper.readValue('{"value":"09/09/2024"}', typeUnderTest).value == LocalDate.of(2024, 9, 9)

        cleanup:
        context.close()
    }

    @Unroll
    void "test json format literal z pattern for temporal #typeName"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
class Test {
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private $typeName creationTimestamp;
    public void setCreationTimestamp($typeName creationTimestamp) {
        this.creationTimestamp = creationTimestamp;
    }
    public $typeName getCreationTimestamp() {
        return creationTimestamp;
    }
}
""")

        expect:
        resolver(jsonMapper.readValue(
            LITERAL_Z_TIMESTAMP_JSON,
            typeUnderTest
        ).creationTimestamp) == resolver(expectedValue)

        cleanup:
        context.close()

        where:
        variation << jsonFormatLiteralZPatternTemporalCases()
        typeName = variation.typeName
        expectedValue = variation.expectedValue
        resolver = variation.resolver
    }

    void "test json format nullable delegating temporal properties"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    private java.util.Date utilDateArray;
    private java.sql.Date sqlDateDefault;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC")
    private java.sql.Date sqlDatePattern;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private java.sql.Timestamp sqlTimestampPattern;

    public java.util.Date getUtilDateArray() {
        return utilDateArray;
    }

    public void setUtilDateArray(java.util.Date utilDateArray) {
        this.utilDateArray = utilDateArray;
    }

    public java.sql.Date getSqlDateDefault() {
        return sqlDateDefault;
    }

    public void setSqlDateDefault(java.sql.Date sqlDateDefault) {
        this.sqlDateDefault = sqlDateDefault;
    }

    public java.sql.Date getSqlDatePattern() {
        return sqlDatePattern;
    }

    public void setSqlDatePattern(java.sql.Date sqlDatePattern) {
        this.sqlDatePattern = sqlDatePattern;
    }

    public java.sql.Timestamp getSqlTimestampPattern() {
        return sqlTimestampPattern;
    }

    public void setSqlTimestampPattern(java.sql.Timestamp sqlTimestampPattern) {
        this.sqlTimestampPattern = sqlTimestampPattern;
    }
}
""")

        expect:
        def value = jsonMapper.readValue('{"utilDateArray":null,"sqlDateDefault":null,"sqlDatePattern":null,"sqlTimestampPattern":null}', argumentOf(context, 'test.Test'))
        value.utilDateArray == null
        value.sqlDateDefault == null
        value.sqlDatePattern == null
        value.sqlTimestampPattern == null

        cleanup:
        context.close()
    }

    @Unroll
    void "test json format pattern without explicit shape for temporal #typeName"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
class Test {
    @JsonFormat(${settings.collect { "$it.key = \"$it.value\"" }.join(", ")})
    private $typeName value;
    public void setValue($typeName value) {
        this.value = value;
    }
    public $typeName getValue() {
        return value;
    }
}
""", [value: writeValue])

        expect:
        writeJson(jsonMapper, beanUnderTest) == expectedWriteJson
        resolver(jsonMapper.readValue(readJson, typeUnderTest).value) == resolver(readExpectedValue)

        cleanup:
        context.close()

        where:
        typeName                   | settings                                                            | writeValue                                                        | expectedWriteJson                         | readJson                                   | readExpectedValue                                           | resolver
        'java.util.Date'           | [pattern: 'yyyy-MM-dd', timezone: 'UTC']                             | new Date(1725753600000L)                                          | '{"value":"2024-09-08"}'                  | '{"value":"2024-09-09"}'                  | new Date(1725840000000L)                                     | { Date d -> d.time }
        'java.sql.Timestamp'       | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone: 'UTC']             | Timestamp.from(Instant.parse('2024-09-08T12:30:45.123Z'))         | '{"value":"2024-09-08T12:30:45.123Z"}'    | '{"value":"2024-09-09T12:30:45.123Z"}'    | Timestamp.from(Instant.parse('2024-09-09T12:30:45.123Z'))    | { Timestamp t -> t.toInstant() }
        'java.time.Instant'        | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone: 'UTC']             | Instant.parse('2024-09-08T12:30:45.123Z')                        | '{"value":"2024-09-08T12:30:45.123Z"}'    | '{"value":"2024-09-09T12:30:45.123Z"}'    | Instant.parse('2024-09-09T12:30:45.123Z')                    | { Instant i -> i }
        'java.time.LocalDate'      | [pattern: 'dd/MM/yyyy']                                              | LocalDate.of(2024, 9, 8)                                          | '{"value":"08/09/2024"}'                  | '{"value":"09/09/2024"}'                  | LocalDate.of(2024, 9, 9)                                     | { LocalDate d -> d }
        'java.time.LocalTime'      | [pattern: 'HH:mm:ss.SSS']                                            | LocalTime.of(12, 30, 45, 123000000)                              | '{"value":"12:30:45.123"}'                | '{"value":"13:30:45.123"}'                | LocalTime.of(13, 30, 45, 123000000)                          | { LocalTime t -> t }
        'java.time.LocalDateTime'  | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSS"]                               | LocalDateTime.of(2024, 9, 8, 12, 30, 45, 123000000)              | '{"value":"2024-09-08T12:30:45.123"}'     | '{"value":"2024-09-09T12:30:45.123"}'     | LocalDateTime.of(2024, 9, 9, 12, 30, 45, 123000000)          | { LocalDateTime t -> t }
        'java.time.OffsetDateTime' | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone: 'UTC']             | OffsetDateTime.of(2024, 9, 8, 12, 30, 45, 123000000, ZoneOffset.UTC) | '{"value":"2024-09-08T12:30:45.123Z"}' | '{"value":"2024-09-09T12:30:45.123Z"}'    | OffsetDateTime.of(2024, 9, 9, 12, 30, 45, 123000000, ZoneOffset.UTC) | { OffsetDateTime t -> t.toInstant() }
        'java.time.ZonedDateTime'  | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone: 'UTC']             | ZonedDateTime.of(2024, 9, 8, 12, 30, 45, 123000000, ZoneOffset.UTC) | '{"value":"2024-09-08T12:30:45.123Z"}'  | '{"value":"2024-09-09T12:30:45.123Z"}'    | ZonedDateTime.of(2024, 9, 9, 12, 30, 45, 123000000, ZoneOffset.UTC)  | { ZonedDateTime t -> t.toInstant() }
        'java.time.Year'           | [pattern: "'year:' yyyy"]                                            | Year.of(2024)                                                    | '{"value":"year: 2024"}'                  | '{"value":"year: 2025"}'                  | Year.of(2025)                                                | { Year y -> y }
    }

    @Unroll
    void "test json format lenient #lenient for #typeName"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "$pattern", timezone = "UTC", lenient = OptBoolean.$lenient)
    private $typeName value;
    public void setValue($typeName value) {
        this.value = value;
    }
    public $typeName getValue() {
        return value;
    }
}
""", [value: writeValue])

        expect:
        writeJson(jsonMapper, beanUnderTest) == expectedWriteJson

        when:
        Object deserialized = null
        Exception failure = null
        try {
            deserialized = jsonMapper.readValue(readJson, typeUnderTest).value
        } catch (Exception e) {
            failure = e
        }

        then:
        if (deserializationFails) {
            assert failure != null
        } else {
            assert failure == null
            assert resolver(deserialized) == resolver(readExpectedValue)
        }

        cleanup:
        context.close()

        where:
        typeName              | lenient | pattern      | writeValue                                             | expectedWriteJson        | readJson                  | readExpectedValue                                      | resolver              | deserializationFails
        'java.util.Date'      | 'TRUE'  | 'yyyy-MM-dd' | Date.from(Instant.parse('2024-02-29T00:00:00Z'))       | '{"value":"2024-02-29"}' | '{"value":"2024-02-30"}' | Date.from(Instant.parse('2024-03-01T00:00:00Z'))      | { Date d -> d.time }  | false
        'java.util.Date'      | 'FALSE' | 'yyyy-MM-dd' | Date.from(Instant.parse('2024-02-29T00:00:00Z'))       | '{"value":"2024-02-29"}' | '{"value":"2024-02-30"}' | null                                                   | { Date d -> d?.time } | true
        'java.time.LocalDate' | 'TRUE'  | 'uuuu-MM-dd' | LocalDate.of(2024, 2, 29)                              | '{"value":"2024-02-29"}' | '{"value":"2024-02-30"}' | LocalDate.of(2024, 2, 29)                             | { LocalDate d -> d }  | false
        'java.time.LocalDate' | 'FALSE' | 'uuuu-MM-dd' | LocalDate.of(2024, 2, 29)                              | '{"value":"2024-02-29"}' | '{"value":"2024-02-30"}' | null                                                   | { LocalDate d -> d }  | true
    }

    @Unroll
    void "test json format compound locale #locale for date"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "$pattern", locale = "$locale", timezone = "UTC")
    private Date value;
    public void setValue(Date value) {
        this.value = value;
    }
    public Date getValue() {
        return value;
    }
}
""", [value: new Date(1665792000000L)])

        expect:
        def json = writeJson(jsonMapper, beanUnderTest)
        json.toLowerCase(Locale.ROOT).contains(expectedText)
        jsonMapper.readValue(json, typeUnderTest).value.time == 1665792000000L

        cleanup:
        context.close()

        where:
        locale        | pattern        | expectedText
        'de_DE'       | 'dd MMM yyyy'  | 'okt'
        'de-DE'       | 'dd MMM yyyy'  | 'okt'
        'it_IT_POSIX' | 'dd MMM yyyy'  | 'ott'
        'fr_FR'       | 'dd MMMM yyyy' | 'octobre'
    }

    void "test json format compound locale for local date"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd MMM yyyy", locale = "de_DE")
    private LocalDate value;
    public void setValue(LocalDate value) {
        this.value = value;
    }
    public LocalDate getValue() {
        return value;
    }
}
""", [value: LocalDate.of(2022, 10, 15)])

        expect:
        def json = writeJson(jsonMapper, beanUnderTest)
        json.toLowerCase(Locale.ROOT).contains('okt')
        jsonMapper.readValue(json, typeUnderTest).value == LocalDate.of(2022, 10, 15)

        cleanup:
        context.close()
    }

    @Unroll
    void "test json format temporal array timestamps as milliseconds for #typeName"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;

@Serdeable
class Test {
    @JsonFormat(
        shape = JsonFormat.Shape.ARRAY,
        without = {
            JsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS,
            JsonFormat.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS
        }
    )
    private $typeName value;
    public void setValue($typeName value) {
        this.value = value;
    }
    public $typeName getValue() {
        return value;
    }
}
""", [value: writeValue])

        expect:
        writeJson(jsonMapper, beanUnderTest) == expectedWriteJson
        jsonMapper.readValue(readJson, typeUnderTest).value == readExpectedValue

        cleanup:
        context.close()

        where:
        typeName                  | writeValue                                           | expectedWriteJson                    | readJson                             | readExpectedValue
        'java.time.LocalTime'     | LocalTime.of(12, 30, 45, 123456789)                  | '{"value":[12,30,45,123]}'           | '{"value":[12,30,45,123]}'           | LocalTime.of(12, 30, 45, 123000000)
        'java.time.LocalDateTime' | LocalDateTime.of(2024, 9, 8, 12, 30, 45, 123456789) | '{"value":[2024,9,8,12,30,45,123]}' | '{"value":[2024,9,8,12,30,45,123]}' | LocalDateTime.of(2024, 9, 8, 12, 30, 45, 123000000)
    }

    void "test json format write dates with zone id for zoned date time"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.ZonedDateTime;

@Serdeable
class Test {
    @JsonFormat(with = JsonFormat.Feature.WRITE_DATES_WITH_ZONE_ID)
    private ZonedDateTime withZoneId;
    @JsonFormat(without = JsonFormat.Feature.WRITE_DATES_WITH_ZONE_ID)
    private ZonedDateTime withoutZoneId;
    public void setWithZoneId(ZonedDateTime withZoneId) {
        this.withZoneId = withZoneId;
    }
    public ZonedDateTime getWithZoneId() {
        return withZoneId;
    }
    public void setWithoutZoneId(ZonedDateTime withoutZoneId) {
        this.withoutZoneId = withoutZoneId;
    }
    public ZonedDateTime getWithoutZoneId() {
        return withoutZoneId;
    }
}
""")
        def value = ZonedDateTime.of(2024, 9, 8, 12, 30, 45, 0, ZoneId.of('Europe/Paris'))
        beanUnderTest = newInstance(context, 'test.Test', [withZoneId: value, withoutZoneId: value])

        expect:
        validateJsonWithoutOrder(
            jsonMapper,
            '{"withZoneId":"2024-09-08T12:30:45+02:00[Europe/Paris]","withoutZoneId":"2024-09-08T12:30:45+02:00"}',
            writeJson(jsonMapper, beanUnderTest)
        )

        cleanup:
        context.close()
    }

    void "test json format adjust dates to context timezone for offset temporal"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.OffsetDateTime;

@Serdeable
class Test {
    @JsonFormat(
        pattern = "yyyy-MM-dd'T'HH:mm:ssXXX",
        timezone = "UTC",
        with = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
    )
    private OffsetDateTime value;
    public void setValue(OffsetDateTime value) {
        this.value = value;
    }
    public OffsetDateTime getValue() {
        return value;
    }
}
""")

        expect:
        jsonMapper.readValue('{"value":"2024-09-08T12:30:45+02:00"}', typeUnderTest).value ==
            OffsetDateTime.of(2024, 9, 8, 10, 30, 45, 0, ZoneOffset.UTC)

        cleanup:
        context.close()
    }

    @Unroll
    void "test deserialize json number format for date #type"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;

@Serdeable
class Test {
    @JsonFormat(${settings.collect { "$it.key=\"$it.value\"" }.join(",")})
    private $type.name value;
    public void setValue($type.name value) {
        this.value = value;
    }
    public $type.name getValue() {
        return value;
    }
}
""", [:], ['micronaut.serde.numeric-time-unit': timeUnit])

        def jsonString = """
{
    "value": ${value}
}
"""
        def read = jsonMapper.readValue(jsonString, typeUnderTest)

        expect:
        resolver(read.value) == expected

        cleanup:
        context.close()

        where:
        type           | timeUnit                     | value                         | settings                                                 | resolver                                                                 | expected
        Instant        | NumericTimeUnit.SECONDS      | "1640995200"                  | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone: "UTC"] | { Instant i -> i.getEpochSecond() }                                      | 1640995200
        Date           | NumericTimeUnit.MILLISECONDS | "1640995200000"               | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone: "UTC"] | { Date d -> d.getTime() }                                                | 1640995200000
        Timestamp      | NumericTimeUnit.MILLISECONDS | "1640995200000"               | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone: "UTC"] | { Timestamp t -> t.getTime()}                                            | 1640995200000
        LocalDate      | NumericTimeUnit.SECONDS      | "19974"                       | [pattern: "yyyy-MM-dd", timezone: "UTC"]                 | { LocalDate d -> d.toString() }                                          | "2024-09-08"
        LocalDateTime  | NumericTimeUnit.SECONDS      | "\"2024-10-18T23:06:24.722\"" | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSS", timezone: "UTC"]  | { LocalDateTime t -> t.atZone(ZoneId.of("UTC")).toInstant().toString() } | "2024-10-18T23:06:24.722Z"
        ZonedDateTime  | NumericTimeUnit.SECONDS      | "1640995200"                  | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone: "UTC"] | { ZonedDateTime t -> t.toString() }                                      | "2022-01-01T00:00Z"
        OffsetDateTime | NumericTimeUnit.SECONDS      | "1640995200"                  | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone: "UTC"] | { OffsetDateTime t -> t.toString() }                                     | "2022-01-01T00:00Z"
        Year           | NumericTimeUnit.SECONDS      | "2024"                        | [pattern: "yyyy", timezone: "UTC"]                       | { Year y -> y.toString() }                                               | "2024"
    }

    private static List<Map<String, Object>> temporalShapeVariations() {
        [
            [
                typeName: 'java.util.Date',
                writeValue: Date.from(Instant.ofEpochMilli(123456)),
                writeExpectedByShape: temporalShapeExpected('"1970-01-01T00:02:03.456Z"', '"1970-01-01T00:02:03.456Z"', '123456', '123456', '"1970-01-01T00:02:03.456Z"'),
                readByShape: temporalShapeExpected('"1970-01-01T00:02:03.456Z"', '"1970-01-01T00:02:03.456Z"', '123456', '123456', '"1970-01-01T00:02:03.456Z"'),
                readExpectedValue: Date.from(Instant.ofEpochMilli(123456)),
                resolver: { Date v -> v.time }
            ],
            [
                typeName: 'java.sql.Date',
                writeValue: new java.sql.Date(123456),
                writeExpectedByShape: temporalShapeExpected('"1970-01-01T00:02:03.456Z"', '"1970-01-01T00:02:03.456Z"', '123456', '123456', '"1970-01-01T00:02:03.456Z"'),
                readByShape: temporalShapeExpected('"1970-01-01T00:02:03.456Z"', '"1970-01-01T00:02:03.456Z"', '123456', '123456', '"1970-01-01T00:02:03.456Z"'),
                readExpectedValue: new java.sql.Date(123456),
                resolver: { java.sql.Date v -> v.time }
            ],
            [
                typeName: 'java.sql.Timestamp',
                writeValue: Timestamp.from(Instant.ofEpochMilli(123456)),
                writeExpectedByShape: temporalShapeExpected('"1970-01-01T00:02:03.456Z"', '"1970-01-01T00:02:03.456Z"', '123456', '123456', '"1970-01-01T00:02:03.456Z"'),
                readByShape: temporalShapeExpected('"1970-01-01T00:02:03.456Z"', '"1970-01-01T00:02:03.456Z"', '123456', '123456', '"1970-01-01T00:02:03.456Z"'),
                readExpectedValue: Timestamp.from(Instant.ofEpochMilli(123456)),
                resolver: { Timestamp v -> v.time }
            ],
            [
                typeName: 'java.time.Instant',
                writeValue: Instant.ofEpochSecond(123, 456789123),
                writeExpectedByShape: temporalShapeExpected('"1970-01-01T00:02:03.456789123Z"', '"1970-01-01T00:02:03.456789123Z"', '123.456789123', '123456'),
                readByShape: temporalShapeExpected('"1970-01-01T00:02:03Z"', '"1970-01-01T00:02:03Z"', '123', '123'),
                readExpectedValue: Instant.ofEpochSecond(123),
                resolver: { Instant v -> v }
            ],
            [
                typeName: 'java.time.LocalDate',
                writeValue: LocalDate.of(1970, 1, 2),
                writeExpectedByShape: temporalShapeExpected('"1970-01-02"', '"1970-01-02"', '[1970,1,2]', '1'),
                readByShape: temporalShapeExpected('"1970-01-02"', '"1970-01-02"', '[1970,1,2]', '1'),
                readExpectedValue: LocalDate.of(1970, 1, 2),
                resolver: { LocalDate v -> v }
            ],
            [
                typeName: 'java.time.LocalTime',
                writeValue: LocalTime.of(12, 30, 45, 123456789),
                writeExpectedByShape: temporalShapeExpected('"12:30:45.123456789"', '"12:30:45.123456789"', '[12,30,45,123456789]', '[12,30,45,123456789]'),
                readByShape: temporalShapeExpected('"12:30:45"', '"12:30:45"', '[12,30,45]', '[12,30,45]'),
                readExpectedValue: LocalTime.of(12, 30, 45),
                resolver: { LocalTime v -> v }
            ],
            [
                typeName: 'java.time.LocalDateTime',
                writeValue: LocalDateTime.of(1970, 1, 1, 0, 2, 3, 456789123),
                writeExpectedByShape: temporalShapeExpected('"1970-01-01T00:02:03.456789123"', '"1970-01-01T00:02:03.456789123"', '[1970,1,1,0,2,3,456789123]', '[1970,1,1,0,2,3,456789123]'),
                readByShape: temporalShapeExpected('"1970-01-01T00:02:03"', '"1970-01-01T00:02:03"', '[1970,1,1,0,2,3]', '[1970,1,1,0,2,3]'),
                readExpectedValue: LocalDateTime.of(1970, 1, 1, 0, 2, 3),
                resolver: { LocalDateTime v -> v }
            ],
            [
                typeName: 'java.time.OffsetDateTime',
                writeValue: OffsetDateTime.of(1970, 1, 1, 0, 2, 3, 456789123, ZoneOffset.UTC),
                writeExpectedByShape: temporalShapeExpected('"1970-01-01T00:02:03.456789123Z"', '"1970-01-01T00:02:03.456789123Z"', '123.456789123', '123456'),
                readByShape: temporalShapeExpected('"1970-01-01T00:02:03Z"', '"1970-01-01T00:02:03Z"', '123', '123'),
                readExpectedValue: OffsetDateTime.of(1970, 1, 1, 0, 2, 3, 0, ZoneOffset.UTC),
                resolver: { OffsetDateTime v -> v.toInstant() }
            ],
            [
                typeName: 'java.time.ZonedDateTime',
                writeValue: ZonedDateTime.of(1970, 1, 1, 0, 2, 3, 456789123, ZoneOffset.UTC),
                writeExpectedByShape: temporalShapeExpected('"1970-01-01T00:02:03.456789123Z"', '"1970-01-01T00:02:03.456789123Z"', '123.456789123', '123456'),
                readByShape: temporalShapeExpected('"1970-01-01T00:02:03Z"', '"1970-01-01T00:02:03Z"', '123', '123'),
                readExpectedValue: ZonedDateTime.of(1970, 1, 1, 0, 2, 3, 0, ZoneOffset.UTC),
                resolver: { ZonedDateTime v -> v.toInstant() }
            ],
            [
                typeName: 'java.time.Year',
                writeValue: Year.of(2024),
                writeExpectedByShape: temporalShapeExpected('2024', '"2024"', '2024', '2024'),
                readByShape: temporalShapeExpected('2024', '"2024"', '2024', '2024'),
                readExpectedValue: Year.of(2024),
                resolver: { Year v -> v }
            ]
        ]
    }

    private static String temporalShapeFields(String typeName) {
        SHAPE_PROPERTIES.collect { property ->
            """    @JsonFormat(shape = JsonFormat.Shape.${property.shape})
    private $typeName ${property.field};"""
        }.join('\n')
    }

    private static String temporalShapeConstructorAssignments() {
        SHAPE_PROPERTIES.collect { property ->
            "        this.${property.field} = value;"
        }.join('\n')
    }

    private static String temporalShapeAccessors(String typeName) {
        SHAPE_PROPERTIES.collect { property ->
            String methodName = methodName(property.field)
            """    public $typeName get$methodName() {
        return ${property.field};
    }
    public void set$methodName($typeName ${property.field}) {
        this.${property.field} = ${property.field};
    }"""
        }.join('\n')
    }

    private static String methodName(String field) {
        field.substring(0, 1).toUpperCase(Locale.ROOT) + field.substring(1)
    }

    private static Map<String, String> temporalShapeExpected(String defaultJson,
                                                             String stringJson,
                                                             String decimalJson,
                                                             String integerJson,
                                                             String arrayJson = decimalJson) {
        SHAPE_PROPERTIES.collectEntries { property ->
            String json
            switch (property.shape) {
                case 'STRING':
                    json = stringJson
                    break
                case 'NUMBER':
                case 'NUMBER_FLOAT':
                    json = decimalJson
                    break
                case 'ARRAY':
                    json = arrayJson
                    break
                case 'NUMBER_INT':
                    json = integerJson
                    break
                default:
                    json = defaultJson
            }
            [(property.field): json]
        }
    }

    private static String temporalShapeJson(Map<String, String> expectedByShape) {
        '{' + SHAPE_PROPERTIES.collect { property ->
            "\"${property.field}\":${expectedByShape[property.field]}"
        }.join(',') + '}'
    }

    private static boolean temporalShapePropertiesMatch(Object bean, Object expected, Closure<?> resolver) {
        def expectedValue = resolver.call(expected)
        SHAPE_PROPERTIES.every { property ->
            resolver.call(bean."${property.field}") == expectedValue
        }
    }

    private static List<Map<String, Object>> enumFormatShapeVariations() {
        JsonFormat.Shape.values().collect { JsonFormat.Shape shape ->
            switch (shape) {
                case JsonFormat.Shape.NUMBER:
                case JsonFormat.Shape.NUMBER_INT:
                case JsonFormat.Shape.NUMBER_FLOAT:
                case JsonFormat.Shape.ARRAY:
                    return [
                        shape: shape.name(),
                        expectedJson: '{"value":1}',
                        serializationFails: false,
                        readJson: '{"value":1}',
                        deserializationFails: false
                    ]
                case JsonFormat.Shape.OBJECT:
                case JsonFormat.Shape.POJO:
                    return [
                        shape: shape.name(),
                        expectedJson: '{"value":{}}',
                        serializationFails: false,
                        readJson: '{"value":{}}',
                        deserializationFails: true
                    ]
                case JsonFormat.Shape.BOOLEAN:
                case JsonFormat.Shape.BINARY:
                    return [
                        shape: shape.name(),
                        expectedJson: null,
                        serializationFails: true,
                        readJson: '{"value":"BETA"}',
                        deserializationFails: false
                    ]
                default:
                    return [
                        shape: shape.name(),
                        expectedJson: '{"value":"BETA"}',
                        serializationFails: false,
                        readJson: '{"value":"BETA"}',
                        deserializationFails: false
                    ]
            }
        }
    }

}
