package io.micronaut.serde.jackson.object;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class ConstructorArgs {
    private final String title;
    private String author;
    private final int pages;
    private String other;

    public ConstructorArgs(String title, int pages) {
        this.title = title;
        this.pages = pages;
    }

    public String getTitle() {
        return title;
    }

    public int getPages() {
        return pages;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getOther() {
        return other;
    }

    public void setOther(String other) {
        this.other = other;
    }
}
