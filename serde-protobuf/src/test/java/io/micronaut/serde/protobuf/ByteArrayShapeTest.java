package io.micronaut.serde.protobuf;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * {@code micronaut.serde.write-binary-as-array} decides whether a {@code byte[]} reaches the
 * encoder through {@code encodeBinary} or one byte at a time through an array. Protobuf has a
 * native {@code bytes} type, so both routes have to land on the same field.
 */
class ByteArrayShapeTest {

    private static final byte[] PAYLOAD = {0, 1, 2, -1, 127, -128};

    @Test
    void producesABytesFieldWhenBinaryIsWrittenDirectly() throws Exception {
        assertBytesField(Map.of("micronaut.serde.write-binary-as-array", false));
    }

    @Test
    void producesABytesFieldUnderTheLegacyArrayShape() throws Exception {
        // the default: byte[] arrives as an array of numbers rather than through encodeBinary
        assertBytesField(Map.of("micronaut.serde.write-binary-as-array", true));
    }

    private void assertBytesField(Map<String, Object> properties) throws Exception {
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            ProtobufMapper mapper = context.getBean(ProtobufMapper.class);
            Descriptors.Descriptor descriptor = ProtoReference.BLOB;
            byte[] expected = DynamicMessage.newBuilder(descriptor)
                .setField(descriptor.findFieldByNumber(1), ByteString.copyFrom(PAYLOAD))
                .build()
                .toByteArray();

            byte[] written = mapper.writeValueAsBytes(Argument.of(Blob.class), new Blob(PAYLOAD));
            assertArrayEquals(expected, written);
            assertArrayEquals(PAYLOAD, mapper.readValue(expected, Argument.of(Blob.class)).data());
        }
    }
}
