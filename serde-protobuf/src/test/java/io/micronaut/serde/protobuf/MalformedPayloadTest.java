package io.micronaut.serde.protobuf;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A length prefix is attacker-controlled, so it has to be checked against the structure it sits in
 * rather than against the payload as a whole. Otherwise a nested message can claim bytes that
 * belong to its parent and the reader silently returns the wrong thing.
 */
class MalformedPayloadTest {

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
    void rejectsANestedValueThatReachesPastItsParent() {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(0x1A);          // Person field 3 (address), length-delimited
        payload.write(0x02);          // ... claiming just two bytes
        payload.write(0x0A);          // Address field 1 (street), length-delimited
        payload.write(100);           // ... claiming a hundred, which is past the address's two
        for (int i = 0; i < 200; i++) {
            // plenty of trailing bytes, so a payload-wide bounds check would let this through
            payload.write('x');
        }

        Exception e = assertThrows(Exception.class,
            () -> mapper.readValue(payload.toByteArray(), Argument.of(Person.class)));
        assertTrue(message(e).contains("reaches past the end of its enclosing structure"), message(e));
    }

    @Test
    void rejectsATruncatedPayload() {
        // field 1 (name) says twenty bytes follow, and none do
        byte[] payload = {0x0A, 20};
        Exception e = assertThrows(Exception.class,
            () -> mapper.readValue(payload, Argument.of(Person.class)));
        assertTrue(message(e).contains("reaches past the end"), message(e));
    }

    @Test
    void rejectsANegativeLength() {
        // a length varint whose value is negative when read as a 32-bit int
        byte[] payload = {0x0A, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x0F};
        Exception e = assertThrows(Exception.class,
            () -> mapper.readValue(payload, Argument.of(Person.class)));
        assertTrue(message(e).contains("negative length"), message(e));
    }

    private static String message(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage()) + " | " + String.valueOf(e.getMessage());
    }
}
