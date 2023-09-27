package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Issue

class JsonSubtypesSpec extends JsonCompileSpec {

    void 'test json sub types using name deserialization'() {
        given:
        def context = buildContext('test.Base', """
package test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    @JsonSubTypes.Type(value = Sub.class, name = "sub-class")
)
class Base {
    private String string;

    public Base(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }
}

@Serdeable
class Sub extends Base {
    private Integer integer;

    public Sub(String string, Integer integer) {
        super(string);
        this.integer = integer;
    }

    public Integer getInteger() {
        return integer;
    }
}
""")
        when:
        def baseArg = argumentOf(context, "test.Base")
        def result = jsonMapper.readValue('{"type":"sub-class","string":"a","integer":1}', baseArg)

        then:
        result.getClass().name == 'test.Sub'
        result.string == 'a'
        result.integer == 1

        when:
        result = jsonMapper.readValue('{"type":"some-other-type","string":"a","integer":1}', baseArg)

        then:
        result.getClass().name != 'test.Sub'

        when:
        result = jsonMapper.readValue('{"type":"Sub","string":"a","integer":1}', baseArg)

        then:
        result.getClass().name != 'test.Sub'
    }

    void 'test json sub types using name deserialization with type name defined'() {
        given:
        def context = buildContext('test.Base', """
package test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    @JsonSubTypes.Type(value = Sub.class)
)
class Base {
    private String string;

    public Base(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }
}

@Serdeable
@JsonTypeName("sub-class")
class Sub extends Base {
    private Integer integer;

    public Sub(String string, Integer integer) {
        super(string);
        this.integer = integer;
    }

    public Integer getInteger() {
        return integer;
    }
}
""")
        when:
        def baseArg = argumentOf(context, "test.Base")
        def result = jsonMapper.readValue('{"type":"sub-class","string":"a","integer":1}', baseArg)

        then:
        result.getClass().name == 'test.Sub'
        result.string == 'a'
        result.integer == 1

        when:
        result = jsonMapper.readValue('{"type":"some-other-type","string":"a","integer":1}', baseArg)

        then:
        result.getClass().name != 'test.Sub'

        when:
        result = jsonMapper.readValue('{"type":"Sub","string":"a","integer":1}', baseArg)

        then:
        result.getClass().name != 'test.Sub'
    }

    void 'test json sub types using name deserialization with names defined'() {
        given:
        def context = buildContext('test.Base', """
package test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    @JsonSubTypes.Type(value = Sub.class, names = {"subClass", "sub-class"})
)
class Base {
    private String string;

    public Base(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }
}

@Serdeable
@JsonTypeName("SubClass")
class Sub extends Base {
    private Integer integer;

    public Sub(String string, Integer integer) {
        super(string);
        this.integer = integer;
    }

    public Integer getInteger() {
        return integer;
    }
}
""")
        when:
        def baseArg = argumentOf(context, "test.Base")
        def result = jsonMapper.readValue('{"type":"sub-class","string":"a","integer":1}', baseArg)

        then:
        result.getClass().name == 'test.Sub'

        when:
        result = jsonMapper.readValue('{"type":"subClass","string":"a","integer":1}', baseArg)

        then:
        result.getClass().name == 'test.Sub'

        when:
        result = jsonMapper.readValue('{"type":"SubClass","string":"a","integer":1}', baseArg)

        then:
        result.getClass().name == 'test.Sub'
    }

    void 'test json sub types using name deserialization with wrapper'() {
        given:
        def context = buildContext('test.Base', """
package test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes(
    @JsonSubTypes.Type(value = Sub.class, name = "subClass")
)
class Base {
    private String string;

    public Base(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }
}

@Serdeable
class Sub extends Base {
    private Integer integer;

    public Sub(String string, Integer integer) {
        super(string);
        this.integer = integer;
    }

    public Integer getInteger() {
        return integer;
    }
}
""")
        when:
        def baseArg = argumentOf(context, "test.Base")
        def result = jsonMapper.readValue('{"subClass":{"string":"a","integer":1}}', baseArg)

        then:
        result.getClass().name == 'test.Sub'
    }

    @Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/575")
    void 'test wrapper unnesting'() {
        given:
        def context = buildContext('test.Base', """
package test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Wrapper(Base base, String other) {
}

@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes(
    @JsonSubTypes.Type(value = Sub.class, name = "subClass")
)
class Base {
    private String string;

    public Base(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }
}

@Serdeable
class Sub extends Base {
    private Integer integer;

    public Sub(String string, Integer integer) {
        super(string);
        this.integer = integer;
    }

    public Integer getInteger() {
        return integer;
    }
}
""")
        when:
        def wrapperArg = argumentOf(context, "test.Wrapper")
        def result = jsonMapper.readValue('{\"base\":{"subClass":{"string":"a","integer":1}},\"other\":\"foo\"}', wrapperArg)

        then:
        result.base.getClass().name == 'test.Sub'
        result.other == 'foo'
    }

    void 'test json sub types using name serialization'() {
        given:
        def context = buildContext('test.Base', """
package test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Sub.class, name = "sub-class"),
    @JsonSubTypes.Type(value = A.class, names = {"sub-class-a", "subClassA"}),
    @JsonSubTypes.Type(value = B.class, name = "ignore", names = {"Ignore"})
})
class Base {
    private String string;

    public Base(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }
}

class Sub extends Base {
    private Integer integer;

    public Sub(String string, Integer integer) {
        super(string);
        this.integer = integer;
    }

    public Integer getInteger() {
        return integer;
    }
}

@Serdeable
class A extends Sub {
    public A(String string, Integer integer) {
        super(string, integer);
    }
}

@JsonTypeName("sub-class-b")
class B extends Sub {
    public B(String string, Integer integer) {
        super(string, integer);
    }
}
""")
        when:
        def sub = newInstance(context, "test.Sub", "a", 1)
        def a = newInstance(context, "test.A", "hello", 2)
        def b = newInstance(context, "test.B", "b", 3)

        then:
        jsonMapper.writeValueAsString(sub) == '{"type":"sub-class","string":"a","integer":1}'
        jsonMapper.writeValueAsString(a) == '{"type":"sub-class-a","string":"hello","integer":2}'
        jsonMapper.writeValueAsString(b) == '{"type":"sub-class-b","string":"b","integer":3}'
    }
}
