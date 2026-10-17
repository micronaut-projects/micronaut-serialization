package io.micronaut.serde.oracle.jdbc.json

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.config.CoercionPolicy
import io.micronaut.serde.exceptions.SerdeException
import oracle.jdbc.driver.json.tree.OracleJsonBinaryImpl
import oracle.jdbc.driver.json.tree.OracleJsonDateImpl
import oracle.jdbc.driver.json.tree.OracleJsonDecimalImpl
import oracle.jdbc.driver.json.tree.OracleJsonDoubleImpl
import oracle.jdbc.driver.json.tree.OracleJsonFloatImpl
import oracle.jdbc.driver.json.tree.OracleJsonIntervalDSImpl
import oracle.jdbc.driver.json.tree.OracleJsonIntervalYMImpl
import oracle.jdbc.driver.json.tree.OracleJsonStringImpl
import oracle.jdbc.driver.json.tree.OracleJsonTimestampImpl
import oracle.jdbc.driver.json.tree.OracleJsonTimestampTZImpl
import oracle.sql.json.OracleJsonDatum
import oracle.sql.json.OracleJsonFactory
import oracle.sql.json.OracleJsonObject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneOffset

/**
 * Decodes OSON produced by the Oracle JSON API with the native {@link OracleOsonDecoder}.
 */
class OracleOsonDecoderSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared OracleJdbcJsonBinaryObjectMapper mapper = context.getBean(OracleJdbcJsonBinaryObjectMapper)
    @Shared OracleJdbcJsonTextObjectMapper textMapper = context.getBean(OracleJdbcJsonTextObjectMapper)
    @Shared OracleJsonFactory factory = new OracleJsonFactory()

    def "decodes a flat entity written as OSON"() {
        given:
        def book = new OsonBook(id: 42L, title: "The title", pages: 321)
        byte[] oson = mapper.writeValueAsBytes(Argument.of(OsonBook), book)

        when:
        def first = mapper.readValue(oson, Argument.of(OsonBook))
        def second = mapper.readValue(new OracleJsonDatum(mapper.writeValueAsBytes(Argument.of(OsonBook), new OsonBook(id: 7L, title: "Another, with a longer title than before", pages: 0))), Argument.of(OsonBook))

        then:
        first.id == 42L
        first.title == "The title"
        first.pages == 321
        second.id == 7L
        second.title == "Another, with a longer title than before"
        second.pages == 0
    }

    def "decodes nested objects, arrays, nulls, booleans, decimals and skips unknown fields"() {
        given:
        def doc = new OsonNested(name: "n", flag: true, ratio: new BigDecimal("3.25"), scores: [1, 2, 3],
            inner: new OsonInner(label: "x", missing: null), extra: [a: 1, b: [true, false], c: [d: "e"]],
            inners: [new OsonInner(label: "p"), new OsonInner(label: "q", missing: "m")])
        byte[] oson = mapper.writeValueAsBytes(Argument.of(OsonNested), doc)

        when:
        def read = mapper.readValue(oson, Argument.of(OsonNested))

        then:
        read.name == "n"
        read.flag
        read.ratio == new BigDecimal("3.25")
        read.scores == [1, 2, 3]
        read.inner.label == "x"
        read.inner.missing == null
        read.extra == [a: 1, b: [true, false], c: [d: "e"]]
        read.inners*.label == ["p", "q"]
        read.inners*.missing == [null, "m"]

        when: "read with a slice of a larger array"
        byte[] padded = new byte[oson.length + 7]
        System.arraycopy(oson, 0, padded, 3, oson.length)
        def sliced = mapper.readValue(padded, 3, oson.length, Argument.of(OsonNested))

        then:
        sliced.name == "n"
        sliced.extra == read.extra
    }

    def "decodes every scalar type the Oracle JSON API produces"() {
        given:
        def ts = LocalDateTime.of(2024, 2, 29, 13, 45, 59, 123456789)
        def tz = OffsetDateTime.of(2024, 2, 29, 23, 15, 0, 500000000, ZoneOffset.ofHoursMinutes(-5, -30))
        def duration = Duration.ofDays(3).plusHours(4).plusMinutes(5).plusSeconds(6).plusNanos(700000000)
        def period = Period.of(2, 11, 0)
        byte[] bin = [1, 2, 3, (byte) 0xAB, (byte) 0xFF] as byte[]
        String longStr = "x" * 300
        OracleJsonObject obj = factory.createObject()
        obj.put("str", "hello wörld")
        obj.put("longStr", longStr)
        obj.put("yes", true)
        obj.put("no", false)
        obj.putNull("nothing")
        obj.put("i", 12345)
        obj.put("l", 9007199254740993L)
        obj.put("negative", -4200L)
        obj.put("big", new BigDecimal("123456789012345678901234567890"))
        obj.put("decimal", new BigDecimal("-0.000123"))
        obj.put("negativeDecimal", new BigDecimal("-98765.4321"))
        obj.put("dbl", new OracleJsonDoubleImpl(3.5d))
        obj.put("negativeDbl", new OracleJsonDoubleImpl(-1.25e-3d))
        obj.put("flt", new OracleJsonFloatImpl(2.75f))
        obj.put("bin", new OracleJsonBinaryImpl(bin, false))
        obj.put("binAsHex", new OracleJsonBinaryImpl(bin, false))
        obj.put("timestamp", new OracleJsonTimestampImpl(ts))
        obj.put("date", new OracleJsonDateImpl(ts.withNano(0)))
        obj.put("day", new OracleJsonDateImpl(ts.withNano(0)))
        obj.put("time", new OracleJsonTimestampImpl(ts))
        obj.put("tz", new OracleJsonTimestampTZImpl(tz))
        obj.put("zoned", new OracleJsonTimestampTZImpl(tz))
        obj.put("duration", new OracleJsonIntervalDSImpl(duration))
        obj.put("period", new OracleJsonIntervalYMImpl(period))
        byte[] oson = mapper.writeValueAsBytes(obj)

        when:
        def read = mapper.readValue(oson, Argument.of(OsonScalars))
        def viaParser = mapper.readValue(new ByteArrayInputStream(oson), Argument.of(OsonScalars))

        then:
        read.str() == "hello wörld"
        read.longStr() == longStr
        read.yes()
        !read.no()
        read.nothing() == null
        read.i() == 12345
        read.l() == 9007199254740993L
        read.negative() == -4200L
        read.big() == new BigInteger("123456789012345678901234567890")
        read.decimal() == new BigDecimal("-0.000123")
        read.negativeDecimal() == new BigDecimal("-98765.4321")
        read.dbl() == 3.5d
        read.negativeDbl() == -1.25e-3d
        read.flt() == 2.75f
        read.bin() == bin
        read.binAsHex() == OracleJsonBinaryImpl.getString(bin, false)
        read.timestamp() == ts
        read.date() == ts.withNano(0)
        read.day() == ts.toLocalDate()
        read.time() == ts.toLocalTime()
        read.tz() == tz
        read.tz().offset == tz.offset
        read.zoned().toOffsetDateTime() == tz
        read.duration() == duration
        read.period() == period

        and: "the parser path reads the same values"
        viaParser.toString().replaceAll("\\[B@\\w+", "") == read.toString().replaceAll("\\[B@\\w+", "")
        viaParser.bin() == read.bin()
    }

    def "decodes generic maps and lists with numbers of every width"() {
        given:
        def values = [tiny: 1, small: 200, medium: 70000, large: 5000000000L, frac: 1.5, neg: -7, zero: 0,
                      list: [1, [2, 3], [k: "v"]], str: "s", none: null, t: true]
        byte[] oson = mapper.writeValueAsBytes(values)

        when:
        Map read = mapper.readValue(oson, Argument.mapOf(String, Object))

        then:
        read.keySet() == values.keySet()
        read.tiny == 1
        read.small == 200
        read.medium == 70000
        read.large == 5000000000L
        read.frac == 1.5
        read.neg == -7
        read.zero == 0
        read.list == [1, [2, 3], [k: "v"]]
        read.str == "s"
        read.none == null
        read.t == true

        and: "the text mapper agrees"
        textMapper.readValue(textMapper.writeValueAsString(read), Map) == textMapper.readValue(textMapper.writeValueAsString(mapper.readValue(new ByteArrayInputStream(oson), Argument.mapOf(String, Object))), Map)
    }

    def "decodes top level arrays and scalars"() {
        expect:
        mapper.readValue(mapper.writeValueAsBytes([1, "two", [3]]), Argument.listOf(Object)) == [1, "two", [3]]
        mapper.readValue(mapper.writeValueAsBytes(Argument.of(String), "just a string"), Argument.of(String)) == "just a string"
        mapper.readValue(mapper.writeValueAsBytes(Argument.of(Integer), 77), Argument.of(Integer)) == 77
        mapper.readValue(mapper.writeValueAsBytes(Argument.of(Boolean), true), Argument.of(Boolean))
    }

    def "decodes documents with many and with long field names"() {
        given: "more than 255 fields forces 16 bit field ids, a long name forces the long name dictionary"
        Map<String, Object> wide = [:]
        300.times { wide["field_$it".toString()] = it }
        wide["n" * 300] = "long"
        byte[] oson = mapper.writeValueAsBytes(wide)

        when:
        Map read = mapper.readValue(oson, Argument.mapOf(String, Object))

        then:
        read == wide
    }

    def "shares field names between documents of the same shape"() {
        given:
        byte[] one = mapper.writeValueAsBytes(Argument.of(OsonBook), new OsonBook(id: 1L, title: "a", pages: 1))
        byte[] two = mapper.writeValueAsBytes(Argument.of(OsonBook), new OsonBook(id: 2L, title: "b", pages: 2))
        def cache = new OracleOsonFieldNameCache()

        when:
        Map first = mapper.readValue(one, Argument.mapOf(String, Object))
        Map second = mapper.readValue(two, Argument.mapOf(String, Object))
        def keysOfFirst = first.keySet().toList()
        def keysOfSecond = second.keySet().toList()

        then:
        keysOfFirst == ["id", "title", "pages"]
        (0..2).every { keysOfFirst[it].is(keysOfSecond[it]) }

        and: "a fresh cache yields distinct instances for the first document only"
        def d1 = new OracleOsonDecoder(one, 0, one.length, LimitingStream.DEFAULT_LIMITS, CoercionPolicy.LENIENT, cache)
        def d2 = new OracleOsonDecoder(two, 0, two.length, LimitingStream.DEFAULT_LIMITS, CoercionPolicy.LENIENT, cache)
        def k1 = d1.decodeObject().decodeKey()
        def k2 = d2.decodeObject().decodeKey()
        k1 == "id"
        k1.is(k2)
    }

    def "rejects bytes that are not OSON"() {
        when:
        mapper.readValue("{\"a\":1}".getBytes("UTF-8"), Argument.mapOf(String, Object))

        then:
        thrown(SerdeException)

        when:
        mapper.readValue([(byte) 0xFF, (byte) 0x4A] as byte[], Argument.mapOf(String, Object))

        then:
        thrown(SerdeException)
    }

    def "still reads the Oracle JSON tree types through the parser"() {
        given:
        byte[] oson = mapper.writeValueAsBytes([a: 1, b: [1, 2]])

        expect:
        mapper.readValue(oson, OracleJsonObject).getInt("a") == 1
        mapper.readValue(new OracleJsonDatum(oson), Argument.of(OracleJsonObject)).getArray("b").size() == 2
    }
}
