package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable // <1>
public class Book {
    private final String title;
    private final int quantity;

    public Book(String title, int quantity) {
        this.title = title;
        this.quantity = quantity;
    }

    public String getTitle() {
        return title;
    }

    public int getQuantity() {
        return quantity;
    }
}
