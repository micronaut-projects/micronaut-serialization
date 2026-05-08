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
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Serdeable
public class CustomTypes {

    @Nullable
    private Decimal128 decimal128;
    @Nullable
    private ObjectId objectId;
    @Nullable
    private BsonRegularExpression regularExpression;
    @Nullable
    private BsonBinary binary;
    @BsonRepresentation(BsonType.BINARY)
    private byte @Nullable [] bytes;
    @BsonRepresentation(BsonType.BINARY)
    @Nullable
    private UUID uuid;
    @Nullable
    private BsonDbPointer dbPointer;
    @Nullable
    private BsonTimestamp bsonTimestamp;
    @BsonRepresentation(BsonType.DATE_TIME)
    private long dateTime;

    public @Nullable Decimal128 getDecimal128() {
        return decimal128;
    }

    public void setDecimal128(@Nullable Decimal128 decimal128) {
        this.decimal128 = decimal128;
    }

    public @Nullable ObjectId getObjectId() {
        return objectId;
    }

    public void setObjectId(@Nullable ObjectId objectId) {
        this.objectId = objectId;
    }

    public @Nullable BsonRegularExpression getRegularExpression() {
        return regularExpression;
    }

    public void setRegularExpression(@Nullable BsonRegularExpression regularExpression) {
        this.regularExpression = regularExpression;
    }

    public @Nullable BsonBinary getBinary() {
        return binary;
    }

    public void setBinary(@Nullable BsonBinary binary) {
        this.binary = binary;
    }

    public byte @Nullable [] getBytes() {
        return bytes;
    }

    public void setBytes(byte @Nullable [] bytes) {
        this.bytes = bytes;
    }

    public @Nullable UUID getUuid() {
        return uuid;
    }

    public void setUuid(@Nullable UUID uuid) {
        this.uuid = uuid;
    }

    public @Nullable BsonDbPointer getDbPointer() {
        return dbPointer;
    }

    public void setDbPointer(@Nullable BsonDbPointer dbPointer) {
        this.dbPointer = dbPointer;
    }

    public @Nullable BsonTimestamp getBsonTimestamp() {
        return bsonTimestamp;
    }

    public void setBsonTimestamp(@Nullable BsonTimestamp bsonTimestamp) {
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
