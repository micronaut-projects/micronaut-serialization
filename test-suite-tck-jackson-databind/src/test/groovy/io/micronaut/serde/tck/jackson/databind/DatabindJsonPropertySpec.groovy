package io.micronaut.serde.tck.jackson.databind

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.serde.jackson.JsonPropertySpec
import spock.lang.PendingFeature

class DatabindJsonPropertySpec extends JsonPropertySpec {

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of("jackson.deserialization.failOnUnknownProperties", "true")
        ))
    }

    void "test required primitive field"() {

        given:
        def ctx = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonProperty(required = true)
    private int value;

    @JsonCreator
    Test(@JsonProperty("value") int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
""")

        when:
        def bean = jsonMapper.readValue('{}', argumentOf(ctx, 'test.Test'))
        then:
        bean.value == 0

        cleanup:
        ctx.close()
    }

    @PendingFeature(reason = "Jackson is using 'defaultValue' only for documentation")
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
""", [value: 'test'])
        when:
            def result = writeJson(jsonMapper, beanUnderTest)

        then:
            result == '{"ignored":false,"other":"test"}'

        when:
            def bean = jsonMapper.readValue(result, argumentOf(context, 'test.Test'))
        then:
            bean.ignored == false
            bean.value == 'test'

        when:
            bean = jsonMapper.readValue("{}", argumentOf(context, 'test.Test'))
        then:
            bean.ignored == false
            bean.value == 'default'

        cleanup:
            context.close()

    }

}
