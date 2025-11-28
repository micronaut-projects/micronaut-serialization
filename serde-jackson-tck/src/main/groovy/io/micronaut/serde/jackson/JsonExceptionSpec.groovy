/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.jackson

abstract class JsonExceptionSpec extends JsonCompileSpec {

    def "test case sensitive errors"() {
        given:
            def compiled = buildContext('''
package enumtest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public Foo data = Foo.BAZ;
}

@Serdeable
enum Foo {
  BAR("br"),
  BAZ("bz");

  private final String value;

  Foo(String value) {
    this.value = value;
  }
}
''')
            def argument = argumentOf(compiled, 'enumtest.Test')

        when:
            jsonMapper.readValue('{"data":"baz"}', argument)
        then:
            def e = thrown(Exception)
            e.message.contains("""Cannot deserialize value of type `enumtest.Foo` from String "baz": not one of the values accepted for Enum class: [BAR, BAZ]""")
                    || e.message.contains("Cannot deserialize value of type `enumtest.Foo` due to: Expected one of [BAR, BAZ] but was 'baz'")
            getPath(e) == """enumtest.Test["data"]"""

        cleanup:
            compiled.close()
    }

    void "unknown enum - case"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

enum Foobar {
    Abc
}

@Serdeable
record Test(Foobar foobar) {
}
''')

        when:
            jsonMapper.readValue("""{ "foobar": "ABC" }""", typeUnderTest)

        then:
            def e = thrown(Exception)
            e.message.contains("""Cannot deserialize value of type `example.Foobar` from String "ABC": not one of the values accepted for Enum class: [Abc]""")
                    || e.message.contains("Cannot deserialize value of type `example.Foobar` due to: Expected one of [Abc] but was 'ABC'")
            getPath(e) == """example.Test["foobar"]"""

        cleanup:
            context.close()
    }

    void "unknown enum - case - JsonValue method"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
enum Foobar {
    AbC;

  @JsonValue
  public String toString() {
    return name().toLowerCase();
  }
}

@Serdeable
record Test(Foobar foobar) {
}
''')

        when:
            jsonMapper.readValue("""{ "foobar": "ABC" }""", typeUnderTest)

        then:
            def e = thrown(Exception)
            e.message.contains("""Cannot deserialize value of type `example.Foobar` from String "ABC": not one of the values accepted for Enum class: [abc]""")
                    || e.message.contains("Cannot deserialize value of type `example.Foobar` due to: Expected one of [abc] but was 'ABC'")
            getPath(e) == """example.Test["foobar"]"""

        cleanup:
            context.close()
    }

    void "unknown enum - case - JsonValue field"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
enum Foobar {
    AbC;

    @JsonValue
    public final String value;

    Foobar() {
        this.value = name().toLowerCase();
    }

}

@Serdeable
record Test(Foobar foobar) {
}
''')

        when:
            jsonMapper.readValue("""{ "foobar": "ABC" }""", typeUnderTest)

        then:
            def e = thrown(Exception)
            e.message.contains("""Cannot deserialize value of type `example.Foobar` from String "ABC": not one of the values accepted for Enum class: [abc]""")
                    || e.message.contains("Cannot deserialize value of type `example.Foobar` due to: Expected one of [abc] but was 'ABC'")
            getPath(e) == """example.Test["foobar"]"""

        cleanup:
            context.close()
    }

    void "unknown enum"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

enum Foobar {
    A, B, C
}

@Serdeable
record Test(Foobar foobar) {
}
''')

        when:
            jsonMapper.readValue("""{ "foobar": "D" }""", typeUnderTest)

        then:
            def e = thrown(Exception)
            e.message.contains("""Cannot deserialize value of type `example.Foobar` from String "D": not one of the values accepted for Enum class: [A, B, C]""")
                    || e.message.contains("Cannot deserialize value of type `example.Foobar` due to: Expected one of [A, B, C] but was 'D'")
            getPath(e) == """example.Test["foobar"]"""

        cleanup:
            context.close()

    }

    void "unknown enum - nested"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

