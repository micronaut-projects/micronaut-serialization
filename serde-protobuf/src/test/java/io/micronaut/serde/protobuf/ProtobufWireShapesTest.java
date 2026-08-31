package io.micronaut.serde.protobuf;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
