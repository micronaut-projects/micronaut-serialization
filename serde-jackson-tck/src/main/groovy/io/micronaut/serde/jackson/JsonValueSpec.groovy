package io.micronaut.serde.jackson

abstract class JsonValueSpec extends JsonCompileSpec {

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
