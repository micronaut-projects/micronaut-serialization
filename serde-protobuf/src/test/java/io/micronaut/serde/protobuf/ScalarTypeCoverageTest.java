package io.micronaut.serde.protobuf;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Every Java scalar a bean might reasonably hold, checked all the way through.
 *
 * <p>The types protobuf has a native equivalent for are compared against bytes built by
 * protobuf-java. The rest have no equivalent, so they are checked for round-tripping and their
 * chosen representation is pinned, since that representation is what a peer would have to agree
 * with.</p>
 */
class ScalarTypeCoverageTest {

    private static ApplicationContext context;
    private static ProtobufMapper mapper;

    @BeforeAll
    static void setup() {
        context = ApplicationContext.run();
        mapper = context.getBean(ProtobufMapper.class);
    }

    @AfterAll
    static void cleanup() {
        context.close();
    }

    private static <T> T roundTrip(Class<T> type, T value) throws Exception {
        return mapper.readValue(mapper.writeValueAsBytes(Argument.of(type), value), Argument.of(type));
    }

    @Test
    void primitivesMatchTheEquivalentSchema() throws Exception {
        var value = new ScalarModels.Primitives(
            true, (byte) -3, (short) 300, 'Z', -70000, 9_000_000_000L, 1.5f, -2.25d, "text", new byte[]{1, 2, 3});

        Descriptors.Descriptor scalars = ProtoReference.SCALARS;
        byte[] expected = DynamicMessage.newBuilder(scalars)
            .setField(scalars.findFieldByNumber(1), true)
            .setField(scalars.findFieldByNumber(2), -3)
            .setField(scalars.findFieldByNumber(3), 300)
            .setField(scalars.findFieldByNumber(4), (int) 'Z')
            .setField(scalars.findFieldByNumber(5), -70000)
            .setField(scalars.findFieldByNumber(6), 9_000_000_000L)
            .setField(scalars.findFieldByNumber(7), 1.5f)
            .setField(scalars.findFieldByNumber(8), -2.25d)
            .setField(scalars.findFieldByNumber(9), "text")
            .setField(scalars.findFieldByNumber(10), ByteString.copyFrom(new byte[]{1, 2, 3}))
            .build()
            .toByteArray();

        assertArrayEquals(expected, mapper.writeValueAsBytes(Argument.of(ScalarModels.Primitives.class), value));

        ScalarModels.Primitives back = mapper.readValue(expected, Argument.of(ScalarModels.Primitives.class));
        assertEquals(true, back.flag());
        assertEquals((byte) -3, back.tiny());
        assertEquals((short) 300, back.small());
        assertEquals('Z', back.letter());
        assertEquals(-70000, back.whole());
        assertEquals(9_000_000_000L, back.big());
        assertEquals(1.5f, back.single());
        assertEquals(-2.25d, back.wide());
        assertEquals("text", back.text());
        assertArrayEquals(new byte[]{1, 2, 3}, back.blob());
    }

    @Test
    void boxedPrimitivesEncodeLikeTheirPrimitives() throws Exception {
        var boxed = new ScalarModels.Boxed(true, (byte) -3, (short) 300, 'Z', -70000, 9_000_000_000L, 1.5f, -2.25d);

        assertEquals(boxed, roundTrip(ScalarModels.Boxed.class, boxed));
    }

    @Test
    void aNullBoxedValueIsAbsentFromThePayload() throws Exception {
        var boxed = new ScalarModels.Boxed(null, null, null, null, null, null, null, null);

        byte[] payload = mapper.writeValueAsBytes(Argument.of(ScalarModels.Boxed.class), boxed);

        assertEquals(0, payload.length);
        assertEquals(boxed, mapper.readValue(payload, Argument.of(ScalarModels.Boxed.class)));
    }

    @Test
    void extremeNumericValuesSurvive() throws Exception {
        var value = new ScalarModels.Boxed(true, Byte.MIN_VALUE, Short.MIN_VALUE, Character.MAX_VALUE,
            Integer.MIN_VALUE, Long.MIN_VALUE, Float.MIN_VALUE, Double.MAX_VALUE);

        assertEquals(value, roundTrip(ScalarModels.Boxed.class, value));
    }

    @Test
    void nonFiniteFloatingPointValuesSurvive() throws Exception {
        var value = new ScalarModels.Boxed(true, (byte) 0, (short) 0, 'a', 0, 0L, Float.NaN, Double.NEGATIVE_INFINITY);

        ScalarModels.Boxed back = roundTrip(ScalarModels.Boxed.class, value);

        assertEquals(Float.NaN, back.single());
        assertEquals(Double.NEGATIVE_INFINITY, back.wide());
    }

    @Test
    void arbitraryPrecisionNumbersTravelAsStrings() throws Exception {
        var value = new ScalarModels.BigNumbers(
            new BigInteger("123456789012345678901234567890"), new BigDecimal("0.1000"));

        assertEquals(value, roundTrip(ScalarModels.BigNumbers.class, value));
        // protobuf has no arbitrary-precision type, so the decimal representation is the contract
        assertArrayEquals("0.1000".getBytes(StandardCharsets.UTF_8), payloadOfSecondField(value));
    }