enum Foobar {
    A, B, C
}

@Serdeable
record Nested(Foobar foobar) {
}

@Serdeable
record Test(Nested nested) {
}
''')

        when:
            jsonMapper.readValue("""{ "nested": {"foobar": "D"} }""", typeUnderTest)

        then:
            def e = thrown(Exception)
            e.message.contains("""Cannot deserialize value of type `example.Foobar` from String "D": not one of the values accepted for Enum class: [A, B, C]""")
                    || e.message.contains("Cannot deserialize value of type `example.Foobar` due to: Expected one of [A, B, C] but was 'D'")
            getPath(e) == """example.Test["nested"]->example.Nested["foobar"]"""

        cleanup:
            context.close()

    }

    void "unknown property - record"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Test(String stringA, String stringB) {
}
''')

        when:
            jsonMapper.readValue(json, typeUnderTest)

        then:
            def e = thrown(Exception)
            e.message.contains("""Unrecognized field "unknownProperty" """) || e.message.contains("Unknown property [unknownProperty]")
            getPath(e) == """example.Test["unknownProperty"]"""

        cleanup:
            context.close()

        where:
            json << [
                    """{ "stringA": "value", "stringB": "value2", "unknownProperty": "value3" }""",
                    """{ "unknownProperty": "value3", "stringA": "value", "stringB": "value2" }""",
            ]
    }

    void "unknown property - nested record"() {
        given:
            def context = buildContext('example.Outer', '''
package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Outer(Inner inner) {
}

@Serdeable
record Inner(String stringA, String stringB) {
}
''')

        when:
            jsonMapper.readValue(json, typeUnderTest)

        then:
            def e = thrown(Exception)
            e.message.contains("""Unrecognized field "unknownProperty" """) || e.message.contains("Unknown property [unknownProperty]")
            getPath(e) == """example.Outer["inner"]->example.Inner["unknownProperty"]"""

        cleanup:
            context.close()

        where:
            json << [
                    """{ "inner": { "stringA": "value", "stringB": "value2", "unknownProperty": "value3" } }""",
                    """{ "inner": { "unknownProperty": "value3", "stringA": "value", "stringB": "value2" } }""",
            ]
    }

    void "unknown property - nested bean"() {
        given:
            def context = buildContext('example.Outer', '''
package example;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

@Serdeable
final class Outer {
    private Inner inner;

    public Inner getInner() {
        return inner;
    }

    public void setInner(Inner inner) {
        this.inner = inner;
    }
}

@Serdeable
record Inner(String stringA, String stringB) {
}
''')

        when:
            jsonMapper.readValue(json, typeUnderTest)

        then:
            def e = thrown(Exception)
            e.message.contains("""Unrecognized field "unknownProperty" """) || e.message.contains("Unknown property [unknownProperty]")
            getPath(e) == """example.Outer["inner"]->example.Inner["unknownProperty"]"""

        cleanup:
            context.close()

        where:
            json << [
                    """{ "inner": { "stringA": "value", "stringB": "value2", "unknownProperty": "value3" } }""",
                    """{ "inner": { "unknownProperty": "value3", "stringA": "value", "stringB": "value2" } }""",
            ]
    }

    void "unknown property - nested mixed bean"() {
        given:
            def context = buildContext('example.Outer', '''
package example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

@Serdeable
final class Outer {
    private final String eee;
    private Inner inner;

    @JsonCreator
    Outer(@Nullable @JsonProperty("eee") String eee) {
        this.eee = eee;
    }

    public Inner getInner() {
        return inner;
    }

    public void setInner(Inner inner) {
        this.inner = inner;
    }
}

@Serdeable
record Inner(String stringA, String stringB) {
}
''')

        when:
            jsonMapper.readValue(json, typeUnderTest)

        then:
            def e = thrown(Exception)
            e.message.contains("""Unrecognized field "unknownProperty" """) || e.message.contains("Unknown property [unknownProperty]")
            getPath(e) == """example.Outer["inner"]->example.Inner["unknownProperty"]"""

        cleanup:
            context.close()

        where:
            json << [
                    """{ "inner": { "stringA": "value", "stringB": "value2", "unknownProperty": "value3" } }""",
                    """{ "inner": { "unknownProperty": "value3", "stringA": "value", "stringB": "value2" } }""",
            ]
    }

    void "unknown property - property bean"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

