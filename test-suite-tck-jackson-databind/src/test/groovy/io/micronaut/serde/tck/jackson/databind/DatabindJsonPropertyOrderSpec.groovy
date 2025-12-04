package io.micronaut.serde.tck.jackson.databind

import io.micronaut.serde.jackson.JsonPropertyOrderSpec
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import spock.lang.PendingFeature
import spock.lang.Unroll

class DatabindJsonPropertyOrderSpec extends JsonPropertyOrderSpec {

    void "test basic order inherit 1"() {
        given:
            def context = buildContext('jsonorder.Test', """
package jsonorder;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test extends SuperTest {
    private int c = 3;
    private int b = 2;
    private int a = 1;
    private final int d;

    public Test(int z, int d) {
        super(z);
        this.d = d;
    }

    public void setA(int a) {
        this.a = a;
    }
    public void setB(int b) {
        this.b = b;
    }
    public void setC(int c) {
        this.c = c;
    }

    public int getD() {
        return d;
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

@Serdeable
class SuperTest {
    private int x = 3;
    private int y = 2;
    private final int z;
    public SuperTest(int z) {
        this.z = z;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public int getY() {
        return y;
    }
}
""")
        when:
            def t = context.classLoader.loadClass('jsonorder.Test')
            beanUnderTest = t.newInstance(4, 5)
        then:
        JSONAssert.assertEquals(writeJson(jsonMapper, beanUnderTest), '{"x":3,"y":2,"z":4,"c":3,"b":2,"a":1,"d":5}', JSONCompareMode.NON_EXTENSIBLE)
    }

    void "test basic order with a constructor 1"() {
        given:
            def context = buildContext('jsonorder.Test', """
package jsonorder;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private int c = 3;
    private int b = 2;
    private int a = 1;
    private final int d;

    public Test(int d) {
        this.d = d;
    }

    public void setA(int a) {
        this.a = a;
    }
    public void setB(int b) {
        this.b = b;
    }
    public void setC(int c) {
        this.c = c;
    }

    public int getD() {
        return d;
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
        when:
            def t = context.classLoader.loadClass('jsonorder.Test')
            beanUnderTest = t.newInstance(4)
        then:
        JSONAssert.assertEquals(writeJson(jsonMapper, beanUnderTest), '{"c":3,"b":2,"a":1,"d":4}', JSONCompareMode.NON_EXTENSIBLE)
    }

    void "test basic order with a constructor 2"() {
        given:
            def context = buildContext('jsonorder.Test', """
package jsonorder;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private final int d;
    private int c = 3;
    private int b = 2;
    private int a = 1;

    public Test(int d) {
        this.d = d;
    }

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

    public int getD() {
        return d;
    }
}
""")
        when:
            def t = context.classLoader.loadClass('jsonorder.Test')
            beanUnderTest = t.newInstance(4)
        then:
        JSONAssert.assertEquals(writeJson(jsonMapper, beanUnderTest), '{"d":4,"c":3,"b":2,"a":1}', JSONCompareMode.NON_EXTENSIBLE)
    }

    void "test basic order with a constructor 2x"() {
        given:
            def context = buildContext('jsonorder.Test', """
package jsonorder;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private int c = 3;
    private int b = 2;
    private int a = 1;
    private final int d;

    public Test(int d) {
        this.d = d;
    }

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

    public int getD() {
        return d;
    }
}
""")
        when:
            def t = context.classLoader.loadClass('jsonorder.Test')
            beanUnderTest = t.newInstance(4)
        then:
        JSONAssert.assertEquals(writeJson(jsonMapper, beanUnderTest), '{"c":3,"b":2,"a":1,"d":4}', JSONCompareMode.NON_EXTENSIBLE)
    }

    void "test order is default"() {
        given:
            buildContext('jsonorder.Test', """
package jsonorder;

import io.micronaut.serde.annotation.Serdeable;

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
""", [:])
        expect:
        JSONAssert.assertEquals(writeJson(jsonMapper, beanUnderTest), '{"c":3,"b":2,"a":1}', JSONCompareMode.NON_EXTENSIBLE)
    }

    void "test renamed property on type where order default"() {
        given:
            buildContext('jsonorder.Test', """
package jsonorder;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
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
        JSONAssert.assertEquals(writeJson(jsonMapper, beanUnderTest), '{"c":3,"a":1,"d":2}', JSONCompareMode.NON_EXTENSIBLE)
    }

    void "test renamed property to existing one on type where order is default"() {
        given:
            buildContext('jsonorder.Test', """
package jsonorder;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
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
            JSONAssert.assertEquals(writeJson(jsonMapper, beanUnderTest), '{"c":3,"d":2,"b":1}', JSONCompareMode.NON_EXTENSIBLE)
    }

    void "test renamed getter property to existing one on type where order is default"() {
        given:
            buildContext('jsonorder.Test', """
package jsonorder;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

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

    @JsonProperty("b")
    public int getA() {
        return a;
    }

    @JsonProperty("d")
    public int getB() {
        return b;
    }
    public int getC() {
        return c;
    }
}
""", [:])
        expect:
        JSONAssert.assertEquals(writeJson(jsonMapper, beanUnderTest), '{"c":3,"d":2,"b":1}', JSONCompareMode.NON_EXTENSIBLE)
    }

    void "test renamed getter property to existing one on type where order is default X"() {
        given:
            buildContext('jsonorder.Test', """
package jsonorder;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

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

    @JsonProperty("d")
    public int getB() {
        return b;
    }
    public int getC() {
        return c;
    }
}
""", [:])
        expect:
        JSONAssert.assertEquals(writeJson(jsonMapper, beanUnderTest), '{"c":3,"a":1,"d":2}', JSONCompareMode.NON_EXTENSIBLE)
    }

    void "test renamed JsonGetter property to existing one on type where order is default"() {
        given:
            buildContext('jsonorder.Test', """
