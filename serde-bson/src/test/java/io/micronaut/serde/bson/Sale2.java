package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;
import org.bson.codecs.pojo.annotations.BsonId;
import org.jspecify.annotations.Nullable;

@Serdeable
public class Sale2 {
    @MyAnn1
    @MyAnn2
    @Serdeable.Serializable(using = QuantityAttributeConverter.class, as = Integer.class)
    @Serdeable.Deserializable(using = QuantityAttributeConverter.class, as = Integer.class)
    private final Quantity quantity;

    @BsonId
    @Nullable
    private String id;

    public Sale2(Quantity quantity) {
        this.quantity = quantity;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public @Nullable String getId() {
        return id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }
}