@Serdeable
final class Test {
    private String stringA;
    private String stringB;

    public String getStringA() {
        return stringA;
    }

    public void setStringA(String stringA) {
        this.stringA = stringA;
    }

    public String getStringB() {
        return stringB;
    }

    public void setStringB(String stringB) {
        this.stringB = stringB;
    }
}
''')

        when:
            jsonMapper.readValue(json, typeUnderTest)

        then:
            def e = thrown(Exception)
            e.message.contains("""Unrecognized field "unknownProperty" """) || e.message.contains("Unknown property [unknownProperty]")
            getPath(e) == """example.Test["unknownProperty"]"""

        cleanup:
            context.close()

        where:
            json << [
                    """{ "stringA": "value", "stringB": "value2", "unknownProperty": "value3" }""",
                    """{ "unknownProperty": "value3", "stringA": "value", "stringB": "value2" }""",
            ]
    }

    void "unknown property - property bean mixed"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

@Serdeable
final class Test {
    private final String stringA;
    private String stringB;

    @JsonCreator
    Test(@JsonProperty("stringA") String stringA) {
        this.stringA = stringA;
    }

    public String getStringB() {
        return stringB;
    }

    public void setStringB(String stringB) {
        this.stringB = stringB;
    }
}
''')

        when:
            jsonMapper.readValue(json, typeUnderTest)

        then:
            def e = thrown(Exception)
            e.message.contains("""Unrecognized field "unknownProperty" """) || e.message.contains("Unknown property [unknownProperty]")
            getPath(e) == """example.Test["unknownProperty"]"""

        cleanup:
            context.close()

        where:
            json << [
                    """{ "stringA": "value", "stringB": "value2", "unknownProperty": "value3" }""",
                    """{ "unknownProperty": "value3", "stringA": "value", "stringB": "value2" }""",

            ]
    }

    void "duplicate property - record"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Test(String stringA, String stringB) {
}
''')

        when:
            jsonMapper.readValue(json, typeUnderTest)

        then:
            def e = thrown(Exception)
            e.message.contains("""Duplicate field 'stringA'""") || e.message.contains("Duplicate property [stringA]")
            def path = getPath(e)
            path == "<unknown>" || path == """example.Test["stringA"]"""

        cleanup:
            context.close()

        where:
            json << [
                    """{ "stringA": "valuea", "stringB": "valueb", "stringA": "valuea" }""",
                    """{ "stringB": "value", "stringA": "first", "stringA": "second" }""",
                    """{ "stringA": "valueA", "stringA": "valueA", "stringB": "second" }"""
            ]
    }

    void "duplicate property - property bean"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

@Serdeable
final class Test {
    private String stringA;
    private String stringB;

    public String getStringA() {
        return stringA;
    }

    public void setStringA(String stringA) {
        this.stringA = stringA;
    }

    public String getStringB() {
        return stringB;
    }

    public void setStringB(String stringB) {
        this.stringB = stringB;
    }
}
''')

        when:
            jsonMapper.readValue(json, typeUnderTest)

        then:
            def e = thrown(Exception)
            e.message.contains("""Duplicate field 'stringA'""") || e.message.contains("Duplicate property [stringA]")
            def path = getPath(e)
            path == "<unknown>" || path == """example.Test["stringA"]"""

        cleanup:
            context.close()

        where:
            json << [
                    """{ "stringA": "valuea", "stringB": "valueb", "stringA": "valuea" }""",
                    """{ "stringB": "value", "stringA": "first", "stringA": "second" }""",
                    """{ "stringA": "valueA", "stringA": "valueA", "stringB": "second" }"""
            ]
    }

    void "duplicate property - property bean mixed"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

