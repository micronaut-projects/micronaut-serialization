package example;

import java.io.IOException;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
public class ProductTest {

    @Test
    void testSerDeser(ObjectMapper objectMapper) throws IOException {
        final String result = objectMapper.writeValueAsString(new Product("Apple", 10));
        assertEquals(
                "{\"p_name\":\"Apple\",\"p_quantity\":10}",
                result
        );
        final Product product = objectMapper.readValue(result, Product.class);
        assertEquals(
                "Apple",
                product.getName()
        );
    }
}
