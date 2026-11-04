package io.micronaut.serde.oracle.jdbc.json;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class OsonBook {
    private Long id;
    private String title;
    private int pages;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
