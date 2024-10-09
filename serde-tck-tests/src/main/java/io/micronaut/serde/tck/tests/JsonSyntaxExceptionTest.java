/*
 * Copyright 2017-2024 original authors
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
