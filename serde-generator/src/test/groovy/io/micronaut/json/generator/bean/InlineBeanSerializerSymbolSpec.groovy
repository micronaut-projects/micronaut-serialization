package io.micronaut.json.generator.bean

import com.fasterxml.jackson.annotation.JsonAutoDetect
import io.micronaut.json.generator.symbol.ProblemReporter
import io.micronaut.serde.exceptions.SerdeException

class InlineBeanSerializerSymbolSpec extends AbstractBeanSerializerSpec {
    void "simple bean"() {
        given:
        def compiled = buildSerializer('''
package example;

class Test {
    public String a;
    private String b;
    
    Test() {}
    
    public String getB() {
        return b;
    }
    
    public void setB(String b) {
        this.b = b;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"a": "foo", "b": "bar"}')
        def testBean = compiled.newInstance()
        testBean.a = "foo"
        testBean.b = "bar"
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.a == "foo"
        deserialized.b == "bar"
        serialized == '{"a":"foo","b":"bar"}'
    }

    void "JsonProperty on field"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.JsonProperty;
class Test {
    @JsonProperty("foo")
    public String bar;
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": "42"}')
        def testBean = compiled.newInstance()
        testBean.bar = "42"
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'
    }

    void "JsonProperty on getter"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.JsonProperty;
class Test {
    private String bar;
    
    @JsonProperty("foo")
    public String getBar() {
        return bar;
    }
    
    public void setBar(String bar) {
        this.bar = bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": "42"}')
        def testBean = compiled.newInstance()
        testBean.bar = "42"
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'
    }

    void "JsonProperty on is-getter"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.JsonProperty;
class Test {
    private boolean bar;
    
    @JsonProperty("foo")
    public boolean isBar() {
        return bar;
    }
    
    public void setBar(boolean bar) {
        this.bar = bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": true}')
        def testBean = compiled.newInstance()
        testBean.bar = true
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == true
        serialized == '{"foo":true}'
    }

    void "JsonProperty on accessors without prefix"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.JsonProperty;
class Test {
    private String bar;
    
    @JsonProperty
    public String bar() {
        return bar;
    }
    
    @JsonProperty
    public void bar(String bar) {
        this.bar = bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"bar": "42"}')
        def testBean = compiled.newInstance()
        testBean.bar = "42"
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"bar":"42"}'
    }

    void "JsonCreator constructor"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    @JsonProperty("foo")
    private final String bar;
    
    @JsonCreator
    public Test(@JsonProperty("foo") String bar) {
        this.bar = bar;
    }
    
    public String getBar() {
        return bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": "42"}')
        def testBean = compiled.newInstance(["42"])
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'
    }

    void "JsonCreator with parameter names"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    public final String foo;
    public final String bar;
    
    @JsonCreator
    public Test(String foo, String bar) {
        this.foo = foo;
        this.bar = bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": "42", "bar": "56"}')

        expect:
        deserialized.foo == "42"
        deserialized.bar == "56"
    }

    void "implicit creator with parameter names"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    public final String foo;
    public final String bar;
    
    public Test(String foo, String bar) {
        this.foo = foo;
        this.bar = bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": "42", "bar": "56"}')

        expect:
        deserialized.foo == "42"
        deserialized.bar == "56"
    }

    void "JsonCreator with single parameter of same name"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    public final String foo;
    
    @JsonCreator
    public Test(String foo) {
        this.foo = foo;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": "42"}')

        expect:
        deserialized.foo == "42"
    }

    void "JsonCreator with single parameter of different name"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    public final String foo;
    
    @JsonCreator
    public Test(String bar) {
        this.foo = bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '"42"')

        expect:
        deserialized.foo == "42"
    }

    void "JsonCreator constructor with properties mode set"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    @JsonProperty("foo")
    private final String bar;
    
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Test(@JsonProperty("foo") String bar) {
        this.bar = bar;
    }
    
    public String getBar() {
        return bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": "42"}')
        def testBean = compiled.newInstance(["42"])
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'
    }

    void "JsonCreator static method"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    @JsonProperty("foo")
    private final String bar;
    
    private Test(String bar) {
        this.bar = bar;
    }
    
    @JsonCreator
    public static Test create(@JsonProperty("foo") String bar) {
        return new Test(bar);
    }
    
    public String getBar() {
        return bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": "42"}')
        def testBean = compiled.newInstance(["42"])
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'
    }

    void "JsonCreator no getter"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    private final String bar;
    
    @JsonCreator
    public Test(@JsonProperty("foo") String bar) {
        this.bar = bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": "42"}')
        def testBean = compiled.newInstance(["42"])
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{}'
    }

    @SuppressWarnings('JsonDuplicatePropertyKeys')
    void "duplicate property throws exception"() {
        given:
        def compiled = buildSerializer('''
package example;

class Test {
    public String foo;
}
''')

        when:
        deserializeFromString(compiled.serializer, '{"foo": "42", "foo": "43"}')

        then:
        thrown SerdeException
    }

    @SuppressWarnings('JsonDuplicatePropertyKeys')
    void "missing required property throws exception"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    String foo;
    
    @JsonCreator
    Test(@JsonProperty(value = "foo", required = true) String foo) {
        this.foo = foo;
    }
}
''')

        when:
        deserializeFromString(compiled.serializer, '{}')

        then:
        thrown SerdeException
    }

