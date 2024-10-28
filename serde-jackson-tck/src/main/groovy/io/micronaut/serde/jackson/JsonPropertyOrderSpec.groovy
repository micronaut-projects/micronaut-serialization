/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.serde.jackson


import spock.lang.Unroll

abstract class JsonPropertyOrderSpec extends JsonCompileSpec {

    @Unroll
    void "test @JsonPropertyOrder on type where order is #order"() {
        given:
            buildContext('jsonorder.Test', """
package jsonorder;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonPropertyOrder(${formatOrder(order)})
class Test {
    private int c = 3;
    private int b = 2;
    private int a = 1;
    public void setA(int a) {
        this.a = a;
    }
    public void setB(int b) {
        this.b = b;
    }
    public void setC(int c) {
        this.c = c;
    }
    public int getA() {
        return a;
    }
    public int getB() {
        return b;
    }
    public int getC() {
        return c;
    }
}
""", [:])
        expect:
            writeJson(jsonMapper, beanUnderTest) == result

        where:
            order           | result
            ['a', 'b', 'c'] | '{"a":1,"b":2,"c":3}'
            ['c', 'a', 'b'] | '{"c":3,"a":1,"b":2}'
    }

    @Unroll
    void "test @JsonPropertyOrder with renamed property on type where order is #order"() {
        given:
            buildContext('jsonorder.Test', """
package jsonorder;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonPropertyOrder(${formatOrder(order)})
class Test {
    private int c = 3;
    @JsonProperty("d")
    private int b = 2;
    private int a = 1;
    public void setA(int a) {
        this.a = a;
    }
    public void setB(int b) {
        this.b = b;
    }
    public void setC(int c) {
        this.c = c;
    }
    public int getA() {
        return a;
    }
    public int getB() {
        return b;
    }
    public int getC() {
        return c;
    }
}
""", [:])
        expect:
            writeJson(jsonMapper, beanUnderTest) == result

        where:
            order           | result
            ['a', 'b', 'c'] | '{"a":1,"d":2,"c":3}'
            ['c', 'a', 'b'] | '{"c":3,"a":1,"d":2}'
            ['a', 'd', 'c'] | '{"a":1,"d":2,"c":3}'
            ['c', 'a', 'd'] | '{"c":3,"a":1,"d":2}'
    }

    @Unroll
    void "test @JsonPropertyOrder with renamed property to existing one on type where order is #order"() {
        given:
            buildContext('jsonorder.Test', """
package jsonorder;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonPropertyOrder(${formatOrder(order)})
class Test {
    private int c = 3;
    @JsonProperty("d")
    private int b = 2;
    @JsonProperty("b")
    private int a = 1;
    public void setA(int a) {
        this.a = a;
    }
    public void setB(int b) {
        this.b = b;
    }
    public void setC(int c) {
        this.c = c;
    }
    public int getA() {
        return a;
    }
    public int getB() {
        return b;
    }
    public int getC() {
        return c;
    }
}
""", [:])
        expect:
            writeJson(jsonMapper, beanUnderTest) == result

        where:
            order           | result
            ['a', 'b', 'c'] | '{"b":1,"c":3,"d":2}'
            ['b', 'd', 'c'] | '{"b":1,"d":2,"c":3}'
            ['c', 'a', 'b'] | '{"c":3,"b":1,"d":2}'
            ['a', 'd', 'c'] | '{"b":1,"d":2,"c":3}'
            ['c', 'a', 'd'] | '{"c":3,"b":1,"d":2}'
    }

    protected static String formatOrder(List list) {
        "{${list.collect { "\"$it\"" }.join(",")}}"
    }
}
