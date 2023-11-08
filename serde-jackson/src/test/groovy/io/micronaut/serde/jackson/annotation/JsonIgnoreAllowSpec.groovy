package io.micronaut.serde.jackson.annotation

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.core.type.Argument
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.PendingFeature

class JsonIgnoreAllowSpec extends JsonCompileSpec {

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of("micronaut.serde.deserialization.ignore-unknown", "false")
        ))
    }

    void "test @JsonIgnoreType"() {
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Other {
    private Test test;

    public void setTest(test.Test test) {
        this.test = test;
    }
    public test.Test getTest() {
        return test;
    }
}
@Serdeable
@JsonIgnoreType
class Test {
    private String value = "ignored";
    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }
}
""")
        def t = newInstance(context, 'test.Test')
        t.value = 'test'
        def o = newInstance(context, 'test.Other')
        o.test = t

        when:
        def result = writeJson(jsonMapper, o)

        then:
        result == '{}'

        when:
        def read = jsonMapper.readValue('{"test":{"value":"test"}}', Argument.of(o.getClass()))

        then:"ignored for deserialization"
        read.test == null

        cleanup:
        context.close()
    }

    void "test simple @JsonIgnoreProperties"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonIgnoreProperties("ignored")
class Test {
    private String value;
    private boolean ignored;
    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }

    public void setIgnored(boolean b) {
        this.ignored = b;
    }

    public boolean isIgnored() {
        return ignored;
    }
}
""", [value:'test'])


        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"value":"test"}'

        when:"deserialization happens"
        def value = jsonMapper.readValue('{"value":"test","ignored":true}', typeUnderTest)

        then:"the property is ignored for the purposes of deserialization"
        value.ignored == false

        cleanup:
        context.close()
    }

    void "test simple @JsonIncludeProperties"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonIncludeProperties("value")
