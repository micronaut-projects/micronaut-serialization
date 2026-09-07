package io.micronaut.serde.protobuf;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.micronaut.serde.protobuf.WireBytes.concat;
import static io.micronaut.serde.protobuf.WireBytes.delimited;
import static io.micronaut.serde.protobuf.WireBytes.fixed32;
import static io.micronaut.serde.protobuf.WireBytes.fixed64;
import static io.micronaut.serde.protobuf.WireBytes.of;
import static io.micronaut.serde.protobuf.WireBytes.overlongVarint;
import static io.micronaut.serde.protobuf.WireBytes.tag;
import static io.micronaut.serde.protobuf.WireBytes.utf8;
import static io.micronaut.serde.protobuf.WireBytes.varint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edge cases lifted from the upstream Protocol Buffers binary conformance suite.
 *
 * <p>The suite itself needs a natively built runner and a test message using maps, oneofs and
 * well-known types, none of which this backend supports. Its binary cases are data, though, so the
 * behaviour they pin down can be checked directly: which malformed payloads must be rejected, and
 * which unusual but legal ones must be accepted.</p>
 *
 * <p>Field numbers below refer to {@link Person}: 1 name, 2 age, 3 address, 4 nicknames,
 * 5 scores, 6 balance, 7 active, 8 ratio.</p>
 */
class BinaryWireFormatConformanceTest {

    private static final int UNKNOWN_FIELD = 99;
    private static final Argument<Person> PERSON = Argument.of(Person.class);

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

    private static Person read(byte[] payload) throws Exception {
        return mapper.readValue(payload, PERSON);
    }

