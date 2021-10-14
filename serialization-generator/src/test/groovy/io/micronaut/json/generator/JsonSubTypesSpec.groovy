package io.micronaut.json.generator

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.json.DeserializationException
import io.micronaut.json.Deserializer
import io.micronaut.json.Serializer

class JsonSubTypesSpec extends AbstractTypeElementSpec implements SerializerUtils {
    def 'wrapper array'() {
        given:
        def compiled = buildClassLoader('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class, name = "a"),
    @JsonSubTypes.Type(value = B.class, names = {"b", "c"})
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_ARRAY)
class Base {
}

class A extends Base {
    public String fieldA;
}
class B extends Base {
    public String fieldB;
}
''')
        def deserializer = (Deserializer) compiled.loadClass('example.$Base$Deserializer').newInstance()

        def serializer = (Serializer) compiled.loadClass('example.$Base$Serializer').newInstance()
        def a = compiled.loadClass('example.A').newInstance()
        a.fieldA = 'foo'

        expect:
        deserializeFromString(deserializer, '["a",{"fieldA":"foo"}]').fieldA == 'foo'
        deserializeFromString(deserializer, '["b",{"fieldB":"foo"}]').fieldB == 'foo'
        deserializeFromString(deserializer, '["c",{"fieldB":"foo"}]').fieldB == 'foo'

        serializeToString(serializer, a) == '["a",{"fieldA":"foo"}]'
    }

    def 'wrapper object'() {
        given:
        def compiled = buildClassLoader('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
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
        def deserializer = (Deserializer) compiled.loadClass('example.$Base$Deserializer').newInstance()

        def serializer = (Serializer) compiled.loadClass('example.$Base$Serializer').newInstance()
        def a = compiled.loadClass('example.A').newInstance()
        a.fieldA = 'foo'

        expect:
        deserializeFromString(deserializer, '{"a":{"fieldA":"foo"}}').fieldA == 'foo'
        deserializeFromString(deserializer, '{"b":{"fieldB":"foo"}}').fieldB == 'foo'
        deserializeFromString(deserializer, '{"c":{"fieldB":"foo"}}').fieldB == 'foo'

        serializeToString(serializer, a) == '{"a":{"fieldA":"foo"}}'
    }

    def 'property'() {
        given:
        def compiled = buildClassLoader('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class, name = "a"),
    @JsonSubTypes.Type(value = B.class, names = {"b", "c"})
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
class Base {
}

class A extends Base {
    public String fieldA;
}
class B extends Base {
    public String fieldB;
}
''')
        def deserializer = (Deserializer) compiled.loadClass('example.$Base$Deserializer').newInstance()

        def serializer = (Serializer) compiled.loadClass('example.$Base$Serializer').newInstance()
        def a = compiled.loadClass('example.A').newInstance()
        a.fieldA = 'foo'

        expect:
        deserializeFromString(deserializer, '{"type":"a","fieldA":"foo"}').fieldA == 'foo'
        deserializeFromString(deserializer, '{"type":"b","fieldB":"foo"}').fieldB == 'foo'
        deserializeFromString(deserializer, '{"type":"c","fieldB":"foo"}').fieldB == 'foo'

