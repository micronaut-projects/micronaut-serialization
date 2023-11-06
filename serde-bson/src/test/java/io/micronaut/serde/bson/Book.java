package io.micronaut.serde.bson;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.annotation.Serdeable;
import org.bson.types.ObjectId;

@Serdeable
public class Book {
    @NonNull
    private ObjectId objectId;
    private String title;
    private int pages;

    public Book() {
    }

    @Creator
    public Book(ObjectId objectId, String title, int pages) {
        this.objectId = objectId;
        this.title = title;
        this.pages = pages;
    }

    public ObjectId getObjectId() {
        return objectId;
    }

    public void setObjectId(ObjectId objectId) {
        this.objectId = objectId;
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