class Test {
    private String value;
    private boolean ignored;
    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }

    public void setIgnored(boolean b) {
        this.ignored = b;
    }

    public boolean isIgnored() {
        return ignored;
    }
}
""", [value:'test'])


        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"value":"test"}'

        when:"deserialization happens"
        def value = jsonMapper.readValue('{"value":"test","ignored":true}', typeUnderTest)

        then:"the property is ignored for the purposes of deserialization"
        value.ignored == false

        cleanup:
        context.close()
    }

    void "test ignoreUnknown=false with @JsonIgnoreProperties"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonIgnoreProperties(ignoreUnknown = false)
class Test {
    private String value;
    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }
}
""", [value:'test'])
        when:
        def result = jsonMapper.readValue('{"value":"test"}', typeUnderTest)

        then:
        result.value == 'test'

        when:
        jsonMapper.readValue('{"value":"test","unknown":true}', typeUnderTest)

        then:
        def e = thrown(SerdeException)
        e.message == 'Unknown property [unknown] encountered during deserialization of type: Test'

        cleanup:
        context.close()
    }

    void "test combined @JsonIgnoreProperties"() {
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Other {
    @JsonIgnoreProperties("ignored2")
    private Test test;

    public void setTest(test.Test test) {
        this.test = test;
    }
    public test.Test getTest() {
        return test;
    }
}
@Serdeable
@JsonIgnoreProperties("ignored")
class Test {
    private String value;
    private boolean ignored;
    private boolean ignored2;
    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }

    public void setIgnored(boolean b) {
        this.ignored = b;
    }

    public boolean isIgnored() {
        return ignored;
    }

    public void setIgnored2(boolean ignored2) {
        this.ignored2 = ignored2;
    }

    public boolean isIgnored2() {
        return ignored2;
    }
}
""")
        def t = newInstance(context, 'test.Test')
        t.value = 'test'
        def o = newInstance(context, 'test.Other')
        o.test = t

        expect:
        writeJson(jsonMapper, o) == '{"test":{"value":"test"}}'

        cleanup:
        context.close()
    }

    void "test multiple combined @JsonIgnoreProperties"() {
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Other {
    @JsonIgnoreProperties("ignored2")
    private Test test;

    private Test test2;

    public void setTest(test.Test test) {
        this.test = test;
    }
    public test.Test getTest() {
        return test;
    }

    public void setTest2(test.Test test) {
        this.test2 = test;
    }
    public test.Test getTest2() {
        return test2;
    }
}
@Serdeable
@JsonIgnoreProperties("ignored")
class Test {
    private String value;
    private boolean ignored;
    private boolean ignored2;
    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }

    public void setIgnored(boolean b) {
        this.ignored = b;
    }

    public boolean isIgnored() {
        return ignored;
    }

    public void setIgnored2(boolean ignored2) {
        this.ignored2 = ignored2;
    }

    public boolean isIgnored2() {
        return ignored2;
    }
}
""")

        def t1 = newInstance(context, 'test.Test')
        t1.value = 'test1'
        def t2 = newInstance(context, 'test.Test')
        t2.value = 'test2'
        def o = newInstance(context, 'test.Other')
        o.test = t1
        o.test.ignored = true
        o.test.ignored2 = true
        o.test2 = t2
        o.test2.ignored = true
        o.test2.ignored2 = true

        when:
        def json = writeJson(jsonMapper, o)

        then:
        json == '{"test":{"value":"test1"},"test2":{"value":"test2","ignored2":true}}'

        when:
        def bean = jsonMapper.readValue(json, context.classLoader.loadClass("test.Other"))

        then:
        bean.test.value == "test1"
        !bean.test.ignored
        !bean.test.ignored2
        bean.test2.value == "test2"
        !bean.test2.ignored
        bean.test2.ignored2

        cleanup:
        context.close()
    }

    void "test combined @JsonIncludeProperties"() {
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Other {
    @JsonIncludeProperties("value")
    private Test test;

    public void setTest(test.Test test) {
        this.test = test;
    }
    public test.Test getTest() {
        return test;
    }
}
@Serdeable
@JsonIncludeProperties({"ignored", "value"})
class Test {
    private String value;
    private boolean ignored;
    private boolean ignored2;
    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }

    public void setIgnored(boolean b) {
        this.ignored = b;
    }

    public boolean isIgnored() {
        return ignored;
    }

    public void setIgnored2(boolean ignored2) {
        this.ignored2 = ignored2;
    }

    public boolean isIgnored2() {
        return ignored2;
    }
}
""")
        def t = newInstance(context, 'test.Test')
        t.value = 'test'
        def o = newInstance(context, 'test.Other')
        o.test = t

        expect:
        writeJson(jsonMapper, o) == '{"test":{"value":"test"}}'

        cleanup:
        context.close()
    }

    void "test @JsonIgnore on field"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private String value;
    @JsonIgnore
    private boolean ignored;
    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }

    public void setIgnored(boolean b) {
        this.ignored = b;
    }

    public boolean isIgnored() {
        return ignored;
    }
}
""", [value:'test'])
        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":"test"}'

        cleanup:
        context.close()

    }

    void "test @JsonIgnore on method"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private String value;

    private boolean ignored;
    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setIgnored(boolean b) {
        this.ignored = b;
    }

    @JsonIgnore
    public boolean isIgnored() {
        return ignored;
    }
}
""", [value:'test'])
        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":"test"}'

        cleanup:
        context.close()

    }

    void "test @JsonIgnore without @Inherited on interface method is inherited"() {
        given:
            def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test implements MyInterface {
    private String value;

    private boolean ignored;
    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }

    public void setIgnored(boolean b) {
        this.ignored = b;
    }

    public boolean isIgnored() {
        return ignored;
    }
}

interface MyInterface {

    @JsonIgnore
    boolean isIgnored();

}


""", [value:'test'])
        expect:
            writeJson(jsonMapper, beanUnderTest) == '{"value":"test"}'

        cleanup:
            context.close()

    }

    void "test @JsonIgnore without @Inherited needs to be put on the implementation"() {
        given:
            def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test implements MyInterface {
    private String value;

    private boolean ignored;
    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }

    public void setIgnored(boolean b) {
        this.ignored = b;
    }

    @JsonIgnore
    public boolean isIgnored() {
        return ignored;
    }
}

