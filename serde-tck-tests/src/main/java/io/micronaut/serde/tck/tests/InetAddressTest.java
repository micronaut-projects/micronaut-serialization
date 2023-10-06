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
package io.micronaut.serde.tck.tests;

import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
public class InetAddressTest {
    @Test
    public void inetAddressSerialization(JsonMapper jsonMapper) throws IOException {
        assertEquals(q("127.0.0.1"), jsonMapper.writeValueAsString(InetAddress.getByName("127.0.0.1")));
        InetAddress input = InetAddress.getByName("google.com");
        assertEquals(q("google.com"), jsonMapper.writeValueAsString(input));

        InetAddress address = jsonMapper.readValue(q("127.0.0.1"), InetAddress.class);
        assertEquals("127.0.0.1", address.getHostAddress());

        final String HOST = "google.com";
        address = jsonMapper.readValue(q(HOST), InetAddress.class);
        assertEquals(HOST, address.getHostName());
    }

    public static String q(String str) {
        return '"'+str+'"';
    }
}