        serializeToString(serializer, a) == '{"type":"a","fieldA":"foo"}'
    }

    def 'deduction'() {
        given:
        def compiled = buildClassLoader('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class),
    @JsonSubTypes.Type(value = B.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
class Base {
}

class A extends Base {
    public String fieldA;
}
class B extends Base {
    public String fieldB;
}
''')
        def deserializer = (Deserializer) compiled.loadClass('example.$Base$Deserializer').newInstance()

        def serializer = (Serializer) compiled.loadClass('example.$Base$Serializer').newInstance()
        def a = compiled.loadClass('example.A').newInstance()
        a.fieldA = 'foo'

        expect:
        deserializeFromString(deserializer, '{"fieldA":"foo"}').fieldA == 'foo'
        deserializeFromString(deserializer, '{"fieldB":"foo"}').fieldB == 'foo'

        serializeToString(serializer, a) == '{"fieldA":"foo"}'
    }

    def 'deduction with supertype prop'() {
        given:
        def compiled = buildClassLoader('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class),
    @JsonSubTypes.Type(value = B.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
class Base {
    public String sup;
}

class A extends Base {
    public String fieldA;
}
class B extends Base {
    public String fieldB;
}
''')
        def deserializer = (Deserializer) compiled.loadClass('example.$Base$Deserializer').newInstance()

        def serializer = (Serializer) compiled.loadClass('example.$Base$Serializer').newInstance()
        def a = compiled.loadClass('example.A').newInstance()
        a.sup = 'x'
        a.fieldA = 'foo'

        expect:
        deserializeFromString(deserializer, '{"sup":"x","fieldA":"foo"}').sup == 'x'
        deserializeFromString(deserializer, '{"sup":"x","fieldA":"foo"}').fieldA == 'foo'
        deserializeFromString(deserializer, '{"sup":"x","fieldB":"foo"}').sup == 'x'
        deserializeFromString(deserializer, '{"sup":"x","fieldB":"foo"}').fieldB == 'foo'

        serializeToString(serializer, a) == '{"fieldA":"foo","sup":"x"}'
    }

    def 'deduction unwrapped'() {
        given:
        def compiled = buildClassLoader('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
@JsonSubTypes({
    @JsonSubTypes.Type(value = A1.class),
    @JsonSubTypes.Type(value = B1.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
class Base1 {
    @JsonUnwrapped public Base2 base2;
}

class A1 extends Base1 {
    public String fieldA1;
}
class B1 extends Base1 {
    public String fieldB1;
}

@JsonSubTypes({
    @JsonSubTypes.Type(value = A2.class),
    @JsonSubTypes.Type(value = B2.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
class Base2 {
    public String sup;
}

class A2 extends Base2 {
    public String fieldA2;
}
class B2 extends Base2 {
    public String fieldB2;
}
''')
        def deserializer = (Deserializer) compiled.loadClass('example.$Base1$Deserializer').newInstance()
        def parsed = deserializeFromString(deserializer, '{"fieldA1":"foo","sup":"x","fieldA2":"bar"}')

        def serializer = (Serializer) compiled.loadClass('example.$Base1$Serializer').newInstance()
        def a1 = compiled.loadClass('example.A1').newInstance()
        a1.fieldA1 = 'foo'
        def a2 = compiled.loadClass('example.A2').newInstance()
        a2.sup = 'x'
        a2.fieldA2 = 'bar'
        a1.base2 = a2

        expect:
        parsed.fieldA1 == 'foo'
        parsed.base2.sup == 'x'
        parsed.base2.fieldA2 == 'bar'

        serializeToString(serializer, a1) == '{"fieldA1":"foo","fieldA2":"bar","sup":"x"}'
    }

    void 'unknown property handling on subtypes'() {
        given:
        def compiled = buildClassLoader('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class),
    @JsonSubTypes.Type(value = B.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, include = JsonTypeInfo.As.PROPERTY, property = "type")
class Base {
}
@JsonIgnoreProperties(ignoreUnknown = true)
class A extends Base {
}
@JsonIgnoreProperties(ignoreUnknown = false)
class B extends Base {
}
''')
        def deserializer = (Deserializer) compiled.loadClass('example.$Base$Deserializer').newInstance()

        expect:
        deserializeFromString(deserializer, '{"type":".A","foo":"bar"}').class.simpleName == 'A'

        when:
        deserializeFromString(deserializer, '{"type":".B","foo":"bar"}')
        then:
        thrown DeserializationException
    }

    void 'any setter merge'() {
        given:
        def compiled = buildClassLoader('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.*;
import java.util.*;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class),
    @JsonSubTypes.Type(value = B.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, include = JsonTypeInfo.As.PROPERTY, property = "type")
class Base {
}
class A extends Base {
    private Map<String, String> anySetter = new HashMap<>();
    
    @JsonAnySetter
    void put(String key, String value) {
        anySetter.put(key, value);
    }
}
class B extends Base {
    private Map<String, String> anySetter = new HashMap<>();
    
    @JsonAnySetter
    void put(String key, String value) {
        anySetter.put(key, value);
    }
}
''')
        def deserializer = (Deserializer) compiled.loadClass('example.$Base$Deserializer').newInstance()

        expect:
        deserializeFromString(deserializer, '{"type":".A","foo":"bar"}').class.simpleName == 'A'
        deserializeFromString(deserializer, '{"type":".A","foo":"bar"}').anySetter == [foo: 'bar']
        deserializeFromString(deserializer, '{"type":".B","foo":"bar"}').class.simpleName == 'B'
        deserializeFromString(deserializer, '{"type":".B","foo":"bar"}').anySetter == [foo: 'bar']

        deserializeFromString(deserializer, '{"foo":"bar","type":".A"}').anySetter == [foo: 'bar']
        deserializeFromString(deserializer, '{"foo":"bar","type":".B"}').anySetter == [foo: 'bar']
    }

    def 'JsonTypeName'() {
        given:
        def compiled = buildClassLoader('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
@JsonSubTypes({
    @JsonSubTypes.Type(A.class),
    @JsonSubTypes.Type(B.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_ARRAY)
class Base {
}

@JsonTypeName
class A extends Base {
    public String fieldA;
}
@JsonTypeName("b")
class B extends Base {
    public String fieldB;
}
''')
        def deserializer = (Deserializer) compiled.loadClass('example.$Base$Deserializer').newInstance()

        def serializer = (Serializer) compiled.loadClass('example.$Base$Serializer').newInstance()
        def a = compiled.loadClass('example.A').newInstance()
        a.fieldA = 'foo'

        expect:
        deserializeFromString(deserializer, '["A",{"fieldA":"foo"}]').fieldA == 'foo'
        deserializeFromString(deserializer, '["b",{"fieldB":"foo"}]').fieldB == 'foo'

        serializeToString(serializer, a) == '["A",{"fieldA":"foo"}]'
    }
}
