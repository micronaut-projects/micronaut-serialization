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
public class ByteBufferTest {

    /**
     * Test ported from com.fasterxml.jackson.databind.ser.jdk.JDKTypeSerializationTest
     * @param jsonMapper JSONMapper either Jackson or Serde implementation
     * @throws IOException If an unrecoverable error occurs
     */
    @Test
    public void testByteBuffer(JsonMapper jsonMapper) throws IOException {
        final byte[] INPUT_BYTES = new byte[]{1, 2, 3, 4, 5};
        String exp = jsonMapper.writeValueAsString(INPUT_BYTES);
        ByteBuffer bbuf = ByteBuffer.wrap(INPUT_BYTES);
        assertEquals(exp, jsonMapper.writeValueAsString(bbuf));
    }
}
