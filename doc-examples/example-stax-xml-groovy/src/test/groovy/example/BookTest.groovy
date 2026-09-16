package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.xml.XmlObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Specification

@MicronautTest
class BookTest extends Specification {

    @Inject
    @Named(XmlObjectMapper.XML_MAPPER_NAME)
    ObjectMapper xmlMapper

    void "test read/write book"() {
        when:
        String result = xmlMapper.writeValueAsString(new Book(
            "978-0307743688",
            "The Stand",
            List.of("Stephen King")
        ))

        then:
        result == '<book isbn="978-0307743688"><title>The Stand</title><authors><author>Stephen King</author></authors></book>'

        when:
        Book book = xmlMapper.readValue(result, Book)

        then:
        book != null
        book.isbn == "978-0307743688"
        book.title == "The Stand"
        book.authors == List.of("Stephen King")
    }

    void "test read/write jaxb book"() {
        given:
        JaxbBook input = new JaxbBook()
        input.isbn = "978-0307743688"
        input.title = "The Stand"
        input.authors = List.of("Stephen King")

        when:
        String result = xmlMapper.writeValueAsString(input)

        then:
        result == '<book isbn="978-0307743688"><title>The Stand</title><subtitle xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:nil="true"></subtitle><author>Stephen King</author></book>'

        when:
        JaxbBook book = xmlMapper.readValue(result, JaxbBook)

        then:
        book.isbn == input.isbn
        book.title == input.title
        book.subtitle == "Untitled"
        book.authors == input.authors
    }
}
