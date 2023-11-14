package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonPropertyOrderSpec
import spock.lang.Unroll

class SerdeJsonPropertyOrderSpec extends JsonPropertyOrderSpec {

    // Jackson Databind doesn't support reordering the properties of the property bean

    @Unroll
    void "test @JsonPropertyOrder on property where order is #order"() {
        given:
            def context = buildContext("""
package jsonorder;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Other {
    @JsonPropertyOrder(${formatOrder(order)})
    private Test test;
    public void setTest(jsonorder.Test test) {
        this.test = test;
    }
    public jsonorder.Test getTest() {
        return test;
    }
}
@Serdeable
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
""")
            def o = newInstance(context, 'jsonorder.Other')
            def t = newInstance(context, 'jsonorder.Test')
            o.test = t

        expect:
            writeJson(jsonMapper, o) == result

        cleanup:
            context.close()

        where:
            order           | result
            ['a', 'b', 'c'] | '{"test":{"a":1,"b":2,"c":3}}'
            ['c', 'a', 'b'] | '{"test":{"c":3,"a":1,"b":2}}'
    }

    @Unroll
    void "test @JsonPropertyOrder with renamed property on property where order is #order"() {
        // NOTE: Jackson can pick the original name or the renamed name
        given:
            def context = buildContext("""
package jsonorder;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Other {
    @JsonPropertyOrder(${formatOrder(order)})
    private Test test;
    public void setTest(jsonorder.Test test) {
        this.test = test;
    }
    public jsonorder.Test getTest() {
        return test;
    }
}
@Serdeable
class Test {
    private int c = 3;
    @com.fasterxml.jackson.annotation.JsonProperty("d")
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
""")
            def o = newInstance(context, 'jsonorder.Other')
            def t = newInstance(context, 'jsonorder.Test')
            o.test = t

        expect:
            writeJson(jsonMapper, o) == result

        cleanup:
            context.close()

        where:
            order           | result
            ['a', 'b', 'c'] | '{"test":{"a":1,"d":2,"c":3}}'
            ['c', 'a', 'b'] | '{"test":{"c":3,"a":1,"d":2}}'
            ['a', 'd', 'c'] | '{"test":{"a":1,"d":2,"c":3}}'
            ['c', 'a', 'd'] | '{"test":{"c":3,"a":1,"d":2}}'
    }

    @Unroll
    void "test @JsonPropertyOrder with renamed property to existing one on property where order is #order"() {
        // NOTE: Jackson can pick the original name or the renamed name
        given:
            def context = buildContext("""
package jsonorder;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Other {
    @JsonPropertyOrder(${formatOrder(order)})
    private Test test;
    public void setTest(jsonorder.Test test) {
        this.test = test;
    }
    public jsonorder.Test getTest() {
        return test;
    }
}
@Serdeable
class Test {
    private int c = 3;
    @com.fasterxml.jackson.annotation.JsonProperty("d")
    private int b = 2;
    @com.fasterxml.jackson.annotation.JsonProperty("b")
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
""")
            def o = newInstance(context, 'jsonorder.Other')
            def t = newInstance(context, 'jsonorder.Test')
            o.test = t

        expect:
            writeJson(jsonMapper, o) == result

        cleanup:
            context.close()

        where:
            order           | result
            ['a', 'b', 'c'] | '{"test":{"b":1,"d":2,"c":3}}'
            ['b', 'd', 'c'] | '{"test":{"b":1,"d":2,"c":3}}'
            ['c', 'a', 'b'] | '{"test":{"c":3,"b":1,"d":2}}'
            ['a', 'd', 'c'] | '{"test":{"b":1,"d":2,"c":3}}'
            ['c', 'a', 'd'] | '{"test":{"c":3,"b":1,"d":2}}'
    }


}
