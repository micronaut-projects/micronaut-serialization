package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;
import org.bson.codecs.pojo.annotations.BsonId;

@Serdeable
public class Sale4 {
    @MyAnn
    private final Quantity quantity;

    @BsonId
    private String id;

    public Sale4(
            @Serdeable.Serializable(using = QuantityAttributeConverter.class, as = Integer.class)
            Quantity quantity
    ) {
        this.quantity = quantity;
    }

    @Serdeable.Deserializable(using = QuantityAttributeConverter.class, as = Integer.class)
    public Quantity getQuantity() {
        return quantity;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
