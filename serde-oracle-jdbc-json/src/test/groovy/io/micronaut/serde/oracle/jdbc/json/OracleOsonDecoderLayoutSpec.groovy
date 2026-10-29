package io.micronaut.serde.oracle.jdbc.json

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.config.CoercionPolicy
import io.micronaut.serde.exceptions.InvalidFormatException
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.oracle.jdbc.json.serde.OracleJsonLocaleDateTimeSerde
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Exercises OSON layouts and decoder paths that the Oracle JSON generator does not produce for small documents,
 * with documents built by hand following the python-oracledb layout.
 */
class OracleOsonDecoderLayoutSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared OracleJdbcJsonBinaryObjectMapper mapper = context.getBean(OracleJdbcJsonBinaryObjectMapper)
    @Shared SerdeRegistry registry = context.getBean(SerdeRegistry)

    // ---- document builders ----------------------------------------------------------------------------------

    static class Bytes {
        final ByteArrayOutputStream out = new ByteArrayOutputStream()

        Bytes u8(int... values) {
            values.each { out.write(it & 0xFF) }
            this
        }

        Bytes u16(int v) {
            u8(v >> 8, v)
        }

        Bytes i32(long v) {
            u8((int) (v >> 24), (int) (v >> 16), (int) (v >> 8), (int) v)
        }

        Bytes i64(long v) {
            i32(v >> 32)
            i32(v & 0xFFFFFFFFL)
        }

        Bytes raw(byte[] b) {
            out.write(b)
            this
        }

        Bytes ascii(String s) {
            raw(s.getBytes(StandardCharsets.UTF_8))
        }

        byte[] bytes() {
            out.toByteArray()
        }
    }

    /** A scalar document: header with the scalar flag, a 16 bit tree size, then the node. */
    static byte[] scalarDoc(byte[] node) {
        new Bytes().u8(0xFF, 0x4A, 0x5A, 0x01).u16(0x0010).u16(node.length).raw(node).bytes()
    }

    static byte[] scalarDoc(Closure<Bytes> node) {
        scalarDoc(node.call(new Bytes()).bytes())
    }

    static byte[] doubleNode(double d) {
        long bits = Double.doubleToLongBits(d)
        bits = d >= 0 ? bits | Long.MIN_VALUE : ~bits
        new Bytes().u8(0x36).i64(bits).bytes()
    }

    static byte[] floatNode(float f) {
        int bits = Float.floatToIntBits(f)
        bits = f >= 0 ? bits | Integer.MIN_VALUE : ~bits
        new Bytes().u8(0x7f).i32(bits & 0xFFFFFFFFL).bytes()
    }

    static Bytes dateTime(Bytes b, LocalDateTime t) {
        b.u8(t.year.intdiv(100) + 100, t.year % 100 + 100, t.monthValue, t.dayOfMonth, t.hour + 1, t.minute + 1, t.second + 1)
    }

    static byte[] stringDoc(String s) {
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8)
        scalarDoc { it.u8(0x33, utf8.length).raw(utf8) }
    }

    OracleOsonDecoder decoder(byte[] oson, CoercionPolicy policy = CoercionPolicy.LENIENT) {
        new OracleOsonDecoder(oson, 0, oson.length, LimitingStream.DEFAULT_LIMITS, policy, new OracleOsonFieldNameCache())
    }

    // ---- layouts ---------------------------------------------------------------------------------------------

    def "decodes 32 bit field ids, name offsets, tree offsets and child counts with relative offsets"() {
        given: "{a: {b: 'v'}} with every size flag at 32 bits and offsets relative to their container"
        def tree = new Bytes()
            .u8(0xB0).i32(1).i32(1).i32(13)      // object at 0: 32 bit count and offsets, field a -> 13
            .u8(0xB0).i32(1).i32(2).i32(13)      // object at 13: field b -> 13 relative to 13
            .u8(0x01).ascii("v")                 // string at 26
            .bytes()
        byte[] oson = new Bytes()
            .u8(0xFF, 0x4A, 0x5A, 0x01)
            .u16(0x0001 | 0x0008 | 0x0800 | 0x1000) // relative offsets, uint32 field ids, names and tree
            .i32(2)                                   // number of field names
            .i32(4)                                   // field name segment size
            .i32(tree.length)                         // tree segment size
            .u16(0)                                   // tiny nodes
            .u8(0x61, 0x62)                           // hash ids
            .i32(0).i32(2)                            // field name offsets
            .u8(1).ascii("a").u8(1).ascii("b")        // field names
            .raw(tree)
            .bytes()

        expect:
        decoder(oson).decodeArbitrary() == [a: [b: "v"]]
    }

    def "decodes objects sharing field ids with an object of 16 bit child count"() {
        given: "[{a: 'v'}, {a: 'w'}], the second object reusing the field ids of the first"
        def tree = new Bytes()
            .u8(0xC0, 2).u16(6).u16(14)          // array at 0 with two children
            .u8(0x88).u16(1).u8(1).u16(12)       // object at 6: 16 bit count, field a -> 12
            .u8(0x01).ascii("v")                 // string at 12
            .u8(0x98).u16(6).u16(19)             // object at 14: shares the field ids of 6, value -> 19
            .u8(0x01).ascii("w")                 // string at 19
            .bytes()
        byte[] oson = new Bytes()
            .u8(0xFF, 0x4A, 0x5A, 0x01).u16(0)
            .u8(1).u16(2).u16(tree.length).u16(0)
            .u8(0x61).u16(0).u8(1).ascii("a")
            .raw(tree)
            .bytes()

        expect:
        decoder(oson).decodeArbitrary() == [[a: "v"], [a: "w"]]
    }

    def "decodes documents nested deeper than the initial frame capacity"() {
        given:
        Map<String, Object> doc = [leaf: true]
        12.times { doc = [("level$it".toString()): doc, other: it] }
        byte[] oson = mapper.writeValueAsBytes(doc)

        expect:
        mapper.readValue(oson, Argument.mapOf(String, Object)) == doc
    }

    def "skips unknown object values"() {
        given:
        byte[] oson = mapper.writeValueAsBytes([id: 5, unknown: [nested: [deeper: 1]], title: "t", pages: 2])

        when:
        def book = mapper.readValue(oson, Argument.of(OsonBook))

        then:
        book.id == 5L
        book.title == "t"
        book.pages == 2
    }

    // ---- scalar nodes ----------------------------------------------------------------------------------------

    def "decodes long strings, long binaries and ids"() {
        given:
        String longString = "s" * 70000
        byte[] longBinary = new byte[70000]
        longBinary[0] = 1
        longBinary[69999] = 2

        expect:
        decoder(scalarDoc { it.u8(0x38).i32(70000).ascii(longString) }).decodeString() == longString
        decoder(scalarDoc { it.u8(0x3b).i32(70000).raw(longBinary) }).decodeBinary() == longBinary
        decoder(scalarDoc { it.u8(0x7e, 3, 1, 2, 0xAB) }).decodeBinary() == [1, 2, (byte) 0xAB] as byte[]
        decoder(scalarDoc { it.u8(0x7e, 3, 1, 2, 0xAB) }).decodeString() == "0102ab"
        decoder(scalarDoc { it.u8(0x3a).u16(2).u8(1, 0xAB) }).decodeString() == "01AB"
    }

    def "rejects vectors with a clear error instead of exposing their encoding"() {
        given:
        byte[] vector = scalarDoc { it.u8(0x7b, 0x01).i32(2).u8(9, 8) }

        when:
        decoder(vector).decodeBinary()
        then:
        def e = thrown(IllegalStateException)
        e.message.contains("VECTOR")

        when:
        decoder(vector).decodeString()
        then:
        thrown(IllegalStateException)

        expect: "a document holding a vector can still skip it"
        decoder(new Bytes()
            .u8(0xFF, 0x4A, 0x5A, 0x01).u16(0)
            .u8(1).u16(2).u16(13).u16(0)
            .u8(0x76).u16(0).u8(1).ascii("v")
            .u8(0x84, 1, 1).u16(5)
            .u8(0x7b, 0x01).i32(2).u8(9, 8)
            .bytes()).with {
            def object = decodeObject()
            assert object.decodeKey() == "v"
            object.skipValue()
            assert object.decodeKey() == null
            object.finishStructure()
            true
        }
    }

    def "does not read dates, intervals or binary values as numbers"() {
        when:
        decoder(scalarDoc { dateTime(it.u8(0x3c), LocalDateTime.of(2024, 2, 29, 0, 0)) }).decodeBigDecimal()
        then:
        thrown(SerdeException)

        when:
        decoder(scalarDoc { it.u8(0x3e).i32(0x80000003L).u8(64, 65, 66).i32(0x80000000L) }).decodeLong()
        then:
        thrown(SerdeException)

        when:
        decoder(scalarDoc { it.u8(0x7e, 1, 1) }).decodeInt()
        then:
        thrown(SerdeException)
    }

    def "coerces numbers, booleans and dates to strings"() {
        given:
        def t = LocalDateTime.of(2024, 2, 29, 10, 20, 30)
        def tz = OffsetDateTime.of(t, ZoneOffset.ofHours(2))
        byte[] date = scalarDoc { dateTime(it.u8(0x3c), t) }
        byte[] timestampTz = scalarDoc { dateTime(it.u8(0x7c), t.minusHours(2)).i32(0).u8(22, 60) }

        expect:
        decoder(mapper.writeValueAsBytes(Argument.of(BigDecimal), new BigDecimal("1.5"))).decodeString() == "1.5"
        decoder(scalarDoc(doubleNode(2.5d))).decodeString() == "2.5"
        decoder(scalarDoc(floatNode(0.75f))).decodeString() == "0.75"
        decoder(scalarDoc { it.u8(0x31) }).decodeString() == "true"
        decoder(scalarDoc { it.u8(0x32) }).decodeString() == "false"
        decoder(date).decodeString() == t.toString()
        decoder(timestampTz).decodeString() == "2024-02-29T10:20:30.000000+02:00"
        decoder(timestampTz).decodeLocalDateTime() == t
    }

    def "converts between number representations"() {
        expect:
        decoder(scalarDoc(doubleNode(-2.5d))).decodeLong() == -2L
        decoder(scalarDoc(doubleNode(2.5d))).decodeBigDecimal() == new BigDecimal("2.5")
        decoder(scalarDoc(floatNode(-0.75f))).decodeDouble() == -0.75d
        decoder(scalarDoc(floatNode(3.0f))).decodeLong() == 3L
        decoder(scalarDoc(floatNode(0.75f))).decodeBigDecimal() == new BigDecimal("0.75")
        decoder(scalarDoc(floatNode(0.75f))).decodeArbitrary() == 0.75f
        decoder(scalarDoc(doubleNode(1.25d))).decodeArbitrary() == 1.25d
        decoder(mapper.writeValueAsBytes(Argument.of(BigDecimal), new BigDecimal("7.9"))).decodeLong() == 7L
        decoder(mapper.writeValueAsBytes(Argument.of(BigDecimal), new BigDecimal("-7.9"))).decodeLong() == -7L
        decoder(stringDoc("42")).decodeLong() == 42L
        decoder(stringDoc("4.5")).decodeDouble() == 4.5d
        decoder(stringDoc("4.5")).decodeBigDecimal() == new BigDecimal("4.5")
    }

    def "rejects fractional numbers for integers under strict coercion"() {
        expect:
        decoder(mapper.writeValueAsBytes(Argument.of(Integer), 7), CoercionPolicy.STRICT).decodeInt() == 7
        decoder(mapper.writeValueAsBytes(Argument.of(Long), 0L), CoercionPolicy.STRICT).decodeLong() == 0L

        when:
        decoder(mapper.writeValueAsBytes(Argument.of(BigDecimal), new BigDecimal("1.5")), CoercionPolicy.STRICT).decodeInt()

        then:
        thrown(SerdeException)

        when:
        decoder(scalarDoc(doubleNode(2.0d)), CoercionPolicy.STRICT).decodeInt()

        then:
        thrown(SerdeException)
    }

    def "decodes dates and binaries from strings and arrays"() {
        given:
        def t = LocalDateTime.of(2024, 2, 29, 10, 20, 30)
        def tz = OffsetDateTime.of(t, ZoneOffset.ofHours(2))

        expect:
        decoder(stringDoc(t.toString())).decodeLocalDateTime() == t
        decoder(stringDoc(tz.toString())).decodeOffsetDateTime() == tz
        decoder(stringDoc(tz.toZonedDateTime().toString())).decodeZonedDateTime() == tz.toZonedDateTime()
        decoder(stringDoc("0AFF")).decodeBinary() == [0x0A, (byte) 0xFF] as byte[]
        decoder(mapper.writeValueAsBytes([1, 2, 3])).decodeBinary() == [1, 2, 3] as byte[]
    }

    def "rejects values of the wrong kind for the Oracle specific reads"() {
        given:
        byte[] number = mapper.writeValueAsBytes(Argument.of(Integer), 1)
        byte[] binary = scalarDoc { it.u8(0x7e, 1, 1) }

        when:
        decoder(number).decodeBinary()
        then:
        thrown(IllegalStateException)

        when:
        decoder(scalarDoc { it.u8(0x31) }).decodeBinary()
        then:
        thrown(IllegalStateException)

        when:
        decoder(number).decodeLocalDateTime()
        then:
        thrown(IllegalStateException)

        when:
        decoder(binary).decodeLocalDateTime()
        then:
        thrown(IllegalStateException)

        when:
        decoder(binary).decodeOffsetDateTime()
        then:
        thrown(IllegalStateException)

        when:
        decoder(binary).decodeZonedDateTime()
        then:
        thrown(IllegalStateException)
    }

    def "creates deserialization exceptions with and without the invalid value"() {
        given:
        def d = decoder(stringDoc("x"))

        expect:
        d.createDeserializationException("bad", "x") instanceof InvalidFormatException
        d.createDeserializationException("bad", null).class == SerdeException
    }

    // ---- invalid documents -----------------------------------------------------------------------------------

    def "rejects invalid documents"() {
        given:
        byte[] oson = mapper.writeValueAsBytes([alpha: 1, beta: 2, gamma: 3, delta: 4])
        byte[] wrongVersion = oson.clone()
        wrongVersion[3] = 2

        when:
        mapper.readValue(oson, 5, oson.length, Argument.mapOf(String, Object))
        then:
        thrown(SerdeException)

        when:
        mapper.readValue(Arrays.copyOf(oson, 10), Argument.mapOf(String, Object))
        then:
        thrown(SerdeException)

        when:
        mapper.readValue(wrongVersion, Argument.mapOf(String, Object))
        then:
        thrown(SerdeException)

        when:
        decoder(scalarDoc { it.u8(0x35) })
        then:
        thrown(IllegalStateException)

        when:
        decoder(scalarDoc { it.u8(0x7b, 0x02) })
        then:
        thrown(IllegalStateException)

        when: "a timestamp with a time zone region instead of an offset"
        decoder(scalarDoc { dateTime(it.u8(0x7c), LocalDateTime.of(2024, 1, 1, 0, 0)).i32(0).u8(0x81, 0x01) }).decodeString()
        then:
        thrown(IllegalStateException)
    }

    // ---- field name cache ------------------------------------------------------------------------------------

    def "the field name cache handles collisions, non ASCII names and its size bound"() {
        given:
        def cache = new OracleOsonFieldNameCache()
        byte[] aa = "Aa".getBytes(StandardCharsets.UTF_8)
        byte[] bb = "BB".getBytes(StandardCharsets.UTF_8)
        byte[] accented = "é".getBytes(StandardCharsets.UTF_8)

        expect: "Aa and BB have the same hash"
        cache.resolve(aa, 0, 2) == "Aa"
        cache.resolve(bb, 0, 2) == "BB"
        cache.resolve(aa, 0, 2).is(cache.resolve(aa, 0, 2))

        and:
        cache.resolve(accented, 0, accented.length) == "é"
        cache.resolve(accented, 0, accented.length).is(cache.resolve(accented, 0, accented.length))

        and: "past the bound names are still resolved, just not cached"
        (0..<5000).every {
            byte[] name = "n$it".getBytes(StandardCharsets.UTF_8)
            cache.resolve(name, 0, name.length) == "n$it"
        }
    }

    // ---- serdes and mapper -----------------------------------------------------------------------------------

    def "the Oracle serdes read through the native decoder"() {
        given:
        def t = LocalDateTime.of(2024, 2, 29, 10, 20, 30, 5000)
        byte[] timestamp = scalarDoc { dateTime(it.u8(0x39), t).i32(5000) }
        def serde = context.getBean(OracleJsonLocaleDateTimeSerde)
        def ctx = registry.newDecoderContext(null)
        def fallback = new OsonFallbackStringSerde()

        expect:
        serde.deserialize(decoder(timestamp), ctx, Argument.of(LocalDateTime)) == t
        serde.deserializeNullable(decoder(timestamp), ctx, Argument.of(LocalDateTime)) == t
        serde.deserializeNullable(decoder(scalarDoc { it.u8(0x30) }), ctx, Argument.of(LocalDateTime)) == null
        fallback.deserialize(decoder(stringDoc("x")), ctx, Argument.of(String)) == "default:x"
        fallback.deserializeNullable(decoder(stringDoc("y")), ctx, Argument.of(String)) == "default:y"

        cleanup:
        ctx.close()
    }

    def "a mapper cloned with a view reads natively"() {
        given:
        def viewMapper = (OracleJdbcJsonBinaryObjectMapper) mapper.cloneWithViewClass(Object)
        byte[] oson = mapper.writeValueAsBytes(Argument.of(OsonBook), new OsonBook(id: 3L, title: "v", pages: 1))

        expect:
        viewMapper.readValue(oson, Argument.of(OsonBook)).title == "v"
    }
}
