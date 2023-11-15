package io.micronaut.serde.jackson


import spock.lang.Issue

abstract class JsonSubtypesSpec extends JsonCompileSpec {

    def 'test JsonSubTypes with wrapper object'() {
        given:
        def compiled = buildContext('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class, name = "a"),
    @JsonSubTypes.Type(value = B.class, names = {"b", "c"})
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
class Base {
}

class A extends Base {
    public String fieldA;
}

class B extends Base {
    public String fieldB;
}
''')

        def baseClass = compiled.classLoader.loadClass('example.Base')
        def a = newInstance(compiled, 'example.A')
        a.fieldA = 'foo'

        expect:
        deserializeFromString(jsonMapper, baseClass, '{"a":{"fieldA":"foo"}}').fieldA == 'foo'
        deserializeFromString(jsonMapper, baseClass, '{"b":{"fieldB":"foo"}}').fieldB == 'foo'
        deserializeFromString(jsonMapper, baseClass, '{"c":{"fieldB":"foo"}}').fieldB == 'foo'

        serializeToString(jsonMapper, a) == '{"a":{"fieldA":"foo"}}'

        cleanup:
        compiled.close()
    }

    void 'test json sub types using name deserialization'() {
        given:
        def context = buildContext('test.Base', """
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = Base.class)
@JsonSubTypes(
    @JsonSubTypes.Type(value = Sub.class, name = "sub-class")
)
class Base {
    private String string;

    @JsonCreator
    public Base(@JsonProperty("string") String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }
}

@Serdeable
class Sub extends Base {
    private Integer integer;

    @JsonCreator
    public Sub(@JsonProperty("string") String string, @JsonProperty("integer") Integer integer) {
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
        result = jsonMapper.readValue('{"type":"unknown","string":"a","integer":1}', baseArg)

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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = Base.class)
@JsonSubTypes(
    @JsonSubTypes.Type(value = Sub.class)
)
class Base {
    private String string;

    @JsonCreator
    public Base(@JsonProperty("string") String string) {
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

    @JsonCreator
    public Sub(@JsonProperty("string") String string, @JsonProperty("integer") Integer integer) {
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
        result = jsonMapper.readValue('{"type":"unknown","string":"a","integer":1}', baseArg)

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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    public Base(@JsonProperty("string") String string) {
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

    @JsonCreator
    public Sub(@JsonProperty("string") String string, @JsonProperty("integer") Integer integer) {
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    public Base(@JsonProperty("string") String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }
}

@Serdeable
class Sub extends Base {
    private Integer integer;

    @JsonCreator
    public Sub(@JsonProperty("string") String string, @JsonProperty("integer") Integer integer) {
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    public Base(@JsonProperty("string") String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }
}

@Serdeable
class Sub extends Base {
    private Integer integer;

    @JsonCreator
    public Sub(@JsonProperty("string") String string, @JsonProperty("integer") Integer integer) {
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    public Base(@JsonProperty("string") String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }
}

class Sub extends Base {
    private Integer integer;

    @JsonCreator
    public Sub(@JsonProperty("string") String string, @JsonProperty("integer") Integer integer) {
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

    // TODO: move
    void 'subtype SerdeImport'() {
        given:
        def context = buildContext('test.Sub', """
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.serde.annotation.SerdeImport;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    @JsonSubTypes.Type(value = Sub.class, name = "sub-class")
)
class Base {
    private String string;

    @JsonCreator
    public Base(@JsonProperty("string") String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }
}

@SerdeImport(Sub.class)
public class Sub extends Base {
    private Integer integer;

    @JsonCreator
    public Sub(@JsonProperty("string") String string, @JsonProperty("integer") Integer integer) {
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
    }
}
