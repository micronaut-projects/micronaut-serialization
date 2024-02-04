package io.micronaut.serde.jackson

import io.micronaut.serde.jackson.jsonvalue.JdkVersion
import spock.lang.PendingFeature

abstract class JsonValueSpec extends JsonCompileSpec {

    void "enum @JsonValue property"() throws IOException {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Foo {
    private final MyEnum myEnum;

    @JsonCreator
    Foo(@JsonProperty("myEnum") MyEnum myEnum) {
        this.myEnum = myEnum;
    }

    public MyEnum getMyEnum() {
        return myEnum;
    }
}

@Serdeable
enum MyEnum {

    VALUE1("value1"),
    VALUE2("value2"),
    VALUE3("value3");

    private final String value;

    MyEnum(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
''')
            def enumValue2 = context.classLoader.loadClass('example.MyEnum').VALUE2
            def testBean = newInstance(context, 'example.Foo', enumValue2)
        when:
            String json = jsonMapper.writeValueAsString(testBean)
        then:
            '{"myEnum":"value2"}' == json

        when:
            def foo = jsonMapper.readValue(json, testBean.class)

        then:
            foo.myEnum == enumValue2
    }

    void "@JsonValue on constructor"() throws IOException {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.jackson.jsonvalue.JdkVersion;

@Serdeable
class Foo {
    private final JdkVersion jdk;

    @JsonCreator
    Foo(@JsonProperty("jdk") JdkVersion jdk) {
        this.jdk = jdk;
    }

    public JdkVersion getJdk() {
        return jdk;
    }
}
''')
            def testBean = newInstance(context, 'example.Foo', JdkVersion.JDK_17)
        when:
            String json = jsonMapper.writeValueAsString(testBean)
        then:
            '{"jdk":"JDK_17"}' == json

        when:
            def foo = jsonMapper.readValue('{"jdk":"JDK_17"}', testBean.class)

        then:
            foo.jdk == JdkVersion.JDK_17
    }

    void "@JsonValue on constructor, value with constructor"() throws IOException {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Foo {
    private final JdkVersion jdk;

    @JsonCreator
    Foo(@JsonProperty("jdk") JdkVersion jdk) {
        this.jdk = jdk;
    }

    public JdkVersion getJdk() {
        return jdk;
    }
}

@Serdeable
final class JdkVersion {

    private final String name;

    @JsonCreator
    public JdkVersion(String name) {
        this.name = name;
    }

    @JsonValue
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name();
    }
}
''')
            def val = newInstance(context, 'example.JdkVersion', 'JDK_17')
            def testBean = newInstance(context, 'example.Foo', val)
        when:
            String json = jsonMapper.writeValueAsString(testBean)
        then:
            '{"jdk":"JDK_17"}' == json

        when:
            def foo = jsonMapper.readValue('{"jdk":"JDK_17"}', testBean.class)

        then:
            foo.jdk.name() == val.name()
    }

    void "@JsonValue on property"() throws IOException {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.jackson.jsonvalue.JdkVersion;

@Serdeable
class Foo {
    private JdkVersion jdk;

    public void setJdk(JdkVersion jdk) {
        this.jdk = jdk;
    }

    public JdkVersion getJdk() {
        return jdk;
    }
}
''')
            def testBean = newInstance(context, 'example.Foo')
            testBean.jdk = JdkVersion.JDK_17
        when:
            String json = jsonMapper.writeValueAsString(testBean)
        then:
            '{"jdk":"JDK_17"}' == json

        when:
            def foo = jsonMapper.readValue('{"jdk":"JDK_17"}', testBean.class)

        then:
            foo.jdk == JdkVersion.JDK_17
    }

    void "@JsonValue on toString"() {
        given:
        def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.*;
@io.micronaut.serde.annotation.Serdeable
class Test {
    public final String foo;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
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
        def testBean = newInstance(context, 'example.Test', 'bar')

        expect:
        serializeToString(jsonMapper, testBean) == '"bar"'

        cleanup:
        context.close()
    }

    void "value and creator"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonValue
    public final String foo;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public Test(String foo) {
        this.foo = foo;
    }
}
''')
        def testBean = newInstance(context, 'example.Test', 'bar')

        expect:
        deserializeFromString(jsonMapper, testBean.getClass(), '"bar"').foo == 'bar'
        serializeToString(jsonMapper, testBean) == '"bar"'
    }

  def "test serialize / deserialize interface impl"() {
        given:
        def context = buildContext('itfeimpl.Test', '''