@Serdeable
final class Test {
    private final String stringA;
    private String stringB;

    @JsonCreator
    Test(@JsonProperty("stringA") String stringA) {
        this.stringA = stringA;
    }

    public String getStringB() {
        return stringB;
    }

    public void setStringB(String stringB) {
        this.stringB = stringB;
    }
}
''')

        when:
            jsonMapper.readValue(json, typeUnderTest)

        then:
            def e = thrown(Exception)
            e.message.contains("""Duplicate field 'stringA'""") || e.message.contains("Duplicate property [stringA]")
            def path = getPath(e)
            path == "<unknown>" || path == """example.Test["stringA"]"""

        cleanup:
            context.close()

        where:
            json << [
                    """{ "stringA": "valuea", "stringB": "valueb", "stringA": "valuea" }""",
                    """{ "stringB": "value", "stringA": "first", "stringA": "second" }""",
                    """{ "stringA": "valueA", "stringA": "valueA", "stringB": "second" }"""
            ]
    }

    void "enum"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public Test2 foo;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test2 {
    public MyEnum bar;
}

enum MyEnum {
    A, B, C
}
''')

        when:
            jsonMapper.readValue('{"foo": {"bar": "xyz"}}}', typeUnderTest)

        then:
            def e = thrown(Exception)
            getPath(e) == """example.Test["foo"]->example.Test2["bar"]"""

        cleanup:
            context.close()
    }

    void "any setter"() {
        given:
            def context = buildContext('example.Test', '''
package example;


import java.util.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

@Serdeable
class Test {
    private Map<String, Test2> anySetter = new HashMap<>();

    @JsonAnySetter
    void put(String key, Test2 value) {
        anySetter.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> myGetter() {
        throw new IllegalStateException("Bam!");
    }

}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test2 {
    public int bar;
}
''')

        when:
            jsonMapper.readValue('{"foo": {"bar": "xyz"}}}', typeUnderTest)

        then:
            def e = thrown(Exception)
            getPath(e) == """example.Test["foo"]->example.Test2["bar"]"""

        when:
            def bean = jsonMapper.readValue('{"foo": {"bar": 123}}}', typeUnderTest)

        then:
            bean

        when:
            jsonMapper.writeValueAsString(bean)
        then:
            def e2 = thrown(Exception)
            def path = getPath(e2)
            path == """example.Test["[anySetter]"]""" || path == """example.Test["myGetter"]"""

        cleanup:
            context.close()
    }

    void "property path"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    public Test2 foo;

    public Test2 getFoo() {
        return foo;
    }

    public void setFoo(Test2 foo) {
        this.foo = foo;
    }
}

@Serdeable
class Test2 {
    public int bar;

    public int getBar() {
        throw new IllegalStateException("Bam!");
    }

    public void setBar(int bar) {
        this.bar = bar;
    }
}
''')

        when:
            jsonMapper.readValue('{"foo": {"bar": "xyz"}}}', typeUnderTest)

        then:
            def e = thrown(Exception)
            getPath(e) == """example.Test["foo"]->example.Test2["bar"]"""

        when:
            def bean = jsonMapper.readValue('{"foo": {"bar": 123}}}', typeUnderTest)

        then:
            bean

        when:
            jsonMapper.writeValueAsString(bean)
        then:
            def e2 = thrown(Exception)
            getPath(e2) == """example.Test["foo"]->example.Test2["bar"]"""

        cleanup:
            context.close()
    }

    void "property path set"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Set;
import java.util.LinkedHashSet;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonDeserialize(as=LinkedHashSet.class)
    public Set<Test2> foo;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test2 {
    public int bar;
}
''')
        when:
            jsonMapper.readValue('{"foo": [{"bar": "xyz"}]}}', typeUnderTest)

        then:
            def e = thrown(Exception)
            getPath(e) == """example.Test["foo"]->java.util.LinkedHashSet[0]->example.Test2["bar"]"""

        cleanup:
            context.close()
    }

    void "property path list"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
