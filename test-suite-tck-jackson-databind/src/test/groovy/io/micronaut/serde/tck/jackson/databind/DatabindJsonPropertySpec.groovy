package io.micronaut.serde.tck.jackson.databind

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.core.type.Argument
import io.micronaut.core.util.StringUtils
import io.micronaut.jackson.databind.JacksonDatabindMapper
import io.micronaut.serde.jackson.JsonPropertySpec
import spock.lang.PendingFeature

class DatabindJsonPropertySpec extends JsonPropertySpec {

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of(
                        "jackson.deserialization-features.fail-on-null-for-primitives", "false",
                        "jackson.deserialization-features.fail-on-unknown-properties", StringUtils.FALSE,
                )
        ))
    }

    void "test @JsonProperty.Access.READ_ONLY (get only) - constructor"() {
        // Jackson cannot deserialize READ_ONLY as null
        given:
            def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {

    private String value;
    private String ignored;

    @JsonCreator
    public Test(@JsonProperty("value") String value, @JsonProperty(value = "ignored", access = JsonProperty.Access.READ_ONLY) String ignored) {
        this.value = value;
        this.ignored = ignored;
    }

    public String getValue() {
        return this.value;
    }

    public String getIgnored() {
        return this.ignored;
    }

}
""")
        when:
            def bean = newInstance(context, 'test.Test', "test", "xyz")
            def result = writeJson(jsonMapper, bean)

        then:
            result == '{"value":"test","ignored":"xyz"}'

        when:
            bean = jsonMapper.readValue('{"value":"test","ignored":"xyz"}', argumentOf(context, 'test.Test'))

        then:
            bean.value == 'test'
            bean.ignored == null

        cleanup:
            context.close()
    }

    void "test @JsonProperty.Access.READ_ONLY (get only) - record"() {
        // Jackson cannot deserialize READ_ONLY as null
        given:
            def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Test(
    @JsonProperty
    String value,
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    String ignored
) {}
""")
        when:
            def bean = newInstance(context, 'test.Test', "test", "xyz")
            def result = writeJson(jsonMapper, bean)

        then:
            result == '{"value":"test","ignored":"xyz"}'

        when:
            bean = jsonMapper.readValue('{"value":"test","ignored":"xyz"}', argumentOf(context, 'test.Test'))

        then:
            bean.value == 'test'
            bean.ignored == null

        cleanup:
            context.close()
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

    void "test primitive field null fails by default"() {
        given:
        def context = ApplicationContext.run()
        def mapper = context.getBean(JacksonDatabindMapper)

        when:
        mapper.readValue('{"value":null}', Argument.of(PrimitiveField))

        then:
        def e = thrown(Exception)
        e.message.contains("FAIL_ON_NULL_FOR_PRIMITIVES")

        cleanup:
        context.close()
    }

    void "test primitive field null uses primitive default when configured"() {
        given:
        def context = ApplicationContext.run([
                'jackson.deserialization-features.fail-on-null-for-primitives': false
        ])
        def mapper = context.getBean(JacksonDatabindMapper)

        when:
        def bean = mapper.readValue('{"value":null}', Argument.of(PrimitiveField))

        then:
        bean.value == 0

        cleanup:
        context.close()
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

class PrimitiveField {
    public int value = 10
}
