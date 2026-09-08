package io.micronaut.serde.protobuf;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static io.micronaut.serde.protobuf.WireBytes.concat;
import static io.micronaut.serde.protobuf.WireBytes.of;
import static io.micronaut.serde.protobuf.WireBytes.tag;
import static io.micronaut.serde.protobuf.WireBytes.utf8;
import static io.micronaut.serde.protobuf.WireBytes.varint;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The decoder's input is attacker-controlled, so a malformed payload has to fail as a rejected
 * payload rather than as an error the caller cannot catch: no unchecked exception escaping a method
 * that declares {@code IOException}, no unbounded allocation, no unbounded recursion, and no work
 * disproportionate to the payload's size.
 */
class UntrustedPayloadTest {

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

    /**
     * Every message in the chain: a wrapped cause carries the detail, the wrapper carries the
     * explanation, and a test should be able to assert on either.
     */
    private static String message(Throwable e) {
        StringBuilder text = new StringBuilder();
        for (Throwable cause = e; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            text.append(cause.getMessage()).append(" | ");
        }
        return text.toString();
    }

    @Test
    void aStackOfGroupTagsDoesNotExhaustTheStack() {
        // a start-group tag is one byte, so skipping groups recursively made recursion depth track
        // payload length. Errors are not Exceptions, so this has to assert on Throwable.
        byte[] payload = new byte[100_000];
        java.util.Arrays.fill(payload, (byte) 0x0B);

        Throwable e = assertThrows(Throwable.class, () -> mapper.readValue(payload, Argument.of(Person.class)));

        assertTrue(e instanceof IOException || e.getCause() instanceof IOException,
            () -> "expected an IOException, got " + e.getClass().getName());
        assertTrue(message(e).contains("group encoding is not supported"), message(e));
    }

    @Test
    void aLengthPrefixNearIntegerMaxDoesNotOverflowTheBoundsCheck() {
        // 0x7FFFFFFF as the length of field 1 on a six-byte payload: position + length wraps
        // negative, so an additive bounds check would accept it
        byte[] payload = concat(tag(1, 2), of(0xFF, 0xFF, 0xFF, 0xFF, 0x07));

        Exception e = assertThrows(Exception.class, () -> mapper.readValue(payload, Argument.of(Person.class)));

        assertTrue(message(e).contains("reaches past the end of its enclosing structure"), message(e));
    }

    @Test
    void aValueInsideANestedMessageCannotReadPastIt() throws Exception {
        // the nested message declares one byte and then contains a fixed64 tag: without the limit
        // reaching the varint and fixed reads, those eight bytes would be taken from the parent and
        // then read again by the parent as its own fields
        byte[] payload = concat(
            tag(3, 2), varint(1), tag(1, 1),
            of(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88));

        Exception e = assertThrows(Exception.class, () -> mapper.readValue(payload, Argument.of(Person.class)));

        assertTrue(message(e).contains("Truncated") || message(e).contains("reaches past"), message(e));
    }

    @Test
    void aStringFieldThatIsNotValidUtf8IsRejected() {
        // 0xFF is not a legal UTF-8 byte; the String constructor would silently substitute U+FFFD
        byte[] payload = concat(tag(1, 2), varint(3), of(0xFF, 0xFE, 0xFD));

        Exception e = assertThrows(Exception.class, () -> mapper.readValue(payload, Argument.of(Person.class)));

        assertTrue(message(e).contains("not valid UTF-8"), message(e));
    }

    @Test
    void validNonAsciiStringsStillDecode() throws Exception {
        byte[] text = "café 世界 🚀".getBytes(StandardCharsets.UTF_8);
        byte[] payload = concat(tag(1, 2), varint(text.length), text);

        assertEquals("café 世界 🚀", mapper.readValue(payload, Argument.of(Person.class)).name());
    }

    @Test
    void anEnormousDigitStringIsRefusedRatherThanParsed() {
        // parsing a decimal string into a BigInteger is superlinear, so an unbounded one lets a
        // small payload occupy a thread for minutes
        byte[] digits = "1".repeat(200_000).getBytes(StandardCharsets.UTF_8);
        byte[] payload = concat(tag(1, 2), varint(digits.length), digits);

        Exception e = assertThrows(Exception.class,
            () -> mapper.readValue(payload, Argument.of(BigNumberHolder.class)));

        assertTrue(message(e).contains("above the limit of 1000"), message(e));
    }

    @Test
    void numbersWithinTheLimitStillDecode() throws Exception {
        String digits = "9".repeat(1000);
        byte[] text = utf8(digits);
        byte[] payload = concat(tag(1, 2), varint(text.length), text);

        assertEquals(new BigInteger(digits),
            mapper.readValue(payload, Argument.of(BigNumberHolder.class)).big());
    }

    @Test
    void aPayloadThatExpandsFarBeyondItsOwnSizeIsRefused() {
        // each level wraps the one below in three more bytes, so the payload stays small while the
        // normalized copies it produces grow with the nesting depth
        byte[] payload = utf8("x".repeat(100_000));
        payload = concat(tag(1, 2), varint(payload.length), payload);
        for (int i = 0; i < 300; i++) {
            payload = concat(tag(2, 2), varint(payload.length), payload);
        }

        byte[] built = payload;
        Exception e = assertThrows(Exception.class, () -> mapper.readValue(built, Argument.of(Recursive.class)));

        assertTrue(message(e).contains("expands to more than"), message(e));
    }

    @Test
    void ordinaryNestingIsUnaffectedByTheBudget() {
        byte[] payload = utf8("leaf");
        payload = concat(tag(1, 2), varint(payload.length), payload);
        for (int i = 0; i < 20; i++) {
            payload = concat(tag(2, 2), varint(payload.length), payload);
        }

        byte[] built = payload;
        assertDoesNotThrow(() -> mapper.readValue(built, Argument.of(Recursive.class)));
    }

    @Test
    void aTruncatedPackedRunCannotConsumeTheFieldAfterIt() {
        // field 5 is a packed repeated int32. The run declares two bytes and ends mid-varint, so an
        // unbounded read would carry on into field 1 and decode its bytes as part of the number.
        byte[] payload = concat(
            concat(tag(5, 2), varint(2), of(0x01, 0x80)),
            concat(tag(1, 2), varint(3), utf8("abc")));

        Exception e = assertThrows(Exception.class, () -> mapper.readValue(payload, Argument.of(Person.class)));

        assertTrue(message(e).contains("Truncated"), message(e));
    }

    @Test
    void aPackedRunOfFixedWidthValuesIsBoundedToo() {
        // field 8 is a double; a fixed64 declared inside a two-byte run must not reach past it
        byte[] payload = concat(
            concat(tag(3, 2), varint(2), tag(1, 1)),
            of(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88));

        Exception e = assertThrows(Exception.class, () -> mapper.readValue(payload, Argument.of(Person.class)));

        assertTrue(message(e).contains("Truncated") || message(e).contains("reaches past"), message(e));
    }

    @Test
    void bigDecimalIsCappedTheSameWay() {
        byte[] digits = ("1." + "0".repeat(200_000)).getBytes(StandardCharsets.UTF_8);
        byte[] payload = concat(tag(2, 2), varint(digits.length), digits);

        Exception e = assertThrows(Exception.class,
            () -> mapper.readValue(payload, Argument.of(BigNumberHolder.class)));

        assertTrue(message(e).contains("above the limit of 1000"), message(e));
        assertEquals(BigDecimal.class, BigDecimal.class);
    }
}
