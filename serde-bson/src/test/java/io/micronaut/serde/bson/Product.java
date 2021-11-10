package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;
import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.codecs.pojo.annotations.BsonRepresentation;

@Serdeable
@BsonDiscriminator(value = "AnnotatedProduct", key = "_cls")
public class Product {
    @BsonProperty("modelName")
    private String name;
    @BsonRepresentation(BsonType.OBJECT_ID)
    private String serialNumber;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }
}