class Test {
    public List<Test2> foo;

    public List<Test2> getFoo() {
        return foo;
    }

    public void setFoo(List<Test2> foo) {
        this.foo = foo;
    }
}

@Serdeable
class Test2 {
    public int bar;

    public int getBar() {
        throw new IllegalStateException("Bam!");
    }

    public void setBar(int bar) {
        this.bar = bar;
    }
}
''')

        when:
            jsonMapper.readValue('{"foo": [{"bar": "xyz"}]}}', typeUnderTest)

        then:
            def e = thrown(Exception)
            getPath(e) == """example.Test["foo"]->java.util.ArrayList[0]->example.Test2["bar"]"""

        when:
            def bean = jsonMapper.readValue('{"foo": [{"bar": 123}]}}', typeUnderTest)

        then:
            bean

        when:
            jsonMapper.writeValueAsString(bean)

        then:
            def ee = thrown(Exception)
            getPath(ee) == """example.Test["foo"]->java.util.ArrayList[0]->example.Test2["bar"]"""

        cleanup:
            context.close()
    }

    void "property path array"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    public Test2[] foo;

    public Test2[] getFoo() {
        return foo;
    }

    public void setFoo(Test2[] foo) {
        this.foo = foo;
    }
}

@Serdeable
class Test2 {
    public int bar;

    public int getBar() {
        throw new IllegalStateException("Bam!");
    }

    public void setBar(int bar) {
        this.bar = bar;
    }
}
''')

        when:
            jsonMapper.readValue('{"foo": [{"bar": "xyz"}]}}', typeUnderTest)

        then:
            def e = thrown(Exception)
            def path = getPath(e)
            // Jackson always references the array as java.lang.Object[]
            path == """example.Test["foo"]->java.lang.Object[][0]->example.Test2["bar"]""" || path == """example.Test["foo"]->example.Test2[][0]->example.Test2["bar"]"""

        when:
            def bean = jsonMapper.readValue('{"foo": [{"bar": 123}]}}', typeUnderTest)

        then:
            bean

        when:
            jsonMapper.writeValueAsString(bean)

        then:
            def ee = thrown(Exception)
            getPath(ee) == """example.Test["foo"]->example.Test2[0]->example.Test2["bar"]"""

        cleanup:
            context.close()
    }

    void "property path map"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
class Test {
    public Map<String, Test2> foo;

    public Map<String, Test2> getFoo() {
        return foo;
    }

    public void setFoo(Map<String, Test2> foo) {
        this.foo = foo;
    }
}

@Serdeable
class Test2 {
    public int bar;

    public int getBar() {
        throw new IllegalStateException("Bam!");
    }

    public void setBar(int bar) {
        this.bar = bar;
    }
}
''')

        when:
            jsonMapper.readValue('{"foo": {"xxx": {"bar": "xyz"}}}}', typeUnderTest)

        then:
            def e = thrown(Exception)
            getPath(e) == """example.Test["foo"]->java.util.LinkedHashMap["xxx"]->example.Test2["bar"]"""

        when:
            def bean = jsonMapper.readValue('{"foo": {"xxx": {"bar": 123}}}}', typeUnderTest)

        then:
            bean

        when:
            jsonMapper.writeValueAsString(bean)

        then:
            def ee = thrown(Exception)
            getPath(ee) == """example.Test["foo"]->java.util.LinkedHashMap["xxx"]->example.Test2["bar"]"""

        cleanup:
            context.close()
    }

    abstract String getPath(Exception e)

}
