package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonCompileSpec

class JsonPropertySpec extends JsonCompileSpec {
    void "test @JsonProperty on field"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonProperty(value = "other", defaultValue = "default")
    private String value;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
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
        writeJson(jsonMapper, beanUnderTest) == '{"other":"test"}'

        cleanup:
        context.close()

    }


}