    void "missing required property throws exception, many variables"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    String v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, 
    v16, v17, v18, v19, v20, v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, 
    v32, v33, v34, v35, v36, v37, v38, v39, v40, v41, v42, v43, v44, v45, v46, v47, 
    v48, v49, v50, v51, v52, v53, v54, v55, v56, v57, v58, v59, v60, v61, v62, v63, 
    v64, v65, v66, v67, v68, v69, v70, v71, v72, v73, v74, v75, v76, v77, v78, v79;

    @JsonCreator
    public Test(
            @JsonProperty(value = "v7", required = true) String v7,
            @JsonProperty(value = "v14", required = true) String v14,
            @JsonProperty(value = "v75", required = true) String v75
    ) {
        this.v7 = v7;
        this.v14 = v14;
        this.v75 = v75;
    }
}
''')

        when:
        deserializeFromString(compiled.serializer, '{"v7": "42", "v75": "43"}')

        then:
        def e = thrown SerdeException
        // with the right message please
        e.message.contains("v14")
    }

    void "unknown properties lead to error"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
@JsonIgnoreProperties(ignoreUnknown = false)
class Test {
    String foo;
}
''')

        when:
        deserializeFromString(compiled.serializer, '{"foo": "1", "bar": "2"}')

        then:
        thrown SerdeException
    }

    void "unknown properties with proper annotation"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
@JsonIgnoreProperties(ignoreUnknown = true)
class Test {
    public String foo;
}
''')

        def des = deserializeFromString(compiled.serializer, '{"foo": "1", "bar": "2"}')

        expect:
        des.foo == "1"
    }

    void "unknown properties with proper annotation, complex"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
@JsonIgnoreProperties(ignoreUnknown = true)
class Test {
    public String foo;
}
''')

        def des = deserializeFromString(compiled.serializer, '{"foo": "1", "bar": [1, 2]}')

        expect:
        des.foo == "1"
    }

    void "json ignore"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
@JsonIgnoreProperties(ignoreUnknown = true)
class Test {
    @JsonIgnore public String foo;
    public String bar;
}
''')

        def des = deserializeFromString(compiled.serializer, '{"foo": "1", "bar": "2"}')
        def testBean = compiled.newInstance()
        testBean.foo = "1"
        testBean.bar = "2"
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        des.foo == null
        des.bar == "2"
        serialized == '{"bar":"2"}'
    }

    void "nullable"() {
        given:
        def compiled = buildSerializer('''
package example;

import io.micronaut.core.annotation.Nullable;
class Test {
    @Nullable String foo;
}
''')

        def des = deserializeFromString(compiled.serializer, '{"foo": null}')
        def testBean = compiled.newInstance()
        testBean.foo = null

        expect:
        des.foo == null
        serializeToString(compiled.serializer, testBean) == '{}'
    }

    void "nullable setter"() {
        given:
        def compiled = buildSerializer('''
package example;

import io.micronaut.core.annotation.Nullable;
class Test {
    private String foo;
    
