package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;
import org.bson.BsonBinary;
import org.bson.BsonDbPointer;
import org.bson.BsonRegularExpression;
import org.bson.BsonTimestamp;
import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonRepresentation;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Serdeable
public class CustomTypes {

    private Decimal128 decimal128;
    private ObjectId objectId;
    private BsonRegularExpression regularExpression;
    private BsonBinary binary;
    @BsonRepresentation(BsonType.BINARY)
    private byte[] bytes;
    @BsonRepresentation(BsonType.BINARY)
    private UUID uuid;
    private BsonDbPointer dbPointer;
    private BsonTimestamp bsonTimestamp;
    @BsonRepresentation(BsonType.DATE_TIME)
    private long dateTime;

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

    public BsonRegularExpression getRegularExpression() {
        return regularExpression;
    }

    public void setRegularExpression(BsonRegularExpression regularExpression) {
        this.regularExpression = regularExpression;
    }

    public BsonBinary getBinary() {
        return binary;
    }

    public void setBinary(BsonBinary binary) {
        this.binary = binary;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public BsonDbPointer getDbPointer() {
        return dbPointer;
    }

    public void setDbPointer(BsonDbPointer dbPointer) {
        this.dbPointer = dbPointer;
    }

    public BsonTimestamp getBsonTimestamp() {
        return bsonTimestamp;
    }

    public void setBsonTimestamp(BsonTimestamp bsonTimestamp) {
        this.bsonTimestamp = bsonTimestamp;
    }

    public long getDateTime() {
        return dateTime;
    }

    public void setDateTime(long dateTime) {
        this.dateTime = dateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomTypes that = (CustomTypes) o;
        return dateTime == that.dateTime && Objects.equals(decimal128, that.decimal128) && Objects.equals(objectId, that.objectId) && Objects.equals(regularExpression, that.regularExpression) && Objects.equals(binary, that.binary) && Arrays.equals(bytes, that.bytes) && Objects.equals(uuid, that.uuid) && Objects.equals(dbPointer, that.dbPointer) && Objects.equals(bsonTimestamp, that.bsonTimestamp);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(decimal128, objectId, regularExpression, binary, uuid, dbPointer, bsonTimestamp, dateTime);
        result = 31 * result + Arrays.hashCode(bytes);
        return result;
    }
}
