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
package io.micronaut.serde.jackson.compiletime;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeableGenerated;

import java.math.BigDecimal;
import java.math.BigInteger;

@SerdeableGenerated
@Introspected
public class SourceGenNonDefaultScalarBean {
    private Integer count;
    private BigInteger bigCount;
    private BigDecimal bigAmount;

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public BigInteger getBigCount() {
        return bigCount;
    }

    public void setBigCount(BigInteger bigCount) {
        this.bigCount = bigCount;
    }

    public BigDecimal getBigAmount() {
        return bigAmount;
    }

    public void setBigAmount(BigDecimal bigAmount) {
        this.bigAmount = bigAmount;
    }
}
