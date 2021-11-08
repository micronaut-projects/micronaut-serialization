package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.util.Objects;

@Serdeable
public class CustomTypes {

    private Decimal128 decimal128;
    private ObjectId objectId;

    public Decimal128 getDecimal128() {
        return decimal128;
    }

    public void setDecimal128(Decimal128 decimal128) {
        this.decimal128 = decimal128;
    }

    public ObjectId getObjectId() {
        return objectId;
    }

    public void setObjectId(ObjectId objectId) {
        this.objectId = objectId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomTypes that = (CustomTypes) o;
        return Objects.equals(decimal128, that.decimal128) && Objects.equals(objectId, that.objectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(decimal128, objectId);
    }
}
