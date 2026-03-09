package io.micronaut.serde.xml

import io.micronaut.core.type.Argument

class XmlIgnoreSpec extends XmlCompileSpec {

    void "test @JsonIgnore on field"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
""", [value: 'test', ignored: true])

        when:
            def bytes = xmlMapper.writeValueAsBytes(beanUnderTest)

        then:
            new String(bytes) == '<Test><value>test</value></Test>'

        cleanup:
            context.close()

    }

    void "test @JsonIgnore on method"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonIgnore;

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
""", [value: 'test'])
        when:
            def bytes = xmlMapper.writeValueAsBytes(beanUnderTest)

        then:
            new String(bytes) == '<Test><value>test</value></Test>'

        cleanup:
            context.close()

    }

}
