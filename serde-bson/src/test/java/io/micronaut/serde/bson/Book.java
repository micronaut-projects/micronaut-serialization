package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;
import org.bson.types.ObjectId;

@Serdeable
public class Book {
    private ObjectId objectId;
    private String title;
    private int pages;

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
