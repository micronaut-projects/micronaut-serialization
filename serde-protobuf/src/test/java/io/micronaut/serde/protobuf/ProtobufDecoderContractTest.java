package io.micronaut.serde.protobuf;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtobufDecoderContractTest {

    private static final byte[] PAYLOAD = {
        10, 1, 'a',
        10, 1, 'b',
        18, 1, 'n'
    };

    @Test
    void decodeBufferAdvancesPastTheWholeRepeatedValue() throws Exception {
        Decoder message = message();
        assertEquals("values", message.decodeKey());

        Decoder buffered = message.decodeBuffer();

        assertEquals("name", message.decodeKey());
        Decoder values = buffered.decodeArray(Argument.listOf(String.class));
        assertTrue(values.hasNextArrayValue());
        assertEquals("a", values.decodeString());
        assertTrue(values.hasNextArrayValue());
        assertEquals("b", values.decodeString());
    }

    @Test
    void finishStructureHonorsTheConsumeFlagForMessages() throws Exception {
        Decoder message = message();

        assertThrows(IllegalStateException.class, () -> message.finishStructure(false));
        message.finishStructure(true);
    }

    @Test
    void finishStructureHonorsTheConsumeFlagForRepeatedValues() throws Exception {
        Decoder message = message();
        assertEquals("values", message.decodeKey());
        Decoder values = message.decodeArray(Argument.listOf(String.class));
        assertTrue(values.hasNextArrayValue());

        assertThrows(IllegalStateException.class, () -> values.finishStructure(false));
        values.finishStructure(true);

        assertEquals("name", message.decodeKey());
    }

    @Test
    void skipValueCanAdvanceTheFirstRepeatedElementBeforeIterationStarts() throws Exception {
        Decoder message = message();
        assertEquals("values", message.decodeKey());
        Decoder values = message.decodeArray(Argument.listOf(String.class));

        values.skipValue();

        assertTrue(values.hasNextArrayValue());
        assertEquals("b", values.decodeString());
    }

    private static Decoder message() throws Exception {
        return new ProtobufDecoder(PAYLOAD, LimitingStream.DEFAULT_LIMITS)
            .decodeObject(Argument.of(ContractMessage.class));
    }

    @Serdeable
    record ContractMessage(
        @ProtoField(1) List<String> values,
        @ProtoField(2) String name
    ) {
    }
}
