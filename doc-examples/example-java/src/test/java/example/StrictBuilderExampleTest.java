package example;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
public class StrictBuilderExampleTest {

    @Test
    void testDefaultValueIsAppliedForAMissingProperty(ObjectMapper objectMapper) throws IOException {
        ReleaseRequest request = objectMapper.readValue(
            "{\"service\":\"checkout\"}",
            Argument.of(ReleaseRequest.class)
        );

        assertEquals("checkout", request.getService());
        assertEquals("platform", request.getOwner());
        assertNull(request.getNotes());
    }

    @Test
    void testMissingRequiredPropertyIsRejected(ObjectMapper objectMapper) {
        SerdeException e = assertThrows(SerdeException.class, () -> objectMapper.readValue(
            "{\"owner\":\"growth\"}",
            Argument.of(ReleaseRequest.class)
        ));

        assertTrue(e.getMessage().contains("Required property"));
    }
}
