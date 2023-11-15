package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonSubtypesSpec

class SerdeJsonSubtypesSpec extends JsonSubtypesSpec {

    // Jackson will fail on unknown type - Serde will deserialize the base type

    void 'test json sub types using name deserialization with unknown type'() {
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

    void 'test json sub types using name deserialization with type name defined with unknown type'() {
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
}
