/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.serde.toml.fixture;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

@Serdeable
@JsonPropertyOrder({"int1", "int2", "int3", "hex1", "oct1", "bin1", "decimal1"})
public final class NumericValues {
    private int int1;
    private long int2;
    private BigInteger int3;
    private BigInteger hex1;
    private BigInteger oct1;
    private BigInteger bin1;
    private BigDecimal decimal1;

    public NumericValues() {
    }

    public NumericValues(int int1,
                         long int2,
                         BigInteger int3,
                         BigInteger hex1,
                         BigInteger oct1,
                         BigInteger bin1,
                         BigDecimal decimal1) {
        this.int1 = int1;
        this.int2 = int2;
        this.int3 = int3;
        this.hex1 = hex1;
        this.oct1 = oct1;
        this.bin1 = bin1;
        this.decimal1 = decimal1;
    }

    public int getInt1() {
        return int1;
    }

    public void setInt1(int int1) {
        this.int1 = int1;
    }

    public long getInt2() {
        return int2;
    }

    public void setInt2(long int2) {
        this.int2 = int2;
    }

    public BigInteger getInt3() {
        return int3;
    }

    public void setInt3(BigInteger int3) {
        this.int3 = int3;
    }

    public BigInteger getHex1() {
        return hex1;
    }

    public void setHex1(BigInteger hex1) {
        this.hex1 = hex1;
    }

    public BigInteger getOct1() {
        return oct1;
    }

    public void setOct1(BigInteger oct1) {
        this.oct1 = oct1;
    }

    public BigInteger getBin1() {
        return bin1;
    }

    public void setBin1(BigInteger bin1) {
        this.bin1 = bin1;
    }

    public BigDecimal getDecimal1() {
        return decimal1;
    }

    public void setDecimal1(BigDecimal decimal1) {
        this.decimal1 = decimal1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NumericValues that)) {
            return false;
        }
        return int1 == that.int1
            && int2 == that.int2
            && Objects.equals(int3, that.int3)
            && Objects.equals(hex1, that.hex1)
            && Objects.equals(oct1, that.oct1)
            && Objects.equals(bin1, that.bin1)
            && Objects.equals(decimal1, that.decimal1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(int1, int2, int3, hex1, oct1, bin1, decimal1);
    }
}