    public void setFoo(@Nullable String foo) {
        this.foo = foo;
    }
}
''')

        expect:
        deserializeFromString(compiled.serializer, '{"foo": null}').foo == null
    }

    void "unwrapped"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
class Test {
    @JsonUnwrapped public Name name = new Name();
}

class Name {
    public String first;
    public String last;
}
''')

        def des = deserializeFromString(compiled.serializer, '{"first":"foo","last":"bar"}')
        def testBean = compiled.newInstance()
        testBean.name.first = "foo"
        testBean.name.last = "bar"

        expect:
        serializeToString(compiled.serializer, testBean) == '{"first":"foo","last":"bar"}'
        des.name != null
        des.name.first == "foo"
        des.name.last == "bar"
    }

    void "unwrapped duplicate property"() {
        given:
        def problemReporter = new ProblemReporter()
        buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
class Test {
    public String first;
    @JsonUnwrapped public Name name = new Name();
}

class Name {
    public String first;
    public String last;
}
''', problemReporter)

        expect:
        problemReporter.failed
        problemReporter.problems.any { it.message.contains('first') }
    }

    void "aliases"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
class Test {
    @JsonAlias("bar")
    public String foo;
}
''')

        expect:
        deserializeFromString(compiled.serializer, '{"foo": "42"}').foo == '42'
        deserializeFromString(compiled.serializer, '{"bar": "42"}').foo == '42'
    }

    void "value and creator"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    @JsonValue
    public final String foo;
    
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public Test(String foo) {
        this.foo = foo;
    }
}
''')
        def testBean = compiled.newInstance(['bar'])

        expect:
        deserializeFromString(compiled.serializer, '"bar"').foo == 'bar'
        serializeToString(compiled.serializer, testBean) == '"bar"'
    }

    void "creator with optional parameter"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    public final String foo;
    public final String bar;
    
    @JsonCreator
    public Test(@JsonProperty("foo") String foo, @JsonProperty(value = "bar", required = true) String bar) {
        this.foo = foo;
        this.bar = bar;
    }
}
''')

        expect:
        deserializeFromString(compiled.serializer, '{"foo":"123","bar":"456"}').foo == '123'
        deserializeFromString(compiled.serializer, '{"foo":"123","bar":"456"}').bar == '456'

        deserializeFromString(compiled.serializer, '{"bar":"456"}').foo == null
        deserializeFromString(compiled.serializer, '{"bar":"456"}').bar == '456'

        when:
        deserializeFromString(compiled.serializer, '{"foo":"123"}')
        then:
        thrown SerdeException
    }

    void "@JsonValue on toString"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    public final String foo;
    
    @JsonCreator
    public Test(String foo) {
        this.foo = foo;
    }
    
    @Override 
    @JsonValue
    public String toString() {
        return foo;
    }
}
''')
        def testBean = compiled.newInstance(['bar'])

        expect:
        serializeToString(compiled.serializer, testBean) == '"bar"'
    }

    void "optional"() {
        given:
        def compiled = buildSerializer('''
package example;

import java.util.Optional;
class Test {
    public Optional<String> foo = Optional.empty();
}
''')
        def testBean = compiled.newInstance()
        testBean.foo = Optional.of('bar')

        expect:
        deserializeFromString(compiled.serializer, '{"foo":"bar"}').foo.get() == 'bar'
        !deserializeFromString(compiled.serializer, '{"foo":null}').foo.isPresent()
        !deserializeFromString(compiled.serializer, '{}').foo.isPresent()
        serializeToString(compiled.serializer, testBean) == '{"foo":"bar"}'
    }

    void "optional nullable mix"() {
        given:
        def compiled = buildSerializer('''
package example;

import io.micronaut.core.annotation.Nullable;
import java.util.Optional;
class Test {
    @Nullable
    private String foo;
    
    public Optional<String> getFoo() {
        return Optional.ofNullable(foo);
    }
    
