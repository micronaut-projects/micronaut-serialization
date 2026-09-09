package io.micronaut.serde.protobuf;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boundaries of the prototype, asserted rather than merely documented, so that they fail loudly
 * if anyone widens them by accident.
 */
class ProtobufLimitationsTest {

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
    void cannotReadIntoATree() {
        Exception e = assertThrows(Exception.class,
            () -> mapper.readValue(new byte[]{8, 1}, Argument.of(JsonNode.class)));
        assertTrue(rootMessage(e).contains("cannot be read without a schema"), rootMessage(e));
    }

    @Test
    void cannotReadIntoAnUntypedProperty() {
        Exception e = assertThrows(Exception.class,
            () -> mapper.readValue(new byte[]{10, 1, 97}, Argument.of(InvalidModels.UntypedProperty.class)));
        assertTrue(rootMessage(e).contains("cannot be read without a schema")
            || rootMessage(e).contains("No introspection found"), rootMessage(e));
    }

    @Test
    void cannotEncodeACollectionAtTheTopLevel() {
        Exception e = assertThrows(Exception.class,
            () -> mapper.writeValueAsBytes(Argument.listOf(String.class), java.util.List.of("a")));
        assertTrue(rootMessage(e).contains("must be a message"), rootMessage(e));
    }

    @Test
    void cannotStreamReactively() {
        assertThrows(UnsupportedOperationException.class,
            () -> mapper.createReactiveParser(p -> {
            }, false));
    }

    /**
     * proto3 omits fields that hold their type's default value, so a reader cannot tell "absent"
     * from "empty string". A property that serde treats as required therefore fails on a payload
     * the reference implementation considers perfectly valid.
     */
    @Test
    void defaultValuedFieldsAreIndistinguishableFromAbsentOnes() throws Exception {
        Descriptors.Descriptor descriptor = ProtoReference.PERSON;
        byte[] emptyName = DynamicMessage.newBuilder(descriptor)
            .setField(descriptor.findFieldByNumber(1), "")
            .setField(descriptor.findFieldByNumber(2), 7)
            .build()
            .toByteArray();

        NameAndAge read = mapper.readValue(emptyName, Argument.of(NameAndAge.class));
        assertEquals(7, read.age());
        // the empty string never reached the wire, so it comes back as null rather than ""
        assertEquals(null, read.name());
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage()) + " | " + String.valueOf(e.getMessage());
    }
}
