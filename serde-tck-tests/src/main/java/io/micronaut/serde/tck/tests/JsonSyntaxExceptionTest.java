package io.micronaut.serde.tck.tests;

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonSyntaxException;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@MicronautTest(startApplication = false)
public class JsonSyntaxExceptionTest {
    @Test
    public void testSyntaxException(JsonMapper jsonMapper) throws IOException {
        String string = "{foo}";
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        Assertions.assertThrows(JsonSyntaxException.class, () -> jsonMapper.readValue(string, MyType.class));
        Assertions.assertThrows(JsonSyntaxException.class, () -> jsonMapper.readValue(string, MyType.class));
        Assertions.assertThrows(JsonSyntaxException.class, () -> jsonMapper.readValue(string, Argument.of(MyType.class)));
        Assertions.assertThrows(JsonSyntaxException.class, () -> jsonMapper.readValue(bytes, Argument.of(MyType.class)));
        Assertions.assertThrows(JsonSyntaxException.class, () -> jsonMapper.readValue(new ByteArrayInputStream(bytes), Argument.of(MyType.class)));
        Assertions.assertThrows(JsonSyntaxException.class, () -> jsonMapper.readValue(new ByteArrayInputStream(bytes), MyType.class));
    }

    @Serdeable
    record MyType(String foo) {}
}