package itfeimpl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private HttpStatusInfo info = new HttpStatusInfoImpl(200);
    public void setInfo(itfeimpl.HttpStatusInfo info) {
        this.info = info;
    }
    public itfeimpl.HttpStatusInfo getInfo() {
        return info;
    }
}

@JsonDeserialize(as = HttpStatusInfoImpl.class)
interface HttpStatusInfo {
    int code();
}

@Serdeable
class HttpStatusInfoImpl implements HttpStatusInfo {
    private final int code;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    HttpStatusInfoImpl(int code) {
        this.code = code;
    }

    @JsonValue
    @Override public int code() {
       return code;
    }
}
''')
        when:
        def result = jsonMapper.writeValueAsString(typeUnderTest.type.newInstance())

        then:
        result == '{"info":200}'

        when:
        def bean = jsonMapper.readValue(result, typeUnderTest)

        then:
        bean.info.code() == 200

        cleanup:
        context.close()
    }

    void "test json value on toString()"() {
        given:
        def context = buildContext('''
package jsonvalue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Other {
    private Test test;
    public void setTest(jsonvalue.Test test) {
        this.test = test;
    }
    public jsonvalue.Test getTest() {
        return test;
    }
}
@Serdeable
class Test {
    private final String name;
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    Test(String n) {
        this.name = n;
    }

    @JsonValue
    public String toString() {
        return name;
    }
}
''')
        def o = newInstance(context, 'jsonvalue.Other')
        def t = newInstance(context, 'jsonvalue.Test', "test")
        o.test = t

        when:
        def result = writeJson(jsonMapper, o)

        then:"JsonValue triggers toString()"
        result == '{"test":"test"}'

        when:
        def read = jsonMapper.readValue(result, argumentOf(context, 'jsonvalue.Other'))

        then:
        read.test.name == 'test'

        when:'null is returned'
        o.test = newInstance(context, 'jsonvalue.Test', (Object[]) null)
        result = writeJson(jsonMapper, o)

        then:
        result == '{}'


        cleanup:
        context.close()
    }

    void "test json value on getter"() {
        given:
        def context = buildContext('''
package jsonvalue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Other {
    private Test test;
    public void setTest(jsonvalue.Test test) {
        this.test = test;
    }
    public jsonvalue.Test getTest() {
        return test;
    }
}
@Serdeable
class Test {
    private final String name;

    private Test(String n) {
        this.name = n;
    }
    @JsonValue
    public String getName() {
        return name;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static Test valueOf(String name) {
        return new Test(name);
    }
}
''')
        def o = newInstance(context, 'jsonvalue.Other')
        def tClass = context.classLoader.loadClass('jsonvalue.Test')
        o.test = tClass.valueOf("test")

        when:
        def result = writeJson(jsonMapper, o)

        then:"JsonValue triggers toString()"
        result == '{"test":"test"}'

        when:
        def read = jsonMapper.readValue(result, argumentOf(context, 'jsonvalue.Other'))

        then:
        read.test.name == 'test'

        cleanup:
        context.close()
    }

    @PendingFeature(reason = "JsonProperty on enum constant level not supported")
    void "JsonProperty on enum"() {
        given:
        def context = buildContext('''
package example;

import io.micronaut.serde.annotation.Serdeable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@Serdeable
class Foo {
    private final MyEnum myEnum;

    @JsonCreator
    Foo(@JsonProperty("myEnum") MyEnum myEnum) {
        this.myEnum = myEnum;
    }

    public MyEnum getMyEnum() {
        return myEnum;
    }
}

@Serdeable
enum MyEnum {

    @JsonProperty("v1")
    VALUE1("value1"),
    @JsonProperty("v2")
    VALUE2("value2"),
    @JsonProperty("v3")
    VALUE3("value3");

    private final String value;

    MyEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}''')
        def enumValue2 = context.classLoader.loadClass('example.MyEnum').VALUE2
        def testBean = newInstance(context, 'example.Foo', enumValue2)
        when:
        String json = jsonMapper.writeValueAsString(testBean)
        then:
        '{"myEnum":"v2"}' == json

        when:
        def foo = jsonMapper.readValue(json, testBean.class)

        then:
        foo.myEnum == enumValue2
    }

}
