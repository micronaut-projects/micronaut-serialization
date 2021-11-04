package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonCompileSpec

class JsonGetterSetterSpec extends JsonCompileSpec {

    void "test json getter / setter"() {
        given:
        def context = buildContext('''
package jsongetter;

import com.fasterxml.jackson.annotation.JsonSetter;import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonGetter;

@Serdeable
class Test {
    private int age = 30;
    private int weight = 75;
    private String name;
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    @JsonGetter
    int age() {
        return age;
    }
    
    @JsonGetter("wgt")
    int weight() {
        return weight;
    }
    
    @JsonSetter
    void age(int age) {
        this.age = age;
    }
    
    @JsonSetter("wgt")
    Test weight(int weight) {
        this.weight = weight;
        return this;
    }
}
''')
        when:
        def bean = newInstance(context, 'jsongetter.Test', [name: "Fred"])
        def result = writeJson(jsonMapper, bean)

        then:
        result == '{"name":"Fred","age":30,"wgt":75}'

        when:
        bean = jsonMapper.readValue(result, argumentOf(context, 'jsongetter.Test'))

        then:
        bean.name == "Fred"
        bean.age() == 30
        bean.weight() == 75

        when:
        bean = jsonMapper.readValue('{"name":"Fred","age":45,"wgt":100}', argumentOf(context, 'jsongetter.Test'))

        then:
        bean.name == "Fred"
        bean.age() == 45
        bean.weight() == 100

        cleanup:
        context.close()
    }
}
