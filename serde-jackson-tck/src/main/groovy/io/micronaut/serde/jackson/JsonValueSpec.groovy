package io.micronaut.serde.jackson

abstract class JsonValueSpec extends JsonCompileSpec {

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
}
