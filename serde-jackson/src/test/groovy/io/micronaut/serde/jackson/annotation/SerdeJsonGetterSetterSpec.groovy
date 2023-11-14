package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonGetterSetterSpec

class SerdeJsonGetterSetterSpec extends JsonGetterSetterSpec {

    void "test json any getter / setter - map parameter"() {
        given:
            def context = buildContext('''
package jsongetter;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonGetter;
import java.util.*;

@Serdeable
class Test {
    private String name;
    private Map<String, Object> attributes = new HashMap<>();

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @JsonAnyGetter
    public Map<String, Object> attrs() {
        return attributes;
    }

    @JsonAnySetter
    void attrs(Map<String, Object> map) {
        this.attributes = map;
    }
}
''')
        when:
            def bean = newInstance(context, 'jsongetter.Test', [name: "Fred"])
            bean.attributes = [foo:'bar', age: 10]
            def result = writeJson(jsonMapper, bean)

        then:
            result == '{"name":"Fred","foo":"bar","age":10}'

        when:
            bean = jsonMapper.readValue(result, argumentOf(context, 'jsongetter.Test'))

        then:
            bean.name == "Fred"
            bean.attributes == [foo:'bar', age:10]

        when:
            bean = jsonMapper.readValue('{"name":"Fred","age":45,"wgt":100}', argumentOf(context, 'jsongetter.Test'))

        then:
            bean.name == "Fred"
            bean.attributes == [age:45, wgt:100]

        cleanup:
            context.close()
    }

    void "test json any getter / setter - records"() {
        given:
            def context = buildContext('''
package jsongetterrecord;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonGetter;
import java.util.*;

@Serdeable
record Test(
    String name,
    @JsonAnyGetter
    @JsonAnySetter
    Map<String, Object> attributes) {
}
''')

            def argument = argumentOf(context, 'jsongetterrecord.Test')

        when:
            def bean = newInstance(context, 'jsongetterrecord.Test', "Fred", [foo:'bar', age: 10])
            def result = writeJson(jsonMapper, bean)

        then:
            result == '{"name":"Fred","foo":"bar","age":10}'

        when:
            bean = jsonMapper.readValue(result, argument)

        then:
            bean.name == "Fred"
            bean.attributes == [foo:'bar', age:10]

        when:
            bean = jsonMapper.readValue('{"name":"Fred","age":45,"wgt":100}', argument)

        then:
            bean.name == "Fred"
            bean.attributes == [age:45, wgt:100]

        cleanup:
            context.close()
    }

    void "test json any getter / setter - records fail on invalid component"() {
        when:
        buildBeanIntrospection('jsongetterrecord.Test', '''
package jsongetterrecord;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonGetter;
import java.util.*;

@Serdeable
record Test(
    String name,
    @JsonAnyGetter
    @JsonAnySetter
    String attributes) {
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("A field annotated with AnyGetter must be a Map")
    }
}
