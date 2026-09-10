package io.micronaut.serde.protobuf;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the wire shapes beyond the simple scalar case: repeated messages, byte strings, the
 * fixed-width and zig-zag representations, and the two encodings a repeated scalar can arrive in.
 */
class ProtobufWireShapesTest {

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

    @Test
    void repeatedMessages() throws Exception {
        Team team = new Team("Analytical Engine", List.of(
            new Address("12 Marylebone", "London"),
            new Address("1 Ada Way", "Cambridge")
        ));

        Descriptors.Descriptor descriptor = ProtoReference.TEAM;
        Descriptors.Descriptor address = ProtoReference.ADDRESS;
        DynamicMessage expected = DynamicMessage.newBuilder(descriptor)
            .setField(descriptor.findFieldByNumber(1), "Analytical Engine")
            .setField(descriptor.findFieldByNumber(2), List.of(
                DynamicMessage.newBuilder(address)
                    .setField(address.findFieldByNumber(1), "12 Marylebone")
                    .setField(address.findFieldByNumber(2), "London").build(),
                DynamicMessage.newBuilder(address)
                    .setField(address.findFieldByNumber(1), "1 Ada Way")
                    .setField(address.findFieldByNumber(2), "Cambridge").build()
            ))
            .build();

        byte[] written = mapper.writeValueAsBytes(Argument.of(Team.class), team);
        assertArrayEquals(expected.toByteArray(), written);
        assertEquals(team, mapper.readValue(expected.toByteArray(), Argument.of(Team.class)));
    }

    @Test
    void fixedWidthAndZigZagRepresentations() throws Exception {
        Numbers numbers = new Numbers(70000, -9000000000L, -1, -1234567L, 0.5f);

        Descriptors.Descriptor descriptor = ProtoReference.NUMBERS;
        DynamicMessage expected = DynamicMessage.newBuilder(descriptor)
            .setField(descriptor.findFieldByNumber(1), 70000)
            .setField(descriptor.findFieldByNumber(2), -9000000000L)
            .setField(descriptor.findFieldByNumber(3), -1)
            .setField(descriptor.findFieldByNumber(4), -1234567L)
            .setField(descriptor.findFieldByNumber(5), 0.5f)
            .build();

        byte[] written = mapper.writeValueAsBytes(Argument.of(Numbers.class), numbers);
        assertArrayEquals(expected.toByteArray(), written);
        assertEquals(numbers, mapper.readValue(expected.toByteArray(), Argument.of(Numbers.class)));
    }

    @Test
    void readsUnpackedRepeatedScalars() throws Exception {
        Descriptors.Descriptor descriptor = ProtoReference.PERSON_UNPACKED;
        DynamicMessage unpacked = DynamicMessage.newBuilder(descriptor)
            .setField(descriptor.findFieldByNumber(1), "Ada Lovelace")
            .setField(descriptor.findFieldByNumber(2), 36)
            .setField(descriptor.findFieldByNumber(4), List.of("Ada", "AAL"))
            .setField(descriptor.findFieldByNumber(5), List.of(1, 300, 70000))
            .setField(descriptor.findFieldByNumber(6), -1234)
            .setField(descriptor.findFieldByNumber(7), true)
            .setField(descriptor.findFieldByNumber(8), 0.5d)
            .build();

        Person read = mapper.readValue(unpacked.toByteArray(), Argument.of(Person.class));
        assertEquals(List.of(1, 300, 70000), read.scores());
        assertEquals(List.of("Ada", "AAL"), read.nicknames());
    }

    @Test
    void skipsFieldsTheReaderDoesNotKnow() throws Exception {
        Descriptors.Descriptor descriptor = ProtoReference.PERSON;
        Descriptors.Descriptor address = ProtoReference.ADDRESS;
        DynamicMessage full = DynamicMessage.newBuilder(descriptor)
            .setField(descriptor.findFieldByNumber(1), "Ada Lovelace")
            .setField(descriptor.findFieldByNumber(2), 36)
            .setField(descriptor.findFieldByNumber(3), DynamicMessage.newBuilder(address)
                .setField(address.findFieldByNumber(1), "12 Marylebone")
                .setField(address.findFieldByNumber(2), "London").build())
            .setField(descriptor.findFieldByNumber(4), List.of("Ada"))
            .setField(descriptor.findFieldByNumber(5), List.of(1, 300))
            .setField(descriptor.findFieldByNumber(6), -1234)
            .setField(descriptor.findFieldByNumber(7), true)
            .setField(descriptor.findFieldByNumber(8), 0.5d)
            .build();

        // fields 3 to 8 are unknown to this reader and must be skipped, not rejected
        assertEquals(new NameAndAge("Ada Lovelace", 36),
            mapper.readValue(full.toByteArray(), Argument.of(NameAndAge.class)));
    }

    @Test
    void concatenatesInterleavedPackedAndUnpackedRepeatedRuns() throws Exception {
        byte[] payload = {
            40, 1,             // scores = 1, unpacked
            10, 1, 'a',       // name = "a", interleaved
            42, 2, 2, 3,      // scores = [2, 3], packed
            40, 4              // scores = 4, unpacked again
        };

        Person read = mapper.readValue(payload, Argument.of(Person.class));
        assertEquals("a", read.name());
        assertEquals(List.of(1, 2, 3, 4), read.scores());
    }

    @Test
    void usesTheLastCompatibleOccurrenceOfASingularScalar() throws Exception {
        byte[] payload = {16, 1, 16, 2};

        NameAndAge read = mapper.readValue(payload, Argument.of(NameAndAge.class));
        assertEquals(2, read.age());
    }

    @Test
    void mergesDuplicateEmbeddedMessages() throws Exception {
        byte[] payload = {
            10, 3, 10, 1, 'a',
            10, 3, 18, 1, 'b'
        };

        AddressHolder read = mapper.readValue(payload, Argument.of(AddressHolder.class));
        assertEquals(new Address("a", "b"), read.address());
    }

    @Test
    void treatsAnIncompatibleWireTypeAsUnknown() throws Exception {
        byte[] payload = {10, 1, 'a', 21, 7, 0, 0, 0};

        NameAndAge read = mapper.readValue(payload, Argument.of(NameAndAge.class));
        assertEquals(new NameAndAge("a", 0), read);
    }

    @Test
    void rejectsAGroupRatherThanSkippingIt() {
        byte[] payload = {
            27,             // unknown field 3, start group
            8, 1,           // nested field
            28,             // field 3, end group
            10, 1, 'a'
        };

        // groups are a proto2-only encoding this backend does not support. Skipping them meant
        // recursing per nested start-group tag, and a start-group tag is one byte, so a small
        // payload could exhaust the stack; see UntrustedPayloadTest.
        Exception e = assertThrows(Exception.class,
            () -> mapper.readValue(payload, Argument.of(NameAndAge.class)));
        assertTrue(String.valueOf(e.getMessage()).contains("group encoding is not supported")
            || String.valueOf(e.getCause()).contains("group encoding is not supported"),
            () -> String.valueOf(e));
    }
}

@Serdeable
record AddressHolder(@ProtoField(1) Address address) {
}
