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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import io.micronaut.serde.annotation.Serdeable;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

/**
 * An XML-serializable book.
 *
 * @since 3.2
 */
@Serdeable // <1>
@JsonRootName("book") // <2>
public class Book {
    @JacksonXmlProperty(isAttribute = true, localName = "isbn") // <3>
    private final String isbn;
    @JacksonXmlProperty(localName = "title") // <4>
    private final String title;
    @JacksonXmlElementWrapper(localName = "authors") // <5>
    @JacksonXmlProperty(localName = "author") // <6>
    private final List<String> authors;

    /**
     * Creates a book.
     *
     * @param isbn The ISBN
     * @param title The title
     * @param authors The authors
     */
    @JsonCreator
    public Book(@JsonProperty("isbn") String isbn,
                @JsonProperty("title") String title,
                @JsonProperty("authors") List<String> authors) {
        this.isbn = isbn;
        this.title = title;
        this.authors = authors;
    }

    /**
     * @return The ISBN
     */
    public String getIsbn() {
        return isbn;
    }

    /**
     * @return The title
     */
    public String getTitle() {
        return title;
    }

    /**
     * @return The authors
     */
    public List<String> getAuthors() {
        return authors;
    }
}
