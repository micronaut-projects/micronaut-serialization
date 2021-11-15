package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonCompileSpec

class JsonIgnoreSpec extends JsonCompileSpec {
    void "test simple @JsonIgnoreProperties"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonIgnoreProperties("ignored")
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

    void "test combined @JsonIgnoreProperties"() {
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Other {
    @JsonIgnoreProperties("ignored2")
    private Test test;
    
    public void setTest(test.Test test) {
        this.test = test;
    }
    public test.Test getTest() {
        return test;
    }
}
@Serdeable
@JsonIgnoreProperties("ignored")
class Test {
    private String value;
    private boolean ignored;
    private boolean ignored2;
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
    
    public void setIgnored2(boolean ignored2) {
        this.ignored2 = ignored2;
    }
    
    public boolean isIgnored2() {
        return ignored2;    
    }
}
""")
        def t = newInstance(context, 'test.Test')
        t.value = 'test'
        def o = newInstance(context, 'test.Other')
        o.test = t

        expect:
        writeJson(jsonMapper, o) == '{"test":{"value":"test"}}'

        cleanup:
        context.close()
    }

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
