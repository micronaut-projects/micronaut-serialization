/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public class ByteBufferSlicedTest {

    /**
     * Test ported from com.fasterxml.jackson.databind.ser.jdk.JDKTypeSerializationTest
     * @param jsonMapper JSONMapper either Jackson or Serde implementation
     * @throws IOException If an unrecoverable error occurs
     */
    @Test
    public void testSlicedByteBuffer(JsonMapper jsonMapper) throws IOException {
        final byte[] INPUT_BYTES = new byte[] { 1, 2, 3, 4, 5 };
        ByteBuffer bbuf = ByteBuffer.wrap(INPUT_BYTES);

        bbuf.position(2);
        ByteBuffer slicedBuf = bbuf.slice();

        assertEquals(jsonMapper.writeValueAsString(new byte[] { 3, 4, 5 }),
            jsonMapper.writeValueAsString(slicedBuf));

        // but how about offset within?
        slicedBuf.position(1);
        assertEquals(jsonMapper.writeValueAsString(new byte[] { 4, 5 }),
            jsonMapper.writeValueAsString(slicedBuf));
    }
}
