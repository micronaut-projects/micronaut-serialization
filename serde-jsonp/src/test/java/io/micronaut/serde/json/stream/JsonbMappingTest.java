/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/JUnit5TestClass.java to edit this template
 */

package io.micronaut.serde.json.stream;

import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
 
/**
 *
 * @author gkrocher
 */
@MicronautTest 
public class JsonbMappingTest {
    @Test
    void testWriteJsonbType(JsonMapper jsonMapper) throws IOException {
        byte[] bytes = jsonMapper.writeValueAsBytes(
                new JsonbTest("test")
        );
        assertEquals("{\"n\":\"test\",\"price\":\"$1.1\",\"dateCreated\":\"011021\"}", new String(bytes, StandardCharsets.UTF_8));
    }

}