interface MyInterface {

    @JsonIgnore
    boolean isIgnored();

}


""", [value:'test'])
        expect:
            writeJson(jsonMapper, beanUnderTest) == '{"value":"test"}'

        cleanup:
            context.close()

    }

    void "test ignoreUnknown=false on a record throws and exception"() {
        given:
            def context = buildContext('test.DeserializableRecord', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.serde.annotation.Serdeable.Deserializable;

@Deserializable
@JsonIgnoreProperties
record DeserializableRecord(String value) {
}


""")
        when:
            Object deserialized = jsonMapper.readValue("""
        {
            "unknown": "abc",
            "value": "xyz"
        }""", typeUnderTest)

        then:
        def e = thrown(SerdeException)
        e.message == 'Unknown property [unknown] encountered during deserialization of type: DeserializableRecord'

        cleanup:
            context.close()

    }

    void "test ignoreUnknown=false on a record throws and exception 2"() {
        given:
            def context = buildContext('test.DeserializableRecord', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.serde.annotation.Serdeable.Deserializable;

@Deserializable
@JsonIgnoreProperties(ignoreUnknown = false)
record DeserializableRecord(String value) {
}


""")
        when:
            Object deserialized = jsonMapper.readValue("""
        {
            "unknown": "abc",
            "value": "xyz"
        }""", typeUnderTest)

        then:
        def e = thrown(SerdeException)
        e.message == 'Unknown property [unknown] encountered during deserialization of type: DeserializableRecord'

        cleanup:
            context.close()

    }

    void "test ignoreUnknown=true on a record doesn't throws and exception"() {
        given:
            def context = buildContext('test.DeserializableRecord', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.serde.annotation.Serdeable.Deserializable;

@Deserializable
@JsonIgnoreProperties(ignoreUnknown = true)
record DeserializableRecord(String value) {
}


""")
        when:
            Object deserialized = jsonMapper.readValue("""
        {
            "unknown": "abc",
            "value": "xyz"
        }""", typeUnderTest)

        then:
            deserialized.value == "xyz"

        cleanup:
            context.close()

    }

    void "test ignoreUnknown=false on a class throws and exception"() {
        given:
            def context = buildContext('test.DeserializableRecord', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.serde.annotation.Serdeable.Deserializable;

@Deserializable
@JsonIgnoreProperties
class DeserializableRecord {

    private String value;

    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}


""")
        when:
            Object deserialized = jsonMapper.readValue("""
        {
            "unknown": "abc",
            "value": "xyz"
        }""", typeUnderTest)

        then:
        def e = thrown(SerdeException)
        e.message == 'Unknown property [unknown] encountered during deserialization of type: DeserializableRecord'

        cleanup:
            context.close()

    }

    void "test ignoreUnknown=false on a class throws and exception 2"() {
        given:
            def context = buildContext('test.DeserializableRecord', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.serde.annotation.Serdeable.Deserializable;

@Deserializable
@JsonIgnoreProperties
class DeserializableRecord {

    private String value;

    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}

""")
        when:
            Object deserialized = jsonMapper.readValue("""
        {
            "unknown": "abc",
            "value": "xyz"
        }""", typeUnderTest)

        then:
        def e = thrown(SerdeException)
        e.message == 'Unknown property [unknown] encountered during deserialization of type: DeserializableRecord'

        cleanup:
            context.close()

    }

    void "test ignoreUnknown=true on a class doesn't throws and exception"() {
        given:
            def context = buildContext('test.DeserializableRecord', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.serde.annotation.Serdeable.Deserializable;

@Deserializable
@JsonIgnoreProperties(ignoreUnknown = true)
class DeserializableRecord {

    private String value;

    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}

""")
        when:
            Object deserialized = jsonMapper.readValue("""
        {
            "unknown": "abc",
            "value": "xyz"
        }""", typeUnderTest)

        then:
            deserialized.value == "xyz"

        cleanup:
            context.close()

    }

    //

    void "test @JsonIgnoreProperties inheritance"() {
        given:
            def context = buildContext('test.A', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties("p2")
class A extends B {

    private String p1;
    private String p2;

    public String getP1() {
        return p1;
    }

    public void setP1(String p1) {
        this.p1 = p1;
    }

    public String getP2() {
        return p2;
    }

    public void setP2(String p2) {
        this.p2 = p2;
    }
}

@JsonIgnoreProperties("a2")
class B extends C {

    private String a1;
    private String a2;

    public String getA1() {
        return a1;
    }

    public void setA1(String a1) {
        this.a1 = a1;
    }

    public String getA2() {
        return a2;
    }

    public void setA2(String a2) {
        this.a2 = a2;
    }
}

@JsonIgnoreProperties("f2")
class C {

    private String f1;
    private String f2;

    public String getF1() {
        return f1;
    }

    public void setF1(String f1) {
        this.f1 = f1;
    }

    public String getF2() {
        return f2;
    }

    public void setF2(String f2) {
        this.f2 = f2;
    }
}


""")
        when:
            def a = newInstance(context, 'test.A')
            a.setA1("a1")
            a.setA2("a2")
            a.setP1("p1")
            a.setP2("p2")
            a.setF1("f1")
            a.setF2("f2")
            String json = jsonMapper.writeValueAsString(a)

        then: // Jackson annotation is not inherited, only the one ignore is applied
            json == '{"f1":"f1","f2":"f2","a1":"a1","a2":"a2","p1":"p1"}'

        cleanup:
            context.close()

    }

    @PendingFeature
    void "test @JsonIgnoreProperties inheritance 2"() {
        given:
            def context = buildContext('test.C3', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.serde.annotation.Serdeable;

@JsonIgnoreProperties("foo")
abstract class C1 {
    abstract String getFoo();
}

abstract class C2 extends C1 {
}

@Serdeable
class C3 extends C2 {
    private final String foo;

    C3(String foo) {
        this.foo = foo;
    }

    @Override
    String getFoo() {
        return foo;
    }
}


""")
        when:
            def a = newInstance(context, 'test.C3', 'bar')
            String json = jsonMapper.writeValueAsString(a)

        then:
            json == '{}'

        cleanup:
            context.close()

    }

    void "test @JsonIgnoreProperties(allowSetters = true)"() {
        given:
            def context = buildContext('test.A', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(value = "p2", allowSetters = true)
class A {

    private String p1;
    private String p2;

    public String getP1() {
        return p1;
    }

    public void setP1(String p1) {
        this.p1 = p1;
    }

    public String getP2() {
        return p2;
    }

    public void setP2(String p2) {
        this.p2 = p2;
    }
}


""")
        when:
            def a = newInstance(context, 'test.A')
            a.setP1("abc")
            a.setP2("xyz")
            String json = jsonMapper.writeValueAsString(a)

        then:
            json == '{"p1":"abc"}'

        when:
            def bean = jsonMapper.readValue('{"p1":"abc","p2":"xyz"}', a.class)

        then:
            bean.p1 == "abc"
            bean.p2 == "xyz"

        when:
            jsonMapper.readValue('{"p1":"abc","p2":"xyz","p3":"foobar"}', a.class)

        then:
            def e = thrown(SerdeException)
            e.message == "Unknown property [p3] encountered during deserialization of type: A"

        cleanup:
            context.close()
    }

    void "test @JsonIgnoreProperties(allowSetters = true, gnoreUnknown = true)"() {
        given:
            def context = buildContext('test.A', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(value = "p2", allowSetters = true, ignoreUnknown = true)
class A {

    private String p1;
    private String p2;

    public String getP1() {
        return p1;
    }

    public void setP1(String p1) {
        this.p1 = p1;
    }

    public String getP2() {
        return p2;
    }

    public void setP2(String p2) {
        this.p2 = p2;
    }
}


""")
        when:
            def a = newInstance(context, 'test.A')
            a.setP1("abc")
            a.setP2("xyz")
            String json = jsonMapper.writeValueAsString(a)

        then:
            json == '{"p1":"abc"}'

        when:
            def bean = jsonMapper.readValue('{"p1":"abc","p2":"xyz"}', a.class)

        then:
            bean.p1 == "abc"
            bean.p2 == "xyz"

        when:
            bean = jsonMapper.readValue('{"p1":"abc","p2":"xyz","p3":"foobar"}', a.class)

        then:
            bean.p1 == "abc"
            bean.p2 == "xyz"

        cleanup:
            context.close()
    }

    void "test @JsonIgnoreProperties(allowGetters = true)"() {
        given:
            def context = buildContext('test.A', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(value = "p2", allowGetters = true)
class A {

    private String p1;
    private String p2;

    public String getP1() {
        return p1;
    }

    public void setP1(String p1) {
        this.p1 = p1;
    }

    public String getP2() {
        return p2;
    }

    public void setP2(String p2) {
        this.p2 = p2;
    }
}


""")
        when:
            def a = newInstance(context, 'test.A')
            a.setP1("abc")
            a.setP2("xyz")
            String json = jsonMapper.writeValueAsString(a)

        then:
            json == '{"p1":"abc","p2":"xyz"}'

        when:
            def bean = jsonMapper.readValue('{"p1":"abc","p2":"xyz"}', a.class)

        then:
            bean.p1 == "abc"
            bean.p2 == null

        when:
            jsonMapper.readValue('{"p1":"abc","p2":"xyz","p3":"foobar"}', a.class)

        then:
            def e = thrown(SerdeException)
            e.message == "Unknown property [p3] encountered during deserialization of type: A"

        cleanup:
            context.close()

    }

    void "test @JsonIgnoreProperties(allowGetters = true, ignoreUnknown = true)"() {
        given:
            def context = buildContext('test.A', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(value = "p2", allowGetters = true, ignoreUnknown = true)
class A {

    private String p1;
    private String p2;

    public String getP1() {
        return p1;
    }

    public void setP1(String p1) {
        this.p1 = p1;
    }

    public String getP2() {
        return p2;
    }

    public void setP2(String p2) {
        this.p2 = p2;
    }
}


""")
        when:
            def a = newInstance(context, 'test.A')
            a.setP1("abc")
            a.setP2("xyz")
            String json = jsonMapper.writeValueAsString(a)

        then:
            json == '{"p1":"abc","p2":"xyz"}'

        when:
            def bean = jsonMapper.readValue('{"p1":"abc","p2":"xyz"}', a.class)

        then:
            bean.p1 == "abc"
            bean.p2 == null

        when:
            bean = jsonMapper.readValue('{"p1":"abc","p2":"xyz","p3":"foobar"}', a.class)

        then:
            bean.p1 == "abc"
            bean.p2 == null

        cleanup:
            context.close()

    }

    void "test combined @JsonIncludeProperties 2"() {
        given:
            def context = buildContext('test.Root', """
package test;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Root {

    @JsonIncludeProperties({"a1", "a2", "p1"})
    private A a;

    public void setA(A a) {
        this.a = a;
    }

    public A getA() {
        return a;
    }
}

@JsonIncludeProperties({"p1", "p2"})
class A extends B {

    private String p1;
    private String p2;

    public String getP1() {
        return p1;
    }

    public void setP1(String p1) {
        this.p1 = p1;
    }

    public String getP2() {
        return p2;
    }

    public void setP2(String p2) {
        this.p2 = p2;
    }
}

class B extends C {

    private String a1;
    private String a2;

    public String getA1() {
        return a1;
    }

    public void setA1(String a1) {
        this.a1 = a1;
    }

    public String getA2() {
        return a2;
    }

    public void setA2(String a2) {
        this.a2 = a2;
    }
}

class C {

    private String f1;
    private String f2;

    public String getF1() {
        return f1;
    }

    public void setF1(String f1) {
        this.f1 = f1;
    }

    public String getF2() {
        return f2;
    }

    public void setF2(String f2) {
        this.f2 = f2;
    }
}


""")
        when:
            def a = newInstance(context, 'test.A')
            a.setA1("a1")
            a.setA2("a2")
            a.setP1("p1")
            a.setP2("p2")
            a.setF1("f1")
            a.setF2("f2")
            def root = newInstance(context, 'test.Root')
            root.setA(a)

            String json = jsonMapper.writeValueAsString(root)


        then:
            json == '{"a":{"p1":"p1"}}'

        when:
            def bean = jsonMapper.readValue('{"a":{"p1":"p1","a1":"a1","p2":"p2","a2":"a2"}}', root.class)

        then: "@JsonIncludeProperties ignores unknown"
            bean.a.p1 == "p1"
            bean.a.p2 == null
            bean.a.a1 == null
            bean.a.a2 == null

        cleanup:
            context.close()

    }

    void "test combined @JsonIncludeProperties and @JsonIncludeProperties"() {
        given:
            def context = buildContext('test.Root', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Root {

    @JsonIgnoreProperties({"p1"})
    private A a;

    public void setA(A a) {
        this.a = a;
    }

    public A getA() {
        return a;
    }
}

@JsonIncludeProperties({"p1", "p2"})
class A extends B {

    private String p1;
    private String p2;

    public String getP1() {
        return p1;
    }

    public void setP1(String p1) {
        this.p1 = p1;
    }

    public String getP2() {
        return p2;
    }

    public void setP2(String p2) {
        this.p2 = p2;
    }
}

class B extends C {

    private String a1;
    private String a2;

    public String getA1() {
        return a1;
    }

    public void setA1(String a1) {
        this.a1 = a1;
    }

    public String getA2() {
        return a2;
    }

    public void setA2(String a2) {
        this.a2 = a2;
    }
}

class C {

    private String f1;
    private String f2;

    public String getF1() {
        return f1;
    }

    public void setF1(String f1) {
        this.f1 = f1;
    }

    public String getF2() {
        return f2;
    }

    public void setF2(String f2) {
        this.f2 = f2;
    }
}


""")
        when:
            def a = newInstance(context, 'test.A')
            a.setA1("a1")
            a.setA2("a2")
            a.setP1("p1")
            a.setP2("p2")
            a.setF1("f1")
            a.setF2("f2")
            def root = newInstance(context, 'test.Root')
            root.setA(a)

            String json = jsonMapper.writeValueAsString(root)

        then:
            json == '{"a":{"p2":"p2"}}'

        when:
            def bean = jsonMapper.readValue('{"a":{"p1":"p1","a1":"a1","p2":"p2","a2":"a2"}}', root.class)

        then: "@JsonIncludeProperties ignores unknown"
            bean.a.p1 == null
            bean.a.p2 == "p2"
            bean.a.a1 == null
            bean.a.a2 == null
        cleanup:
            context.close()

    }

    void "test combined @JsonIncludeProperties and @JsonIncludeProperties 2"() {
        given:
            def context = buildContext('test.Root', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Root {

    @JsonIncludeProperties({"a1", "p1"})
    private A a;

    public void setA(A a) {
        this.a = a;
    }

    public A getA() {
        return a;
    }
}

@JsonIgnoreProperties({"p1", "p2"})
class A extends B {

    private String p1;
    private String p2;

    public String getP1() {
        return p1;
    }

    public void setP1(String p1) {
        this.p1 = p1;
    }

    public String getP2() {
        return p2;
    }

    public void setP2(String p2) {
        this.p2 = p2;
    }
}

class B extends C {

    private String a1;
    private String a2;

    public String getA1() {
        return a1;
    }

    public void setA1(String a1) {
        this.a1 = a1;
    }

    public String getA2() {
        return a2;
    }

    public void setA2(String a2) {
        this.a2 = a2;
    }
}

class C {

    private String f1;
    private String f2;

    public String getF1() {
        return f1;
    }

    public void setF1(String f1) {
        this.f1 = f1;
    }

    public String getF2() {
        return f2;
    }

    public void setF2(String f2) {
        this.f2 = f2;
    }
}


""")
        when:
            def a = newInstance(context, 'test.A')
            a.setA1("a1")
            a.setA2("a2")
            a.setP1("p1")
            a.setP2("p2")
            a.setF1("f1")
            a.setF2("f2")
            def root = newInstance(context, 'test.Root')
            root.setA(a)

            String json = jsonMapper.writeValueAsString(root)

        then:
            json == '{"a":{"a1":"a1"}}'

        when:
            def bean = jsonMapper.readValue('{"a":{"a1":"a1","a2":"a2","xx":"yy"}}', root.class)

        then: "@JsonIncludeProperties ignores any other properties"
            noExceptionThrown()
            bean.a.a1 == "a1"
            bean.a.a2 == null

        when:
            jsonMapper.readValue('{"a":{"a1":"a1","a2":"a2","xx":"yy"},"b":"123"}', root.class)

        then: "Root doesn't allow any extra properties"
            def e = thrown(SerdeException)
            e.message == "Unknown property [b] encountered during deserialization of type: Root"

        cleanup:
            context.close()
    }

    void "test combined @JsonIgnoreProperties and @JsonIgnoreProperties"() {
        given:
            def context = buildContext('test.Root', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Root {

    @JsonIgnoreProperties({"a1", "p1"})
    private A a;

    public void setA(A a) {
        this.a = a;
    }

    public A getA() {
        return a;
    }
}

@JsonIgnoreProperties({"p1", "p2"})
class A extends B {

    private String p1;
    private String p2;

    public String getP1() {
        return p1;
    }

    public void setP1(String p1) {
        this.p1 = p1;
    }

    public String getP2() {
        return p2;
    }

    public void setP2(String p2) {
        this.p2 = p2;
    }
}

class B extends C {

    private String a1;
    private String a2;

    public String getA1() {
        return a1;
    }

    public void setA1(String a1) {
        this.a1 = a1;
    }

    public String getA2() {
        return a2;
    }

    public void setA2(String a2) {
        this.a2 = a2;
    }
}

class C {

    private String f1;
    private String f2;

    public String getF1() {
        return f1;
    }

    public void setF1(String f1) {
        this.f1 = f1;
    }

    public String getF2() {
        return f2;
    }

    public void setF2(String f2) {
        this.f2 = f2;
    }
}


""")
        when:
            def a = newInstance(context, 'test.A')
            a.setA1("a1")
            a.setA2("a2")
            a.setP1("p1")
            a.setP2("p2")
            a.setF1("f1")
            a.setF2("f2")
            def root = newInstance(context, 'test.Root')
            root.setA(a)

            String json = jsonMapper.writeValueAsString(root)

        then:
            json == '{"a":{"f1":"f1","f2":"f2","a2":"a2"}}'

        when:
            def bean = jsonMapper.readValue('{"a":{"f1":"f1","f2":"f2","a2":"a2","a1":"a1","p1":"p1","p2":"p2"}}', root.class)

        then:
            bean.a.a1 == null
            bean.a.a2 == "a2"
            bean.a.f1 == "f1"
            bean.a.f2 == "f2"
            bean.a.p1 == null
            bean.a.p2 == null

        when:
            jsonMapper.readValue('{"a":{"f1":"f1","f2":"f2","a2":"a2","a1":"a1","xxx":"yyy"}}', root.class)

        then:
            def e = thrown(SerdeException)
            e.cause.message == "Unknown property [xxx] encountered during deserialization of type: A a"

        cleanup:
            context.close()
    }

}
