/*
 * Copyright 2017-2026 original authors
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
package example;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.xml.XmlObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Woodstox XML example.
 *
 * @since 3.2
 */
@MicronautTest
public class BookTest {

    private final ObjectMapper xmlMapper;

    @Inject
    BookTest(@Named(XmlObjectMapper.XML_MAPPER_NAME) ObjectMapper xmlMapper) {
        this.xmlMapper = xmlMapper;
    }

    @Test
    void testWriteReadBookWithWoodstox() throws IOException {
        assertTrue(XMLInputFactory.newFactory().getClass().getName().startsWith("com.ctc.wstx."));
        assertTrue(XMLOutputFactory.newFactory().getClass().getName().startsWith("com.ctc.wstx."));

        String result = xmlMapper.writeValueAsString(new Book(
            "978-0307743688",
            "The Stand",
            List.of("Stephen King")
        ));

        assertEquals(
            "<book isbn=\"978-0307743688\"><title>The Stand</title><authors><author>Stephen King</author></authors></book>",
            result
        );

        Book book = xmlMapper.readValue(result, Book.class);
        assertNotNull(book);
        assertEquals("978-0307743688", book.getIsbn());
        assertEquals("The Stand", book.getTitle());
        assertEquals(List.of("Stephen King"), book.getAuthors());
    }

    @Test
    void testWriteReadJaxbBook() throws IOException {
        JaxbBook input = new JaxbBook();
        input.isbn = "978-0307743688";
        input.title = "The Stand";
        input.authors = List.of("Stephen King");

        String result = xmlMapper.writeValueAsString(input);

        assertEquals(
            "<book isbn=\"978-0307743688\"><title>The Stand</title><subtitle xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"></subtitle><author>Stephen King</author></book>",
            result
        );

        JaxbBook book = xmlMapper.readValue(result, JaxbBook.class);
        assertEquals(input.isbn, book.isbn);
        assertEquals(input.title, book.title);
        assertNull(book.subtitle);
        assertEquals(input.authors, book.authors);
    }
}
