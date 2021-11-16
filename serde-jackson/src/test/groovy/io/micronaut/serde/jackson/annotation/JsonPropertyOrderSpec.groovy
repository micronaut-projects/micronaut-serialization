package io.micronaut.serde.jackson.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Unroll

class JsonPropertyOrderSpec extends JsonCompileSpec {

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

    private static String formatOrder(List list) {
        "{${list.collect { "\"$it\"" }.join(",")}}"
    }
}
