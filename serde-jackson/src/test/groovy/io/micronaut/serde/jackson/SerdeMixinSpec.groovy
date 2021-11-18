package io.micronaut.serde.jackson


import io.micronaut.serde.exceptions.SerdeException

class SerdeMixinSpec extends JsonCompileSpec {

    void "test mixin serde - both"() {
        given:
        def context = buildContext('mixintest.Test','''
package mixintest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.SerdeMixin;

@SerdeMixin(Test.class)
class TestMixin {}

public class Test {
    @JsonProperty("n")
    private String name;
    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
}
''', [name:'test'])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"n":"test"}'
        jsonMapper.readValue('{"n":"test"}', typeUnderTest).name == 'test'
    }

    void "test mixin serde - ser only"() {
        given:
        def context = buildContext('mixintest.Test','''
package mixintest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.SerdeMixin;
import io.micronaut.serde.annotation.Serdeable;

@SerdeMixin(
    value = Test.class,
    deser = @Serdeable.Deserializable(enabled = false)
)
class TestMixin {}

public class Test {
    @JsonProperty("n")
    private String name;
    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
}
''', [name:'test'])



        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"n":"test"}'

        when:
        jsonMapper.readValue('{"n":"test"}', typeUnderTest).name == 'test'

        then:
        def e = thrown(SerdeException)
    }
}
