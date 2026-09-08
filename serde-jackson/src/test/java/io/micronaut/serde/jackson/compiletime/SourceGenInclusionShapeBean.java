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
import java.util.List;
import java.util.Map;

/**
 * Covers every property shape a generated serializer can take an inclusion decision for: primitives,
 * boxed scalars encoded without a property serializer, and values written through a property
 * serializer.
 */
@SerdeableGenerated
@Introspected
public class SourceGenInclusionShapeBean {
    private String text;
    private Boolean boxedFlag;
    private Character boxedLetter;
    private Byte boxedByte;
    private Short boxedShort;
    private Integer boxedInt;
    private Long boxedLong;
    private Float boxedFloat;
    private Double boxedDouble;
    private BigInteger bigInteger;
    private BigDecimal bigDecimal;
    private boolean flag;
    private char letter;
    private int count;
    private long id;
    private float ratio;
    private double score;
    private List<String> tags;
    private Map<String, String> attributes;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Boolean getBoxedFlag() {
        return boxedFlag;
    }

    public void setBoxedFlag(Boolean boxedFlag) {
        this.boxedFlag = boxedFlag;
    }

    public Character getBoxedLetter() {
        return boxedLetter;
    }

    public void setBoxedLetter(Character boxedLetter) {
        this.boxedLetter = boxedLetter;
    }

    public Byte getBoxedByte() {
        return boxedByte;
    }

    public void setBoxedByte(Byte boxedByte) {
        this.boxedByte = boxedByte;
    }

    public Short getBoxedShort() {
        return boxedShort;
    }

    public void setBoxedShort(Short boxedShort) {
        this.boxedShort = boxedShort;
    }

    public Integer getBoxedInt() {
        return boxedInt;
    }

    public void setBoxedInt(Integer boxedInt) {
        this.boxedInt = boxedInt;
    }

    public Long getBoxedLong() {
        return boxedLong;
    }

    public void setBoxedLong(Long boxedLong) {
        this.boxedLong = boxedLong;
    }

    public Float getBoxedFloat() {
        return boxedFloat;
    }

    public void setBoxedFloat(Float boxedFloat) {
        this.boxedFloat = boxedFloat;
    }

    public Double getBoxedDouble() {
        return boxedDouble;
    }

    public void setBoxedDouble(Double boxedDouble) {
        this.boxedDouble = boxedDouble;
    }

    public BigInteger getBigInteger() {
        return bigInteger;
    }

    public void setBigInteger(BigInteger bigInteger) {
        this.bigInteger = bigInteger;
    }

    public BigDecimal getBigDecimal() {
        return bigDecimal;
    }

    public void setBigDecimal(BigDecimal bigDecimal) {
        this.bigDecimal = bigDecimal;
    }

    public boolean isFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public char getLetter() {
        return letter;
    }

    public void setLetter(char letter) {
        this.letter = letter;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public float getRatio() {
        return ratio;
    }

    public void setRatio(float ratio) {
        this.ratio = ratio;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }
}
