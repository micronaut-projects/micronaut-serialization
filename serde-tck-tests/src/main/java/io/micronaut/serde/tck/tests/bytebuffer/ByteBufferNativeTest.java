package io.micronaut.serde.tck.tests.bytebuffer;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.util.StringUtils;
import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "micronaut.serde.write-binary-as-array", value = StringUtils.FALSE)
@MicronautTest(startApplication = false)
public class ByteBufferNativeTest {
    /**
     * Test ported from com.fasterxml.jackson.databind.ser.jdk.JDKTypeSerializationTest
     * @param jsonMapper JSONMapper either Jackson or Serde implementation
     * @throws IOException If an unrecoverable error occurs
     */
    @Test
    public void testByteBufferNative(JsonMapper jsonMapper) throws IOException {
        final byte[] INPUT_BYTES = new byte[]{1, 2, 3, 4, 5};
        String exp = jsonMapper.writeValueAsString(INPUT_BYTES);
        // so far so good, but must ensure Native buffers also work:
        ByteBuffer bbuf2 = ByteBuffer.allocateDirect(5);
        bbuf2.put(INPUT_BYTES);
        bbuf2.flip();
        assertEquals(exp, jsonMapper.writeValueAsString(bbuf2));
    }
}
