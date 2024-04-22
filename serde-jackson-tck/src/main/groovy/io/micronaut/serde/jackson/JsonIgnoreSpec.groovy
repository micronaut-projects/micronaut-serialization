package io.micronaut.serde.jackson

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.jackson.tst.AfterCareStatsEntry
import io.micronaut.serde.jackson.tst.ClassificationAndStats
import io.micronaut.serde.jackson.tst.ClassificationVars
import io.micronaut.serde.jackson.tst.MainAggregationVm

abstract class JsonIgnoreSpec extends JsonCompileSpec {

    abstract protected String unknownPropertyMessage(String propertyName, String className)

    def 'JsonIgnore and enum as map keys'() {
        given:
            def ctx = ApplicationContext.run()
            def jsonMapper = ctx.getBean(JsonMapper)
            def obj = new MainAggregationVm(
                    List.of(
                            new ClassificationAndStats(
                                    new ClassificationVars("01"),
                                    new AfterCareStatsEntry()
                            )
                    )
            )
            def json = '{"afterCare":[{"klassifisering":{"regionKode":"01"},"stats":{"SomeField1":0,"SomeField2":0}}]}'
        expect:
            serializeToString(jsonMapper, obj) == json

        cleanup:
            ctx.close()
    }

     void 'JsonIgnoreType'() {
        given:
        def compiled = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import io.micronaut.core.annotation.Introspected;

@io.micronaut.serde.annotation.Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public String foo;
    public Used used;
}
@JsonIgnoreType
@io.micronaut.serde.annotation.Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Used {
    public String bar;
}
''')
        def bean = newInstance(compiled, 'example.Test')
        bean.foo = '42'
        bean.used = newInstance(compiled, 'example.Used')
        bean.used.bar = '56'

        expect:
        serializeToString(jsonMapper, bean) == '{"foo":"42"}'
        deserializeFromString(jsonMapper, bean.getClass(), '{"foo":"42","used":{"bar":"56"}}').used == null
    }

    void "json ignore"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonIgnoreProperties(ignoreUnknown = true)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonIgnore
    public String foo;
    public String bar;
}
''', [:])

        def des = jsonMapper.readValue('{"foo": "1", "bar": "2"}', typeUnderTest)
        beanUnderTest.foo = "1"
        beanUnderTest.bar = "2"
        def serialized = writeJson(jsonMapper, beanUnderTest)

        expect:
        des.foo == null
        des.bar == "2"
        serialized == '{"bar":"2"}'

        cleanup:
        context.close()
    }

    void "json ignore on a bean"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonIgnoreProperties(ignoreUnknown = true)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonIgnore
    public Ignored foo;
    public String bar;
}
class Ignored {
}
''', [:])

        def des = jsonMapper.readValue('{"foo": "1", "bar": "2"}', typeUnderTest)

        expect:
        des.foo == null
        des.bar == "2"

        cleanup:
        context.close()
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
        def e = thrown(Exception)
        e.message.contains unknownPropertyMessage("unknown", "test.Test")

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
        def e = thrown(Exception)
        e.message.contains unknownPropertyMessage("unknown", "test.DeserializableRecord")

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
        def e = thrown(Exception)
        e.message.contains unknownPropertyMessage("unknown", "test.DeserializableRecord")

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
        def e = thrown(Exception)
        e.message.contains unknownPropertyMessage("unknown", "test.DeserializableRecord")

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
        def e = thrown(Exception)
        e.message.contains unknownPropertyMessage("unknown", "test.DeserializableRecord")

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

}