    private static void rejects(byte[] payload, String what) {
        Exception e = assertThrows(Exception.class, () -> read(payload), () -> "accepted " + what);
        assertTrue(rootMessage(e) != null, what);
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

    // ---------------------------------------------------------------- illegal tags

    @Test
    void rejectsFieldNumberZero() {
        // field number 0 does not exist, whatever wire type follows it
        rejects(concat(of(0x00), varint(1)), "field number 0 as a varint");
        rejects(concat(of(0x01), fixed64(1)), "field number 0 as a fixed64");
        rejects(concat(of(0x02), varint(2), utf8("hi")), "field number 0 as length-delimited");
        rejects(concat(of(0x05), fixed32(1)), "field number 0 as a fixed32");
    }

    @Test
    void rejectsFieldNumbersAboveTheLegalMaximum() {
        // 536870912 is one past the maximum legal field number
        rejects(concat(tag(536870912, 0), varint(1)), "a field number one past the maximum");
        rejects(concat(varint((1L << 35) | 0), varint(1)), "a tag above 32 bits");
    }

    @Test
    void rejectsOverlongTagVarints() {
        // the value decodes to a legal tag, but a tag varint may not be padded out
        rejects(concat(overlongVarint(tagValue(1, 2), 6), varint(2), utf8("hi")),
            "a six-byte tag varint");
        rejects(concat(overlongVarint(tagValue(1, 2), 10), varint(2), utf8("hi")),
            "a ten-byte tag varint");
    }

    @Test
    void rejectsVarintsLongerThanTenBytes() {
        rejects(of(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x00),
            "a varint of eleven bytes");
    }

    @Test
    void rejectsTheWireTypesProtobufDoesNotDefine() {
        rejects(concat(tag(1, 6), varint(1)), "wire type 6");
        rejects(concat(tag(1, 7), varint(1)), "wire type 7");
    }

    @Test
    void rejectsGroupWireTypes() {
        // groups are proto2 only and unsupported here, so they must not be mistaken for anything
        rejects(concat(tag(1, 3), varint(1)), "a start-group tag");
        rejects(concat(tag(1, 4)), "an end-group tag");
        rejects(concat(tag(UNKNOWN_FIELD, 3), varint(1)), "a start-group tag on an unknown field");
    }

    // ---------------------------------------------------------------- premature end of input

    @Test
    void rejectsATagWithNoValue() {
        rejects(tag(2, 0), "a varint field with no value");
        rejects(tag(8, 1), "a fixed64 field with no value");
        rejects(tag(1, 2), "a length-delimited field with no length");
        rejects(tag(UNKNOWN_FIELD, 0), "an unknown varint field with no value");
        rejects(tag(UNKNOWN_FIELD, 2), "an unknown length-delimited field with no length");
    }

    @Test
    void rejectsATruncatedValue() {
        rejects(concat(tag(2, 0), of(0x80)), "a truncated varint");
        rejects(concat(tag(8, 1), utf8("abcdefg")), "a fixed64 of seven bytes");
        rejects(concat(tag(1, 2), of(0x80)), "a truncated length prefix");
        rejects(concat(tag(UNKNOWN_FIELD, 0), of(0x80)), "a truncated unknown varint");
        rejects(concat(tag(UNKNOWN_FIELD, 5), utf8("abc")), "an unknown fixed32 of three bytes");
    }

    @Test
    void rejectsDelimitedDataShorterThanItsLength() {
        rejects(concat(tag(1, 2), varint(1)), "a string claiming one byte that is not there");
        rejects(concat(tag(4, 2), varint(1)), "a repeated string claiming a byte that is not there");
        rejects(concat(tag(UNKNOWN_FIELD, 2), varint(1)), "an unknown field claiming a missing byte");
        rejects(concat(tag(1, 2), varint(50), utf8("short")), "a string claiming far more than is there");
    }

    @Test
    void rejectsATruncatedSubmessage() {
        // the submessage ends in the middle of one of its own fields
        byte[] incomplete = concat(tag(1, 0), of(0x80));
        rejects(concat(tag(3, 2), varint(incomplete.length), incomplete), "a submessage cut mid-value");
    }

    @Test
    void rejectsATruncatedPackedRun() {
        rejects(concat(tag(5, 2), varint(1)), "a packed run claiming a byte that is not there");
        rejects(concat(tag(5, 2), varint(1), of(0x80)), "a packed run cut mid-varint");
    }

    @Test
    void rejectsANestedLengthThatOverrunsItsParent() {
        byte[] inner = concat(tag(1, 2), varint(100));
        rejects(concat(tag(3, 2), varint(inner.length), inner, utf8("padding".repeat(30))),
            "a nested value reaching past its parent");
    }

    // ---------------------------------------------------------------- unusual but legal

    @Test
    void acceptsAnOverlongValueVarint() throws Exception {
        // padding is only illegal in tags; a value may use up to ten bytes however it likes
        Person person = read(concat(tag(2, 0), overlongVarint(42, 10)));

        assertEquals(42, person.age());
    }

    @Test
    void acceptsFieldsInAnyOrder() throws Exception {
        Person person = read(concat(
            concat(tag(8, 1), fixed64(Double.doubleToRawLongBits(1.5))),
            concat(tag(2, 0), varint(30)),
            delimited(1, utf8("ada"))));

        assertEquals("ada", person.name());
        assertEquals(30, person.age());
        assertEquals(1.5, person.ratio());
    }

    @Test
    void takesTheLastValueOfARepeatedScalarField() throws Exception {
        Person person = read(concat(
            concat(tag(2, 0), varint(1)),
            concat(tag(2, 0), varint(2)),
            concat(tag(2, 0), varint(3))));

        assertEquals(3, person.age());
    }

    @Test
    void mergesRepeatedOccurrencesOfAMessageField() throws Exception {
        byte[] street = delimited(1, utf8("st"));
        byte[] city = delimited(2, utf8("ci"));
        Person person = read(concat(
            concat(tag(3, 2), varint(street.length), street),
            concat(tag(3, 2), varint(city.length), city)));

        assertEquals(new Address("st", "ci"), person.address());
    }

    @Test
    void concatenatesARepeatedFieldSplitAcrossRuns() throws Exception {
        Person person = read(concat(
            concat(tag(5, 2), varint(2), varint(1), varint(2)),
            concat(tag(5, 0), varint(3)),
            concat(tag(5, 2), varint(1), varint(4))));

        assertEquals(List.of(1, 2, 3, 4), person.scores());
    }

    @Test
    void acceptsAPackedRunForARepeatedScalar() throws Exception {
        Person person = read(concat(tag(5, 2), varint(3), varint(1), varint(2), varint(3)));

        assertEquals(List.of(1, 2, 3), person.scores());
    }

    @Test
    void acceptsUnpackedInputForARepeatedScalar() throws Exception {
        Person person = read(concat(
            concat(tag(5, 0), varint(1)),
            concat(tag(5, 0), varint(2)),
            concat(tag(5, 0), varint(3))));

        assertEquals(List.of(1, 2, 3), person.scores());
    }

    @Test
    void acceptsAnEmptyPackedRun() throws Exception {
        Person person = read(concat(tag(5, 2), varint(0)));

        assertEquals(List.of(), person.scores() == null ? List.of() : person.scores());
    }

    @Test
    void skipsUnknownFieldsOfEveryWireType() throws Exception {
        Person person = read(concat(
            concat(tag(UNKNOWN_FIELD, 0), varint(1)),
            concat(tag(UNKNOWN_FIELD + 1, 1), fixed64(2)),
            delimited(UNKNOWN_FIELD + 2, utf8("ignored")),
            concat(tag(UNKNOWN_FIELD + 3, 5), fixed32(3)),
            delimited(1, utf8("ada"))));

        assertEquals("ada", person.name());
    }

    @Test
    void readsAnyNonZeroVarintAsTrue() throws Exception {
        assertEquals(true, read(concat(tag(7, 0), varint(1))).active());
        assertEquals(true, read(concat(tag(7, 0), varint(2))).active());
        assertEquals(true, read(concat(tag(7, 0), varint(0xFFFFFFFFL))).active());
        assertEquals(false, read(concat(tag(7, 0), varint(0))).active());
    }

    @Test
    void narrowsAnOversizedVarintToTheDeclaredWidth() throws Exception {
        // a negative int32 is sign-extended to ten bytes on the wire and must come back narrowed
        Person person = read(concat(tag(2, 0), varint(-1L)));

        assertEquals(-1, person.age());
    }

    @Test
    void decodesZigZagValuesAtTheirExtremes() throws Exception {
        assertEquals(Integer.MIN_VALUE, read(concat(tag(6, 0), varint(0xFFFFFFFFL))).balance());
        assertEquals(Integer.MAX_VALUE, read(concat(tag(6, 0), varint(0xFFFFFFFEL))).balance());
    }

    private static int tagValue(int fieldNumber, int wireType) {
        return fieldNumber << 3 | wireType;
    }
}
