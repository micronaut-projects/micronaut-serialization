package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonCompileSpec

class JsonGetterSpec extends JsonCompileSpec {

    void "test json getter"() {
        given:
        def context = buildContext('''
package jsongetter;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonGetter;

@Serdeable
class Test {
    private String name;
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    @JsonGetter
    int age() {
        return 30;
    }
    
    @JsonGetter("wgt")
    int weight() {
        return 75;
    }
}
''')
        when:
        def bean = newInstance(context, 'jsongetter.Test', [name: "Fred"])
        def result = writeJson(jsonMapper, bean)

        then:
        result == '{"name":"Fred","age":30,"wgt":75}'

        cleanup:
        context.close()
    }
}
