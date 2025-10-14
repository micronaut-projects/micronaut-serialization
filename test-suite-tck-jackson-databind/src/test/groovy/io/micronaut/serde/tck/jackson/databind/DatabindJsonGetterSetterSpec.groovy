package io.micronaut.serde.tck.jackson.databind

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.serde.jackson.JsonGetterSetterSpec
import spock.lang.PendingFeature

class DatabindJsonGetterSetterSpec extends JsonGetterSetterSpec {

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        // Disable Micronaut annotation processors
        return [new TypeElementVisitor() {
            @Override
            void visitClass(ClassElement element, VisitorContext context) {
            }
        }]
    }

    // Not supported cases by Jackson Databind

    @PendingFeature(reason = "@JsonAnySetter on setter needs to be (String key, Object value)")
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

}
