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

        //then:
        assertEquals("{\"jdk\":\"JDK_17\"}", json);

        //when:
        Foo foo = jsonMapper.readValue("{\"jdk\":\"JDK_17\"}", Foo.class);

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
