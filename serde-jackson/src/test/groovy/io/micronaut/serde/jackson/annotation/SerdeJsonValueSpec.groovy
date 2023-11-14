package io.micronaut.serde.jackson.annotation


import io.micronaut.serde.jackson.JsonValueSpec
import spock.lang.PendingFeature

class SerdeJsonValueSpec extends JsonValueSpec {

    @PendingFeature(reason = "Align with Databind behaviour of @JsonInclude(JsonInclude.Include.NON_ABSENT)")
    void "test json value on toString() - NON_ABSENT"() {
        given:
        def context = buildContext('''
package jsonvalue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonInclude(JsonInclude.Include.NON_ABSENT)
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
        result == '{"test":null}'

        cleanup:
        context.close()
    }
}
