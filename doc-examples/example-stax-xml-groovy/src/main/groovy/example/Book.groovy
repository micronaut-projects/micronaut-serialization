package example

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonRootName
import io.micronaut.serde.annotation.Serdeable
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty

@Serdeable // <1>
@JsonRootName("book") // <2>
class Book {
    @JacksonXmlProperty(isAttribute = true, localName = "isbn") // <3>
    final String isbn
    @JacksonXmlProperty(localName = "title") // <4>
    final String title
    @JacksonXmlElementWrapper(localName = "authors") // <5>
    @JacksonXmlProperty(localName = "author") // <6>
    final List<String> authors

    @JsonCreator
    Book(@JsonProperty("isbn") String isbn,
         @JsonProperty("title") String title,
         @JsonProperty("authors") List<String> authors) {
        this.isbn = isbn
        this.title = title
        this.authors = authors
    }
}
