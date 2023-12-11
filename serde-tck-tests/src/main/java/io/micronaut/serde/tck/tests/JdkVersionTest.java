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
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(startApplication = false)
public class JdkVersionTest {
    @Test
    public void testJdkSerialization(JsonMapper jsonMapper) throws IOException {
        //given:
        JdkVersion jdk17 = JdkVersion.JDK_17;

        //when:
        String json = jsonMapper.writeValueAsString(new Foo(jdk17));
        String expected = """
                {"jdk":"JDK_17"}""";

        //then:
        assertEquals(expected, json);

        //when:
        Foo foo = jsonMapper.readValue(expected, Foo.class);

        //then:
        assertEquals(jdk17, foo.jdk);
    }

    @Serdeable
    static class Foo {
        private final JdkVersion jdk;

        Foo(JdkVersion jdk) {
            this.jdk = jdk;
        }

        public JdkVersion getJdk() {
            return jdk;
        }
    }
}
