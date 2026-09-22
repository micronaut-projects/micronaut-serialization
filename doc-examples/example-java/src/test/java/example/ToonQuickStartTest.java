package example;

import io.micronaut.context.ApplicationContext;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.toon.ToonMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToonQuickStartTest {

    @Test
    void testWriteAndReadToon() throws IOException {
        try (ApplicationContext context = ApplicationContext.run()) {
            ObjectMapper toonMapper = context.getBean(ToonMapper.class);
            ToonLibrary library = new ToonLibrary("City Library", List.of(
                new ToonBook("The Stand", List.of("Stephen King")),
                new ToonBook("VALIS", List.of("Philip K. Dick"))
            ));

            String toon = new String(toonMapper.writeValueAsBytes(library));

            assertEquals("""
                name: City Library
                books[2]:
                  - title: The Stand
                    authors[1]: Stephen King
                  - title: VALIS
                    authors[1]: Philip K. Dick""", toon);
            assertEquals(library, toonMapper.readValue(toon, ToonLibrary.class));
        }
    }
}