package jsonorder;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

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

    @JsonGetter("b")
    public int getA() {
        return a;
    }

    @JsonGetter("d")
    public int getB() {
        return b;
    }
    public int getC() {
        return c;
    }
}
""", [:])
        expect:
        JSONAssert.assertEquals(writeJson(jsonMapper, beanUnderTest), '{"c":3,"d":2,"b":1}', JSONCompareMode.NON_EXTENSIBLE)
    }

    void "test property order with renamed JsonGetter property order"() {
        given:
            buildContext('jsonorder.Test', """
package jsonorder;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

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

    @JsonGetter("d")
    public int getB() {
        return b;
    }
    public int getC() {
        return c;
    }
}
""", [:])
        expect:
        JSONAssert.assertEquals(writeJson(jsonMapper, beanUnderTest),  '{"c":3,"a":1,"d":2}', JSONCompareMode.NON_EXTENSIBLE)

    }

    void "test property order with bean and JsonProperty 1"() {
        given:
            buildContext('jsonorder.Test', """
package jsonorder;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

@Serdeable
final class Test {
    @JsonProperty("d")
    private final int a;
    private final int b;

    Test(@JsonProperty("d") int a, int b) {
        this.a = a;
        this.b = b;
    }

    public int getA() {
        return a;
    }

    public int getB() {
        return b;
    }
}
""")
        when:
            def bean = typeUnderTest.getType().newInstance(1, 2)
        then:
            writeJson(jsonMapper, bean) ==  '{"d":1,"b":2}'
    }

    void "test property order with bean and JsonProperty 1X"() {
        given:
            buildContext('jsonorder.Test', """
package jsonorder;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

@Serdeable
final class Test {
    @JsonProperty("d")
    private final int a;
    private final int b;

    Test(int a, int b) {
        this.a = a;
        this.b = b;
    }

    public int getA() {
        return a;
    }

    public int getB() {
        return b;
    }
}
""")
        when:
            def bean = typeUnderTest.getType().newInstance(1, 2)
        then:
            writeJson(jsonMapper, bean) ==  '{"b":2,"d":1}'
    }

    void "test property order with bean and JsonProperty 2"() {
        given:
            buildContext('jsonorder.Test', """
package jsonorder;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

@Serdeable
final class Test {
    private final int a;
    @JsonProperty("d")
    private final int b;
    private final int c;

    Test(int a, int b, int c) {
        this.a = a;
        this.b = b;
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
        when:
            def bean = typeUnderTest.getType().newInstance(1, 2, 3)
        then:
            writeJson(jsonMapper, bean) ==  '{"a":1,"c":3,"d":2}'
    }

    void "test property order with record and JsonProperty 1"() {
        given:
            buildContext('jsonorder.Test', """
package jsonorder;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Test(@JsonProperty("d") int a, int b) {
}
""")
        when:
            def bean = typeUnderTest.getType().newInstance(1, 2)
        then:
            writeJson(jsonMapper, bean) ==  '{"d":1,"b":2}'
    }

    @PendingFeature(reason = "Not supported by Jackson Databind")
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


}