    @Test
    void enumsTravelAsTheirNames() throws Exception {
        var value = new ScalarModels.Enums(ScalarModels.Colour.GREEN);

        assertEquals(value, roundTrip(ScalarModels.Enums.class, value));
        // note this is a divergence: protobuf enums are varints of the declared number
        byte[] payload = mapper.writeValueAsBytes(Argument.of(ScalarModels.Enums.class), value);
        assertEquals("GREEN", new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8));
    }

    @Test
    void dateAndTimeTypesSurvive() throws Exception {
        var value = new ScalarModels.Temporal(
            Instant.ofEpochMilli(1787000000000L),
            LocalDate.of(2026, 9, 7),
            LocalDateTime.of(2026, 9, 7, 12, 30, 15),
            LocalTime.of(1, 2, 3),
            ZonedDateTime.of(2026, 9, 7, 12, 30, 15, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2026, 9, 7, 12, 30, 15, 0, ZoneOffset.UTC),
            Year.of(2026),
            Duration.ofSeconds(90),
            new Date(1787000000000L),
            TimeZone.getTimeZone("UTC"));

        assertEquals(value, roundTrip(ScalarModels.Temporal.class, value));
    }

    @Test
    void durationTravelsAsAVarintDespiteBeingAnObject() throws Exception {
        // Duration is serialized as nanoseconds by a serde of its own, so the schema cannot predict
        // its wire type from the Java type. The payload has to be taken at its word.
        var value = new ScalarModels.Temporal(null, null, null, null, null, null, null, Duration.ofSeconds(90), null, null);

        byte[] payload = mapper.writeValueAsBytes(Argument.of(ScalarModels.Temporal.class), value);

        assertEquals(0, payload[0] & 7, "expected wire type 0 (varint)");
        assertEquals(8, payload[0] >>> 3, "expected field number 8");
        assertEquals(Duration.ofSeconds(90),
            mapper.readValue(payload, Argument.of(ScalarModels.Temporal.class)).duration());
    }

    @Test
    void identifierAndLocaleTypesSurvive() throws Exception {
        var value = new ScalarModels.Misc(
            UUID.fromString("6f616b42-0ed8-571e-823f-ee4aca6b7ce9"),
            URI.create("https://example.com/a?b=c"),
            Locale.US,
            StandardCharsets.UTF_8);

        assertEquals(value, roundTrip(ScalarModels.Misc.class, value));
    }

    @Test
    void presentOptionalsSurvive() throws Exception {
        var value = new ScalarModels.Optionals(
            Optional.of("here"), OptionalInt.of(-1), OptionalLong.of(2L), OptionalDouble.of(3.5));

        assertEquals(value, roundTrip(ScalarModels.Optionals.class, value));
    }

    @Test
    void emptyOptionalsAreAbsentFromThePayload() throws Exception {
        var value = new ScalarModels.Optionals(
            Optional.empty(), OptionalInt.empty(), OptionalLong.empty(), OptionalDouble.empty());

        byte[] payload = mapper.writeValueAsBytes(Argument.of(ScalarModels.Optionals.class), value);

        assertEquals(0, payload.length);
        assertEquals(value, mapper.readValue(payload, Argument.of(ScalarModels.Optionals.class)));
    }

    @Test
    void nullReferencesAreAbsentAndComeBackNull() throws Exception {
        var value = new ScalarModels.Nullables(null, null, null);

        byte[] payload = mapper.writeValueAsBytes(Argument.of(ScalarModels.Nullables.class), value);

        assertEquals(0, payload.length);
        ScalarModels.Nullables back = mapper.readValue(payload, Argument.of(ScalarModels.Nullables.class));
        assertNull(back.text());
        assertNull(back.whole());
        assertNull(back.address());
    }

    @Test
    void aValueEqualToItsTypeDefaultIsIndistinguishableFromAbsent() throws Exception {
        // proto3 omits a scalar holding its type's default, so "" and false do not survive as
        // themselves. Anything that must tell "unset" from "empty" needs @Nullable or Optional,
        // which turn the property into an explicit-presence field.
        assertNull(roundTrip(ScalarModels.Nullables.class, new ScalarModels.Nullables("", null, null)).text());
        assertEquals(" ", roundTrip(ScalarModels.Nullables.class, new ScalarModels.Nullables(" ", null, null)).text());
        assertNull(roundTrip(ScalarModels.Nullables.class, new ScalarModels.Nullables(null, null, null)).text());
    }

    @Test
    void nullableAndOptionalPropertiesKeepTheirDefaults() throws Exception {
        // the same values, declared so that presence is explicit, do survive
        var nullable = new ScalarModels.ExplicitPresence("", 0, false, new byte[0]);

        ScalarModels.ExplicitPresence back = roundTrip(ScalarModels.ExplicitPresence.class, nullable);

        assertEquals("", back.text());
        assertEquals(0, back.whole());
        assertEquals(false, back.flag());
        assertArrayEquals(new byte[0], back.blob());

        var optional = new ScalarModels.Optionals(Optional.of(""), OptionalInt.of(0), OptionalLong.of(0L), OptionalDouble.of(0d));
        assertEquals(optional, roundTrip(ScalarModels.Optionals.class, optional));
    }

    private static byte[] payloadOfSecondField(ScalarModels.BigNumbers value) throws Exception {
        byte[] payload = mapper.writeValueAsBytes(Argument.of(ScalarModels.BigNumbers.class), value);
        // skip field 1 (tag, length, payload), then field 2's tag and length
        int cursor = 1;
        cursor += 1 + (payload[cursor] & 0xFF);
        cursor += 1;
        int length = payload[cursor] & 0xFF;
        byte[] result = new byte[length];
        System.arraycopy(payload, cursor + 1, result, 0, length);
        return result;
    }
}
