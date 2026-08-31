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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Checks the encoder and decoder against protobuf-java rather than against themselves: bytes
 * written here must parse in the reference implementation, and bytes written there must parse here.
 */
class ProtobufWireCompatibilityTest {

    private static ApplicationContext context;
    private static ProtobufMapper mapper;

    private static final Person PERSON = new Person(
        "Ada Lovelace",
        36,
        new Address("12 Marylebone", "London"),
        List.of("Ada", "AAL"),
        List.of(1, 300, 70000),
        -1234,
        true,
        0.5d
    );

    @BeforeAll
    static void setup() {
        context = ApplicationContext.run();
        mapper = context.getBean(ProtobufMapper.class);
    }

    @AfterAll
    static void cleanup() {
        context.close();
    }

    private static DynamicMessage reference() {
        Descriptors.Descriptor person = ProtoReference.PERSON;
        Descriptors.Descriptor address = ProtoReference.ADDRESS;
        DynamicMessage nested = DynamicMessage.newBuilder(address)
            .setField(address.findFieldByNumber(1), "12 Marylebone")
            .setField(address.findFieldByNumber(2), "London")
            .build();
        return DynamicMessage.newBuilder(person)
            .setField(person.findFieldByNumber(1), "Ada Lovelace")
            .setField(person.findFieldByNumber(2), 36)
            .setField(person.findFieldByNumber(3), nested)
            .setField(person.findFieldByNumber(4), List.of("Ada", "AAL"))
            .setField(person.findFieldByNumber(5), List.of(1, 300, 70000))
            .setField(person.findFieldByNumber(6), -1234)
            .setField(person.findFieldByNumber(7), true)
            .setField(person.findFieldByNumber(8), 0.5d)
            .build();
    }

    @Test
    void writesBytesTheReferenceImplementationAccepts() throws Exception {
        byte[] written = mapper.writeValueAsBytes(Argument.of(Person.class), PERSON);
        DynamicMessage parsed = DynamicMessage.parseFrom(ProtoReference.PERSON, written);
        Descriptors.Descriptor person = ProtoReference.PERSON;

        assertEquals("Ada Lovelace", parsed.getField(person.findFieldByNumber(1)));
        assertEquals(36, parsed.getField(person.findFieldByNumber(2)));
        assertEquals(List.of("Ada", "AAL"), parsed.getField(person.findFieldByNumber(4)));
        assertEquals(List.of(1, 300, 70000), parsed.getField(person.findFieldByNumber(5)));
        assertEquals(-1234, parsed.getField(person.findFieldByNumber(6)));
        assertEquals(true, parsed.getField(person.findFieldByNumber(7)));
        assertEquals(0.5d, parsed.getField(person.findFieldByNumber(8)));

        DynamicMessage nested = (DynamicMessage) parsed.getField(person.findFieldByNumber(3));
        assertEquals("12 Marylebone", nested.getField(ProtoReference.ADDRESS.findFieldByNumber(1)));
        assertEquals("London", nested.getField(ProtoReference.ADDRESS.findFieldByNumber(2)));
    }

    @Test
    void producesByteIdenticalOutput() throws Exception {
        assertArrayEquals(reference().toByteArray(), mapper.writeValueAsBytes(Argument.of(Person.class), PERSON));
    }

    @Test
    void readsBytesTheReferenceImplementationProduced() throws Exception {
        Person read = mapper.readValue(reference().toByteArray(), Argument.of(Person.class));
        assertEquals(PERSON, read);
    }

    @Test
    void omitsNullFieldsEntirely() throws Exception {
        Person withoutAddress = new Person("Nobody", 1, null, List.of(), List.of(), 0, false, 0d);
        byte[] written = mapper.writeValueAsBytes(Argument.of(Person.class), withoutAddress);
        DynamicMessage parsed = DynamicMessage.parseFrom(ProtoReference.PERSON, written);

        // a null nested message and empty repeated fields leave nothing on the wire at all
        assertEquals(false, parsed.hasField(ProtoReference.PERSON.findFieldByNumber(3)));
        assertEquals(0, ((List<?>) parsed.getField(ProtoReference.PERSON.findFieldByNumber(4))).size());
        assertEquals(0, ((List<?>) parsed.getField(ProtoReference.PERSON.findFieldByNumber(5))).size());

        Person read = mapper.readValue(written, Argument.of(Person.class));
        assertNull(read.address());
    }
}
