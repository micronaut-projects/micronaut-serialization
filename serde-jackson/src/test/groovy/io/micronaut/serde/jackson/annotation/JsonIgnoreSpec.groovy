package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonCompileSpec

class JsonIgnoreSpec extends JsonCompileSpec {

    void "test @JsonIgnore on field"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private String value;
    @JsonIgnore
    private boolean ignored;
    public void setValue(String value) {
        this.value = value;
    } 
    public String getValue() {
        return value;
    }
    
    public void setIgnored(boolean b) {
        this.ignored = b;
    }
    
    public boolean isIgnored() {
        return ignored;
    }
}
""", [value:'test'])
        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":"test"}'

        cleanup:
        context.close()

    }

    void "test @JsonIgnore on method"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private String value;
    
    private boolean ignored;
    public void setValue(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public void setIgnored(boolean b) {
        this.ignored = b;
    }
    
    @JsonIgnore
    public boolean isIgnored() {
        return ignored;
    }
}
""", [value:'test'])
        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"value":"test"}'

        cleanup:
        context.close()

    }

}
