package io.micronaut.serde.xml

import io.micronaut.core.type.Argument

class XmlPropertySpec extends XmlCompileSpec {

    void "test @JacksonXmlProperty on field"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

import com.fasterxml.jackson.annotation.JsonProperty;

@Serdeable
class Test {
    @JsonProperty("other")
    private String value;

    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }
}
""", [value: 'test'])

        when:
            def bytes = xmlMapper.writeValueAsBytes(beanUnderTest)

        then:
            new String(bytes) == '<Test><other>test</other></Test>'

        cleanup:
            context.close()
    }

}
