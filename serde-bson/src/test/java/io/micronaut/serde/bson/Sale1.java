package io.micronaut.serde.bson;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.annotation.Serdeable;
import org.bson.codecs.pojo.annotations.BsonId;

@Serdeable
public class Sale1 {
    @MyAnn1
    @MyAnn2
    @Serdeable.Serializable(using = QuantityAttributeConverter.class, as = Integer.class)
    @Serdeable.Deserializable(using = QuantityAttributeConverter.class, as = Integer.class)
    private Quantity quantity;

    @NonNull
    @MyAnn1
    @MyAnn2
    @Serdeable.Serializable(using = QuantityAttributeConverter.class, as = Integer.class)
    @Serdeable.Deserializable(using = QuantityAttributeConverter.class, as = Integer.class)
    private Quantity nullQuantity;

    @BsonId
    private String id;

    public void setQuantity(Quantity quantity) {
        this.quantity = quantity;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public Quantity getNullQuantity() {
        return nullQuantity;
    }

    public void setNullQuantity(Quantity nullQuantity) {
        this.nullQuantity = nullQuantity;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
