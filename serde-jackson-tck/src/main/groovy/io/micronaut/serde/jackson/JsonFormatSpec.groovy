package io.micronaut.serde.jackson


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
import java.time.format.DateTimeFormatter

abstract class JsonFormatSpec extends JsonCompileSpec {

    void "test disable validation"() {
        when:
        def i = buildBeanIntrospection('jsongetterrecord.Test', """
package jsongetterrecord;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;


@Serdeable(validate=false)
record Test(
    @JsonFormat(pattern="bunch 'o junk")
    int value) {
}
""")

        then:
        i != null
    }

    @Unroll
    void "test fail compilation when invalid format applied to number for type #type"() {
        when:
        buildBeanIntrospection('jsongetterrecord.Test', """
package jsongetterrecord;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;


@Serdeable
record Test(
    @JsonFormat(pattern="bunch 'o junk")
    $type.name value) {
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Specified pattern [bunch 'o junk] is not a valid decimal format. See the javadoc for DecimalFormat: Malformed pattern \"bunch 'o junk\"")

        where:
        type << [Integer, int.class]
    }

    @Unroll
    void "test fail compilation when invalid format applied to date for type #type"() {
        when:
        buildBeanIntrospection('jsongetterrecord.Test', """
package jsongetterrecord;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;


@Serdeable
record Test(
    @JsonFormat(pattern="bunch 'o junk")
    $type.name value) {
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Specified pattern [bunch 'o junk] is not a valid date format. See the javadoc for DateTimeFormatter: Unknown pattern letter: b")

        where:
        type << [LocalDateTime]
    }

    @Unroll
    void "test json format for #type and settings #settings with record"() {
        given:
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
        expect:
        def beanUnderTest = newInstance(context, 'test.Test', value)
        def typeUnderTest = argumentOf(context, 'test.Test')
        writeJson(jsonMapper, beanUnderTest) == result
        def read = jsonMapper.readValue(result, typeUnderTest)
        typeUnderTest.type.isInstance(read)
        read.value == value

        cleanup:
        context.close()

        where:
        type       | value                       | settings                                   | result
        // locale
        Double     | 100000.12d                  | [pattern: '$###,###.###', locale: 'de_DE'] | '{"value":"$100.000,12"}'

        // without lo
        byte       | 10 as byte                  | [pattern: '$###,###.###']                  | '{"value":"$10"}'
        Byte       | 10 as byte                  | [pattern: '$###,###.###']                  | '{"value":"$10"}'
        int        | 10                          | [pattern: '$###,###.###']                  | '{"value":"$10"}'
        Integer    | 10                          | [pattern: '$###,###.###']                  | '{"value":"$10"}'
        long       | 100000l                     | [pattern: '$###,###.###']                  | '{"value":"$100,000"}'
        Long       | 100000l                     | [pattern: '$###,###.###']                  | '{"value":"$100,000"}'
        short      | 10000 as short              | [pattern: '$###,###.###']                  | '{"value":"$10,000"}'
        Short      | 10000 as short              | [pattern: '$###,###.###']                  | '{"value":"$10,000"}'
        double     | 100000.12d                  | [pattern: '$###,###.###']                  | '{"value":"$100,000.12"}'
        Double     | 100000.12d                  | [pattern: '$###,###.###']                  | '{"value":"$100,000.12"}'
        float      | 100000.12f                  | [pattern: '$###,###.###']                  | '{"value":"$100,000.117"}'
        Float      | 100000.12f                  | [pattern: '$###,###.###']                  | '{"value":"$100,000.117"}'
        BigDecimal | new BigDecimal("100000.12") | [pattern: '$###,###.###']                  | '{"value":"$100,000.12"}'
        BigDecimal | new BigDecimal("100000.12") | [pattern: '$###,###.###']                  | '{"value":"$100,000.12"}'
        BigInteger | new BigInteger("100000")    | [pattern: '$###,###.###']                  | '{"value":"$100,000"}'
        BigInteger | new BigInteger("100000")    | [pattern: '$###,###.###']                  | '{"value":"$100,000"}'

    }

    @Unroll
    void "test json format for #type and settings #settings"() {
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
""", [value: value])
        expect:
        writeJson(jsonMapper, beanUnderTest) == result
        def read = jsonMapper.readValue(result, typeUnderTest)
        typeUnderTest.type.isInstance(read)
        read.value == value

        cleanup:
        context.close()

        where:
        type       | value                       | settings                                   | result
        // locale
        Double     | 100000.12d                  | [pattern: '$###,###.###', locale: 'de_DE'] | '{"value":"$100.000,12"}'

        // without locale
        byte       | 10                          | [pattern: '$###,###.###']                  | '{"value":"$10"}'
        Byte       | 10                          | [pattern: '$###,###.###']                  | '{"value":"$10"}'
        int        | 10                          | [pattern: '$###,###.###']                  | '{"value":"$10"}'
        Integer    | 10                          | [pattern: '$###,###.###']                  | '{"value":"$10"}'
        long       | 100000l                     | [pattern: '$###,###.###']                  | '{"value":"$100,000"}'
        Long       | 100000l                     | [pattern: '$###,###.###']                  | '{"value":"$100,000"}'
        short      | 10000                       | [pattern: '$###,###.###']                  | '{"value":"$10,000"}'
        Short      | 10000                       | [pattern: '$###,###.###']                  | '{"value":"$10,000"}'
        double     | 100000.12d                  | [pattern: '$###,###.###']                  | '{"value":"$100,000.12"}'
        Double     | 100000.12d                  | [pattern: '$###,###.###']                  | '{"value":"$100,000.12"}'
        float      | 100000.12f                  | [pattern: '$###,###.###']                  | '{"value":"$100,000.117"}'
        Float      | 100000.12f                  | [pattern: '$###,###.###']                  | '{"value":"$100,000.117"}'
        BigDecimal | new BigDecimal("100000.12") | [pattern: '$###,###.###']                  | '{"value":"$100,000.12"}'
        BigDecimal | new BigDecimal("100000.12") | [pattern: '$###,###.###']                  | '{"value":"$100,000.12"}'
        BigInteger | new BigInteger("100000")    | [pattern: '$###,###.###']                  | '{"value":"$100,000"}'
        BigInteger | new BigInteger("100000")    | [pattern: '$###,###.###']                  | '{"value":"$100,000"}'

    }

    @Unroll
    void "test json format for date #type and settings #settings"() {
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
""", [value: value])
        def result = writeJson(jsonMapper, beanUnderTest)
        def read = jsonMapper.readValue(result, typeUnderTest)

        expect:
        result.startsWith('{"value":"') // was serialized as string, not long
        typeUnderTest.type.isInstance(read)
        resolver(read.value) == resolver(value)

        cleanup:
        context.close()

        where:
        type           | value                                     | settings                                | resolver
        Instant        | Instant.now()                             | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ"] | { Instant i -> i.toEpochMilli() }
        Date           | new Date()                                | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ"] | { Date d -> d.time }
        java.sql.Date  | new java.sql.Date(2021, 9, 15)            | [pattern: "yyyy-MM-dd"]                 | { java.sql.Date d -> d }
        Timestamp      | new Timestamp(System.currentTimeMillis()) | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ"] | { Timestamp d -> d }
        LocalTime      | LocalTime.now()                           | [pattern: "HH:mm:ss"]                   | { LocalTime i -> i.toSecondOfDay() }
        LocalDate      | LocalDate.now()                           | [pattern: "yyyy-MM-dd"]                 | { LocalDate d -> d }
        LocalDateTime  | LocalDateTime.now()                       | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSS"]  | { LocalDateTime i -> i.toInstant(ZoneOffset.from(ZoneOffset.UTC)).toEpochMilli() }
        ZonedDateTime  | ZonedDateTime.now()                       | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ"] | { ZonedDateTime i -> i.toInstant().toEpochMilli() }
        OffsetDateTime | OffsetDateTime.now()                      | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ"] | { OffsetDateTime i -> i.toInstant().toEpochMilli() }
        Year           | Year.of(2021)                             | [pattern: "yyyy"]                       | { Year y -> y }
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
""", [:])

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
        type           | value           | settings                                                 | resolver                                                                     | expected
        Instant        | "1640995200000" | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone: "UTC"] | { Instant i -> i.toEpochMilli() }                                            | 1640995200000
        Date           | "1640995200000" | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone: "UTC"] | { Date d -> d.getTime() }                                                    | 1640995200000
        java.sql.Date  | "19975"         | [pattern: "yyyy-MM-dd", timezone: "UTC"]                 | { java.sql.Date d -> d.toString() }                                          | LocalDate.ofEpochDay(Integer.parseInt(value)).toString()
        Timestamp      | "1725772522892" | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone: "UTC"] | { Timestamp t -> t.getTime()}                                                | 1725772522892
        LocalTime      | "54922971875000"| [pattern: "HH:mm:ss", timezone: "UTC"]                   | { LocalTime l -> l.toString() }                                              | LocalTime.ofNanoOfDay(Long.parseLong(value)).format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        LocalDate      | "19974"         | [pattern: "yyyy-MM-dd", timezone: "UTC"]                 | { LocalDate d -> d.toString() }                                              | LocalDate.ofEpochDay(Integer.parseInt(value)).toString()
        LocalDateTime  | "1725772522892" | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSS", timezone: "UTC"]  | { LocalDateTime t -> t.atZone(ZoneId.of("UTC")).toInstant().toEpochMilli() } | 1725772522892
        ZonedDateTime  | "1725772522892" | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone: "UTC"] | { ZonedDateTime t -> t.toInstant().toEpochMilli() }                          | 1725772522892
        OffsetDateTime | "1725772522892" | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone: "UTC"] | { OffsetDateTime t -> t.toInstant().toEpochMilli() }                         | 1725772522892
        Year           | "2024"          | [pattern: "yyyy", timezone: "UTC"]                       | { Year y -> y.toString() }                                                   | "2024"
    }

}
