package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonCompileSpec

class JsonSerializeDeserializeSpec extends JsonCompileSpec {

    void 'test json serialize/deserialize as'() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.serde.annotation.Serdeable;

@JsonSerialize(as = TestImpl.class)
@JsonDeserialize(as = TestImpl.class)
interface Test {
    String getValue();
}

@Serdeable
class TestImpl implements Test {
    private final String value;
    TestImpl(String value) {
        this.value = value;
    }
    
    @Override
    public String getValue() {
        return value;
    }
}
""")

        when:
        def result = jsonMapper.readValue('{"value":"test"}', typeUnderTest)

        then:
        result.getClass().name == 'test.TestImpl'
        result.value == 'test'

        when:
        def json = writeJson(jsonMapper, result)

        then:
        json == '{"value":"test"}'
    }
}