    public void setFoo(@Nullable String foo) {
        this.foo = foo;
    }
}
''')
        def testBean = compiled.newInstance()
        testBean.foo = 'bar'

        expect:
        deserializeFromString(compiled.serializer, '{"foo":"bar"}').foo.get() == 'bar'
        serializeToString(compiled.serializer, testBean) == '{"foo":"bar"}'
    }

    void "@JsonInclude"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.*;

class Test {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String alwaysString;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String nonNullString;
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public String nonAbsentString;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String nonEmptyString;
    
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String[] nonEmptyArray;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> nonEmptyList;
    
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> nonAbsentOptionalString;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<List<String>> nonEmptyOptionalList;
}
''')
        def with = compiled.newInstance()
        with.alwaysString = 'a';
        with.nonNullString = 'a';
        with.nonAbsentString = 'a';
        with.nonEmptyString = 'a';
        with.nonEmptyArray = ['a'];
        with.nonEmptyList = ['a'];
        with.nonAbsentOptionalString = Optional.of('a');
        with.nonEmptyOptionalList = Optional.of(['a']);

        def without = compiled.newInstance()
        without.alwaysString = null
        without.nonNullString = null
        without.nonAbsentString = null
        without.nonEmptyString = null
        without.nonEmptyArray = []
        without.nonEmptyList = []
        without.nonAbsentOptionalString = Optional.empty()
        without.nonEmptyOptionalList = Optional.of([])

        expect:
        serializeToString(compiled.serializer, with) == '{"alwaysString":"a","nonNullString":"a","nonAbsentString":"a","nonEmptyString":"a","nonEmptyArray":["a"],"nonEmptyList":["a"],"nonAbsentOptionalString":"a","nonEmptyOptionalList":["a"]}'
        serializeToString(compiled.serializer, without) == '{"alwaysString":null}'
    }

    void "missing properties are not overwritten"() {
        given:
        def compiled = buildSerializer('''
package example;

import io.micronaut.core.annotation.Nullable;
import java.util.Optional;
class Test {
    @Nullable
    public String foo = "bar";
}
''')

        expect:
        deserializeFromString(compiled.serializer, '{}').foo == 'bar'
        deserializeFromString(compiled.serializer, '{"foo":null}').foo == null
    }

    void "@JsonAnyGetter"() {
        given:
        def compiled = buildSerializer('''
package example;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
class Test {
    @JsonAnyGetter
    Map<String, String> anyGetter() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("foo", "bar");
        map.put("123", "456");
        return map;
    }
}
''')
        def test = compiled.newInstance()

        expect:
        serializeToString(compiled.serializer, test) == '{"foo":"bar","123":"456"}'
    }

    void "@JsonAnySetter"() {
        given:
        def compiled = buildSerializer('''
package example;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
class Test {
    private Map<String, String> anySetter = new HashMap<>();
    
    @JsonAnySetter
    void put(String key, String value) {
        anySetter.put(key, value);
    }
}
''')

        expect:
        deserializeFromString(compiled.serializer, '{"foo":"bar","123":"456"}').anySetter == ['foo': 'bar', '123': '456']
    }

    void 'unwrapped ignore unknown outer'() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
@JsonIgnoreProperties(ignoreUnknown = true)
class A {
    @JsonUnwrapped B b;
}
@JsonIgnoreProperties(ignoreUnknown = false)
class B {
}
''')

        expect:
        deserializeFromString(compiled.serializer, '{"foo":"bar"}') != null
    }

    void 'unwrapped ignore unknown inner'() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
@JsonIgnoreProperties(ignoreUnknown = false)
class A {
    @JsonUnwrapped B b;
}
@JsonIgnoreProperties(ignoreUnknown = true)
class B {
}
''')

        expect:
        deserializeFromString(compiled.serializer, '{"foo":"bar"}') != null
    }

    void 'unwrapped ignore unknown neither'() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
@JsonIgnoreProperties(ignoreUnknown = false)
class Outer {
    @JsonUnwrapped B b;
}
@JsonIgnoreProperties(ignoreUnknown = false)
class B {
}
''')

        when:
        deserializeFromString(compiled.serializer, '{"foo":"bar"}')

        then:
        def e = thrown SerdeException
        expect:'error should have the name of the outer class'
        e.message.contains("Outer")
    }

    void 'generic supertype'() {
        given:
        def compiled = buildSerializer('''
package example;

