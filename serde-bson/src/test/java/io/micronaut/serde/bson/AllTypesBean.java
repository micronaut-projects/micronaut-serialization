package io.micronaut.serde.bson;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

@Serdeable
public class AllTypesBean {

    private boolean someBool;
    private int someInt;
    private long someLong;
    private double someDouble;
    private short someShort;
    private float someFloat;
    private byte someByte;
    @Nullable
    private Boolean someBoolean;
    @Nullable
    private String someString;
    @Nullable
    private Integer someInteger;
    @Nullable
    private Long someLongObj;
    @Nullable
    private Double someDoubleObj;
    @Nullable
    private Short someShortObj;
    @Nullable
    private Float someFloatObj;
    @Nullable
    private Byte someByteObj;
    @Nullable
    private BigDecimal bigDecimal;
    @Nullable
    private BigInteger bigInteger;
    @Nullable
    private Decimal128 decimal128;
    @Nullable
    private ObjectId objectId;

    public int getSomeInt() {
        return someInt;
    }

    public void setSomeInt(int someInt) {
        this.someInt = someInt;
    }

    public long getSomeLong() {
        return someLong;
    }

    public void setSomeLong(long someLong) {
        this.someLong = someLong;
    }

    public double getSomeDouble() {
        return someDouble;
    }

    public void setSomeDouble(double someDouble) {
        this.someDouble = someDouble;
    }

    public short getSomeShort() {
        return someShort;
    }

    public void setSomeShort(short someShort) {
        this.someShort = someShort;
    }

    public float getSomeFloat() {
        return someFloat;
    }

    public void setSomeFloat(float someFloat) {
        this.someFloat = someFloat;
    }

    public byte getSomeByte() {
        return someByte;
    }

    public void setSomeByte(byte someByte) {
        this.someByte = someByte;
    }

    public String getSomeString() {
        return someString;
    }

    public void setSomeString(String someString) {
        this.someString = someString;
    }

    public Integer getSomeInteger() {
        return someInteger;
    }

    public void setSomeInteger(Integer someInteger) {
        this.someInteger = someInteger;
    }

    public Long getSomeLongObj() {
        return someLongObj;
    }

    public void setSomeLongObj(Long someLongObj) {
        this.someLongObj = someLongObj;
    }

    public Double getSomeDoubleObj() {
        return someDoubleObj;
    }

    public void setSomeDoubleObj(Double someDoubleObj) {
        this.someDoubleObj = someDoubleObj;
    }

    public Short getSomeShortObj() {
        return someShortObj;
    }

    public void setSomeShortObj(Short someShortObj) {
        this.someShortObj = someShortObj;
    }

    public Float getSomeFloatObj() {
        return someFloatObj;
    }

    public void setSomeFloatObj(Float someFloatObj) {
        this.someFloatObj = someFloatObj;
    }

    public Byte getSomeByteObj() {
        return someByteObj;
    }

    public void setSomeByteObj(Byte someByteObj) {
        this.someByteObj = someByteObj;
    }

    public BigDecimal getBigDecimal() {
        return bigDecimal;
    }

    public void setBigDecimal(BigDecimal bigDecimal) {
        this.bigDecimal = bigDecimal;
    }

    public BigInteger getBigInteger() {
        return bigInteger;
    }

    public void setBigInteger(BigInteger bigInteger) {
        this.bigInteger = bigInteger;
    }

    @Nullable
    public Decimal128 getDecimal128() {
        return decimal128;
    }

    public void setDecimal128(@Nullable Decimal128 decimal128) {
        this.decimal128 = decimal128;
    }

    @Nullable
    public Boolean getSomeBoolean() {
        return someBoolean;
    }

    public void setSomeBoolean(@Nullable Boolean someBoolean) {
        this.someBoolean = someBoolean;
    }

    public boolean isSomeBool() {
        return someBool;
    }

    public void setSomeBool(boolean someBool) {
        this.someBool = someBool;
    }

    @Nullable
    public ObjectId getObjectId() {
        return objectId;
    }

    public void setObjectId(@Nullable ObjectId objectId) {
        this.objectId = objectId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AllTypesBean that = (AllTypesBean) o;
        return someBool == that.someBool && someInt == that.someInt && someLong == that.someLong && Double.compare(that.someDouble, someDouble) == 0 && someShort == that.someShort && Float.compare(that.someFloat, someFloat) == 0 && someByte == that.someByte && Objects.equals(someBoolean, that.someBoolean) && Objects.equals(someString, that.someString) && Objects.equals(someInteger, that.someInteger) && Objects.equals(someLongObj, that.someLongObj) && Objects.equals(someDoubleObj, that.someDoubleObj) && Objects.equals(someShortObj, that.someShortObj) && Objects.equals(someFloatObj, that.someFloatObj) && Objects.equals(someByteObj, that.someByteObj) && Objects.equals(bigDecimal, that.bigDecimal) && Objects.equals(bigInteger, that.bigInteger) && Objects.equals(decimal128, that.decimal128) && Objects.equals(objectId, that.objectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(someBool, someInt, someLong, someDouble, someShort, someFloat, someByte, someBoolean, someString, someInteger, someLongObj, someDoubleObj, someShortObj, someFloatObj, someByteObj, bigDecimal, bigInteger, decimal128, objectId);
    }
}
