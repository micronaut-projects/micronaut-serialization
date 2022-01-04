
package io.micronaut.serde.bson;

public class Quantity {

    private final int amount;

    public Quantity(int amount) {
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }

    public static Quantity valueOf(int amount) {
        return new Quantity(amount);
    }
}
