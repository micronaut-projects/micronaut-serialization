package io.micronaut.serde.protobuf;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;
import org.junit.jupiter.api.Test;

import java.util.List;
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

    @Test
    void repeatedBytesUseOneLengthDelimitedValuePerElementUnderTheLegacyArrayShape() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            ProtobufMapper mapper = context.getBean(ProtobufMapper.class);
            var value = new RepeatedBlobs(List.of(new byte[]{1, 2}, new byte[0], new byte[]{3}));
            byte[] expected = {10, 2, 1, 2, 10, 0, 10, 1, 3};

            assertArrayEquals(expected, mapper.writeValueAsBytes(value));
            RepeatedBlobs decoded = mapper.readValue(expected, Argument.of(RepeatedBlobs.class));
            assertArrayEquals(new byte[]{1, 2}, decoded.values().get(0));
            assertArrayEquals(new byte[0], decoded.values().get(1));
            assertArrayEquals(new byte[]{3}, decoded.values().get(2));
        }
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

@Serdeable
record RepeatedBlobs(@ProtoField(1) List<byte[]> values) {
}