class Sub extends Sup<String> {
}
class Sup<T> {
    public T value;
}
''')

        expect:
        deserializeFromString(compiled.serializer, '{"value":"bar"}').value == 'bar'
    }

    void 'generic supertype mixed'() {
        given:
        def compiled = buildSerializer('''
package example;

class Subsub extends Sub<java.util.List<String>> {
}
class Sub<T> extends Sup<String> {
    public java.util.List<T> value2;
}
class Sup<T> {
    public T value;
}
''')

        expect:
        deserializeFromString(compiled.serializer, '{"value":"bar","value2":[["foo","bar"]]}').value == 'bar'
        deserializeFromString(compiled.serializer, '{"value":"bar","value2":[["foo","bar"]]}').value2 == [["foo", "bar"]]
    }

    void 'auto-detect visibility homogenous'() {
        given:
        def compiled = buildSerializer("""
package example;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.$configuredVisibility,
    isGetterVisibility = JsonAutoDetect.Visibility.$configuredVisibility,
    setterVisibility = JsonAutoDetect.Visibility.$configuredVisibility,
    fieldVisibility = JsonAutoDetect.Visibility.$configuredVisibility
)
class Test {
    $declaredVisibility String field = "unchanged";
    
    private String setterValue = "unchanged";
    
    $declaredVisibility void setSetter(String value) {
        this.setterValue = value;
    }
    
    private String getterValue = "unchanged";
    
    $declaredVisibility String getGetter() {
        return getterValue;
    }
    
    private String isGetterValue = "unchanged";
    
    $declaredVisibility String isIsGetter() {
        return isGetterValue;
    }
}
""")
        def instance = compiled.newInstance()
        instance.field = 'foo'
        instance.setterValue = 'qux'
        instance.getterValue = 'bar'
        instance.isGetterValue = 'baz'

        // json with all fields
        def fullJson = '{"field":"foo","getter":"bar","isGetter":"baz","setter":"qux"}'

        // json with only the serializable fields
        def expectedJson = appears ? '{"field":"foo","getter":"bar","isGetter":"baz"}' : '{}'

        expect:
        deserializeFromString(compiled.serializer, fullJson).field == appears ? 'foo' : 'unchanged'
        deserializeFromString(compiled.serializer, fullJson).setterValue == appears ? 'qux' : 'unchanged'
        deserializeFromString(compiled.serializer, fullJson).getterValue == 'unchanged' // never written
        deserializeFromString(compiled.serializer, fullJson).isGetterValue == 'unchanged' // never written

        serializeToString(compiled.serializer, instance) == expectedJson

        where:
        configuredVisibility         | declaredVisibility    | appears
        // hide private by default
        JsonAutoDetect.Visibility.DEFAULT | 'private'                  | false
        // hide package-private by default
        JsonAutoDetect.Visibility.DEFAULT | ''                         | false
        // various access modes
        // ANY is not supported (we can't access private fields)
        JsonAutoDetect.Visibility.NON_PRIVATE | 'private'                  | false
        JsonAutoDetect.Visibility.NON_PRIVATE | ''                         | true
        JsonAutoDetect.Visibility.NON_PRIVATE | 'protected'                | true
        JsonAutoDetect.Visibility.NON_PRIVATE | 'public'                   | true
        JsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC | 'private'                  | false
        JsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC | ''                         | false
        JsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC | 'protected'                | true
        JsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC | 'public'                   | true
        JsonAutoDetect.Visibility.PUBLIC_ONLY | 'private'                  | false
        JsonAutoDetect.Visibility.PUBLIC_ONLY | ''                         | false
        JsonAutoDetect.Visibility.PUBLIC_ONLY | 'protected'                | false
        JsonAutoDetect.Visibility.PUBLIC_ONLY | 'public'                   | true
        JsonAutoDetect.Visibility.NONE | 'private'                  | false
        JsonAutoDetect.Visibility.NONE | ''                         | false
        JsonAutoDetect.Visibility.NONE | 'protected'                | false
        JsonAutoDetect.Visibility.NONE | 'public'                   | false
    }

    void 'auto-detect visibility heterogenous'() {
        given:
        def compiled = buildSerializer("""
package example;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.$configuredGetterVisibility,
    isGetterVisibility = JsonAutoDetect.Visibility.$configuredIsGetterVisibility,
    setterVisibility = JsonAutoDetect.Visibility.$configuredSetterVisibility,
    fieldVisibility = JsonAutoDetect.Visibility.$configuredFieldVisibility
)
class Test {
    $declaredFieldVisibility String field = "unchanged";
    
    private String setterValue = "unchanged";
    
    $declaredSetterVisibility void setSetter(String value) {
        this.setterValue = value;
    }
    
    private String getterValue = "unchanged";
    
    $declaredGetterVisibility String getGetter() {
        return getterValue;
    }
    
    private String isGetterValue = "unchanged";
    
    $declaredIsGetterVisibility String isIsGetter() {
        return isGetterValue;
    }
}
""")
        def instance = compiled.newInstance()
        instance.field = 'foo'
        instance.setterValue = 'qux'
        instance.getterValue = 'bar'
        instance.isGetterValue = 'baz'

        // json with all fields
        def fullJson = '{"field":"foo","getter":"bar","isGetter":"baz","setter":"qux"}'

        // json with only the visible fields
        def expectedJson = '{'
        if (fieldAppears) expectedJson += '"field":"foo",'
        if (getterAppears) expectedJson += '"getter":"bar",'
        if (isGetterAppears) expectedJson += '"isGetter":"baz",'
        if (expectedJson.length() > 1) expectedJson = expectedJson.substring(0, expectedJson.length() - 1)
        expectedJson += '}'

        expect:
        deserializeFromString(compiled.serializer, fullJson).field == fieldAppears ? 'foo' : 'unchanged'
        deserializeFromString(compiled.serializer, fullJson).setterValue == setterAppears ? 'qux' : 'unchanged'
        deserializeFromString(compiled.serializer, fullJson).getterValue == 'unchanged' // never written
        deserializeFromString(compiled.serializer, fullJson).isGetterValue == 'unchanged' // never written

        serializeToString(compiled.serializer, instance) == expectedJson

        where:

        configuredFieldVisibility         | declaredFieldVisibility    | fieldAppears
        JsonAutoDetect.Visibility.NON_PRIVATE | ''                  | true
        JsonAutoDetect.Visibility.DEFAULT | ''                  | false
        JsonAutoDetect.Visibility.DEFAULT | ''                  | false
        JsonAutoDetect.Visibility.DEFAULT | ''                  | false
        __
        configuredGetterVisibility         | declaredGetterVisibility    | getterAppears
        JsonAutoDetect.Visibility.DEFAULT | ''                  | false
        JsonAutoDetect.Visibility.NON_PRIVATE | ''                  | true
        JsonAutoDetect.Visibility.DEFAULT | ''                  | false
        JsonAutoDetect.Visibility.DEFAULT | ''                  | false
        __
        configuredIsGetterVisibility         | declaredIsGetterVisibility    | isGetterAppears
        JsonAutoDetect.Visibility.DEFAULT | ''                  | false
        JsonAutoDetect.Visibility.DEFAULT | '' | false
        JsonAutoDetect.Visibility.NON_PRIVATE | '' | true
        JsonAutoDetect.Visibility.DEFAULT | '' | false
        __
        configuredSetterVisibility | declaredSetterVisibility | setterAppears
        JsonAutoDetect.Visibility.DEFAULT | '' | false
        JsonAutoDetect.Visibility.DEFAULT | '' | false
        JsonAutoDetect.Visibility.DEFAULT | '' | false
        JsonAutoDetect.Visibility.NON_PRIVATE | '' | true
    }

    void 'JsonIgnoreType'() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

class Test {
    public String foo;
    public Used used;
}
@JsonIgnoreType
class Used {
    public String bar;
}
''')
        def bean = compiled.newInstance()
        bean.foo = '42'
        bean.used = compiled.beanClass.classLoader.loadClass('example.Used').newInstance()
        bean.used.bar = '56'

        expect:
        deserializeFromString(compiled.serializer, '{"foo":"42","used":{"bar":"56"}}').used == null
        serializeToString(compiled.serializer, bean) == '{"foo":"42"}'
    }
}
