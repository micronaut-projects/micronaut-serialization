package example;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;

@Serdeable // <1>
public class Book {
    private final String title;
    @JsonbProperty("qty") // <2>
    private final int quantity;

    @JsonbCreator // <3>
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
