package io.micronaut.serde.jackson

abstract class JsonGetterSetterSpec extends JsonCompileSpec {

    void "test json getter / setter"() {
        given:
        def context = buildContext('''
package jsongetter;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;import com.fasterxml.jackson.annotation.JsonSetter;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonGetter;
import java.util.*;

@Serdeable
class Test {
    private String name;
    private int age = 30;
    private int weight = 75;
    private Map<String, Object> attributes = new HashMap<>();

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

    @JsonAnyGetter
    Map<String, Object> attrs() {
        return attributes;
    }

    @JsonAnySetter
    void attrs(String name, Object value) {
        this.attributes.put(name, value);
    }
}
''')
        when:
        def bean = newInstance(context, 'jsongetter.Test', [name: "Fred"])
        bean.attributes = [foo:'bar']
        def result = writeJson(jsonMapper, bean)

        then:
        result == '{"name":"Fred","age":30,"wgt":75,"foo":"bar"}'

        when:
        bean = jsonMapper.readValue(result, argumentOf(context, 'jsongetter.Test'))

        then:
        bean.name == "Fred"
        bean.age() == 30
        bean.weight() == 75
        bean.attributes == [foo:'bar']

        when:
        bean = newInstance(context, 'jsongetter.Test', [name: "Fred"])
        bean.age = 45
        bean.weight = 100
        result = writeJson(jsonMapper, bean)

        then:
        result == '{"name":"Fred","age":45,"wgt":100}'

        when:
        bean = jsonMapper.readValue(result, argumentOf(context, 'jsongetter.Test'))

        then:
        bean.name == "Fred"
        bean.age() == 45
        bean.weight() == 100

        cleanup:
        context.close()
    }

    void "test json any getter / setter with constructor"() {
        given:
        def context = buildContext('''
package jsongetter;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonGetter;
import java.util.*;

@Serdeable
class Test {
    private String name;
    private int age;
    private int weight;
    private Map<String, Object> attributes = new HashMap<>();

    @JsonCreator
    Test(@JsonProperty("name") String name, @JsonProperty("age") int age, @JsonProperty("wgt") int weight) {
        this.name = name;
        this.age = age;
        this.weight = weight;
    }

    public String getName() {
        return name;
    }

    @JsonGetter
    public int age() {
        return age;
    }

    @JsonGetter("wgt")
    public int weight() {
        return weight;
    }

    @JsonAnyGetter
    Map<String, Object> attrs() {
        return attributes;
    }

    @JsonAnySetter
    void attrs(String name, Object value) {
        this.attributes.put(name, value);
    }
}
''')
        when:
        def bean = newInstance(context, 'jsongetter.Test', "Fred", 30, 75)
        bean.attributes = [foo:'bar']
        def result = writeJson(jsonMapper, bean)

        then:
        result == '{"name":"Fred","age":30,"wgt":75,"foo":"bar"}'

        when:
        bean = jsonMapper.readValue(result, argumentOf(context, 'jsongetter.Test'))

        then:
        bean.name == "Fred"
        bean.age() == 30
        bean.weight() == 75
        bean.attributes == [foo:'bar']

        when:
        bean = jsonMapper.readValue('{"name":"Fred","age":45,"wgt":100}', argumentOf(context, 'jsongetter.Test'))

        then:
        bean.name == "Fred"
        bean.age() == 45
        bean.weight() == 100

        cleanup:
        context.close()
    }

    void "test json any getter / setter with inner class"() {
        given:
        def context = buildContext('''
package jsongetter;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.serde.annotation.Serdeable;
import java.util.*;

@Serdeable
class Test {
    private Map<String, Object> attributes = new HashMap<>();
    private Inner inner;

    void setInner(Inner inner) {
        this.inner = inner;
    }

    Inner getInner() {
        return inner;
    }

    @JsonAnyGetter
    Map<String, Object> getAttributes() {
        return attributes;
    }

    @JsonAnySetter
    void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
}

@Serdeable
class Inner {
    private Map<String, Object> attributes = new HashMap<>();
    private String name;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @JsonAnyGetter
    Map<String, Object> getAttributes() {
        return attributes;
    }

    @JsonAnySetter
    void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
}
''')
        var json = '{"age":10,"inner":{"name":"Bill","color":"green"}}'

        when:
        var deserialized = jsonMapper.readValue(json, argumentOf(context, 'jsongetter.Test'))

        then:
        deserialized.inner.name == "Bill"
        deserialized.attributes["age"] == 10
        deserialized.inner.attributes["color"] == "green"

        cleanup:
        context.close()
    }

    void "test json any getter / setter - map field"() {
        given:
        def context = buildContext('''
package jsongetter;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonGetter;
import java.util.*;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public String name;
    @JsonAnyGetter
    @JsonAnySetter
    public Map<String, Object> attributes = new HashMap<>();
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
}
