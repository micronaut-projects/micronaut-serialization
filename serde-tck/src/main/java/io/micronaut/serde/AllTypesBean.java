/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

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

    public boolean isSomeBool() {
        return someBool;
    }

    public void setSomeBool(boolean someBool) {
        this.someBool = someBool;
    }

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

    @Nullable
    public Boolean getSomeBoolean() {
        return someBoolean;
    }

    public void setSomeBoolean(@Nullable Boolean someBoolean) {
        this.someBoolean = someBoolean;
    }

    @Nullable
    public String getSomeString() {
        return someString;
    }

    public void setSomeString(@Nullable String someString) {
        this.someString = someString;
    }

    @Nullable
    public Integer getSomeInteger() {
        return someInteger;
    }

    public void setSomeInteger(@Nullable Integer someInteger) {
        this.someInteger = someInteger;
    }

    @Nullable
    public Long getSomeLongObj() {
        return someLongObj;
    }

    public void setSomeLongObj(@Nullable Long someLongObj) {
        this.someLongObj = someLongObj;
    }

    @Nullable
    public Double getSomeDoubleObj() {
        return someDoubleObj;
    }

    public void setSomeDoubleObj(@Nullable Double someDoubleObj) {
        this.someDoubleObj = someDoubleObj;
    }

    @Nullable
    public Short getSomeShortObj() {
        return someShortObj;
    }

    public void setSomeShortObj(@Nullable Short someShortObj) {
        this.someShortObj = someShortObj;
    }

    @Nullable
    public Float getSomeFloatObj() {
        return someFloatObj;
    }

    public void setSomeFloatObj(@Nullable Float someFloatObj) {
        this.someFloatObj = someFloatObj;
    }

    @Nullable
    public Byte getSomeByteObj() {
        return someByteObj;
    }

    public void setSomeByteObj(@Nullable Byte someByteObj) {
        this.someByteObj = someByteObj;
    }

    @Nullable
    public BigDecimal getBigDecimal() {
        return bigDecimal;
    }

    public void setBigDecimal(@Nullable BigDecimal bigDecimal) {
        this.bigDecimal = bigDecimal;
    }

    @Nullable
    public BigInteger getBigInteger() {
        return bigInteger;
    }

    public void setBigInteger(@Nullable BigInteger bigInteger) {
        this.bigInteger = bigInteger;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AllTypesBean that = (AllTypesBean) o;
        return someBool == that.someBool && someInt == that.someInt && someLong == that.someLong && Double.compare(that.someDouble, someDouble) == 0 && someShort == that.someShort && Float.compare(that.someFloat, someFloat) == 0 && someByte == that.someByte && Objects.equals(someBoolean, that.someBoolean) && Objects.equals(someString, that.someString) && Objects.equals(someInteger, that.someInteger) && Objects.equals(someLongObj, that.someLongObj) && Objects.equals(someDoubleObj, that.someDoubleObj) && Objects.equals(someShortObj, that.someShortObj) && Objects.equals(someFloatObj, that.someFloatObj) && Objects.equals(someByteObj, that.someByteObj) && Objects.equals(bigDecimal, that.bigDecimal) && Objects.equals(bigInteger, that.bigInteger);
    }

    @Override
    public int hashCode() {
        return Objects.hash(someBool, someInt, someLong, someDouble, someShort, someFloat, someByte, someBoolean, someString, someInteger, someLongObj, someDoubleObj, someShortObj, someFloatObj, someByteObj, bigDecimal, bigInteger);
    }
}
