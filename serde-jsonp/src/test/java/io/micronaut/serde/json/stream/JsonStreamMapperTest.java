package io.micronaut.serde.json.stream;

import java.io.IOException;

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class JsonStreamMapperTest {
    @Test
    void testSimpleMapping(JsonMapper mapper) throws IOException {
        Simple s = new Simple();
        s.setValue("test");
        final byte[] result = mapper.writeValueAsBytes(s);
        final String str = new String(result);

        assertEquals(
                "{\"value\":\"test\"}",
                str
        );
        final Simple simple = mapper.readValue(str, Argument.of(Simple.class));
        assertNotNull(simple);
        assertEquals(
                "test",
                simple.getValue()
        );
    }


    @Serdeable
    static final class Simple {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
