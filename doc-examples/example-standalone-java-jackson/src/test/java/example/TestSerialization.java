package example;

import io.micronaut.serde.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.Map;

public class TestSerialization {
    public static ObjectMapper getStandardMapper() {
        return ObjectMapper.create(Collections.emptyMap());
    }

    public static ObjectMapper getPrettyMapper() {
        return ObjectMapper.create(Map.of("micronaut.serde.jackson.pretty-print", "true"));
    }

    @Test
    public void testSerializeDeserialize() throws Exception {
        final Book book = new Book("example-book", 2);
        final ObjectMapper mapper = getStandardMapper();
        String result = mapper.writeValueAsString(book);
        System.out.println(result);

        String prettyValue = getPrettyMapper().writeValueAsString(book);
        System.out.println(prettyValue);

        final Book deserialized = mapper.readValue(result, Book.class);
        assertEquals(deserialized.getTitle(), book.getTitle());
        assertEquals(deserialized.getQuantity(), book.getQuantity());
    }
}