package io.micronaut.serde.toml.fixture;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonPropertyOrder({"title", "pages", "author"})
public class Book {
    private String title;
    private int pages;
    private Author author;

    public Book() {
    }

    public Book(String title, int pages, Author author) {
        this.title = title;
        this.pages = pages;
        this.author = author;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }

    @Serdeable
    @JsonPropertyOrder({"name"})
    public static class Author {
        private String name;

        public Author() {
        }

        public Author(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
