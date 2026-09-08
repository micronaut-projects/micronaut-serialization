package example;

import io.micronaut.context.ApplicationContext;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.properties.PropertiesMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class PropertiesQuickStartTest {

    @Test
    void testWriteReadBook(@Named(PropertiesMapper.NAME) ObjectMapper propertiesMapper) throws IOException {
        PropertiesLibrary library = library();

        String result = propertiesMapper.writeValueAsString(library);
        List<String> lines = result.lines().toList();

        assertTrue(lines.contains("book.title=The Stand"));
        assertTrue(lines.contains("book.authors[0].name=Stephen King"));
        assertTrue(lines.contains("book.authors[1].name=JRR Tolkien"));
        assertEquals(library, propertiesMapper.readValue(result, PropertiesLibrary.class));
    }

    @Test
    void testDottedArrayIndexes() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.serde.format.properties.array-index-style", "DOTTED"
        ))) {
            ObjectMapper propertiesMapper = context.getBean(PropertiesMapper.class);

            String result = propertiesMapper.writeValueAsString(library());
            List<String> lines = result.lines().toList();

            assertTrue(lines.contains("book.authors.1.name=Stephen King"));
            assertTrue(lines.contains("book.authors.2.name=JRR Tolkien"));
        }
    }

    private static PropertiesLibrary library() {
        return new PropertiesLibrary(new PropertiesLibrary.PropertiesBook(
            "The Stand",
            List.of(
                new PropertiesLibrary.Author("Stephen King", 60),
                new PropertiesLibrary.Author("JRR Tolkien", 81)
            )
        ));
    }
}
