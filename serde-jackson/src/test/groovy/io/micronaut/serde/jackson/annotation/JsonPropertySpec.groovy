package io.micronaut.serde.jackson.annotation

import io.micronaut.core.beans.exceptions.IntrospectionException
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Requires
import spock.lang.Unroll

class JsonPropertySpec extends JsonCompileSpec {
    void "test JsonProperty on private methods"() {
        when:
        buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonIgnore
    private String value;
    
    public void setValue(String value) {
        this.value = value;
    } 
    public String getValue() {
        return value;
    }
    
    @JsonProperty("value")
    private void setValueInternal(String value) {
        this.value = value.toLowerCase();
    } 
    
    @JsonProperty("value")
    private String getValueInternal() {
        return value.toUpperCase();
    }
}
""", [value: 'test'])
        then:
        def e = thrown(RuntimeException)
        e.message.contains("JSON annotations cannot be used on private methods")
    }

    void "test JsonProperty on protected methods"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonIgnore
    private String value;
    
    public void setValue(String value) {
        this.value = value;
    } 
    public String getValue() {
        return value;
    }
    
    @JsonProperty("value")
    protected void setValueInternal(String value) {
        this.value = value.toLowerCase();
    } 
    
    @JsonProperty("value")
    protected String getValueInternal() {
        return value.toUpperCase();
    }
}
""", [value: 'test'])
        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"value":"TEST"}'

        when:
        def bean =
                jsonMapper.readValue(result, argumentOf(context, 'test.Test'))
        then:
        bean.value == 'test'

        cleanup:
        context.close()
    }

    @Unroll
    void "test invalid defaultValue for #type and value #value"() {

        when:
        buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonProperty(defaultValue = "$value")
    private $type.name value;
    public void setValue($type.name value) {
        this.value = value;
    } 
    public $type.name getValue() {
        return value;
    }
}
""", [:])

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Invalid defaultValue [$value] specified")

        where:
        type    | value
        Integer | 'junk'
        URL     | 'ws://junk'
    }

    @Unroll
    @Requires({ jvm.isJava17Compatible() })
    void "test invalid defaultValue for #type and value #value for records"() {

        when:
        buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Test(
    @JsonProperty(defaultValue = "$value")
    $type.name value
) {}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Invalid defaultValue [$value] specified")

        where:
        type      | value
        Integer   | 'junk'
        int.class | 'junk'
        URL       | 'ws://junk'
    }

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
        result == '{"other":"test"}'

        when:
        def bean =
                jsonMapper.readValue('{"ignored":true}', argumentOf(context, 'test.Test'))
        then:
        bean.ignored == true
        bean.value == 'default'

        cleanup:
        context.close()

    }

    @Requires({ jvm.isJava17Compatible() })
    void "test @JsonProperty records"() {
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Test(
    @JsonProperty(value = "other", defaultValue = "default")
    String value,
    @JsonProperty(access = JsonProperty.Access.READ_ONLY, defaultValue = "false")
    boolean ignored
) {}
""")
        when:
        def bean = newInstance(context, 'test.Test', "test", false)
        def result = writeJson(jsonMapper, bean)

        then:
        result == '{"other":"test"}'

        when:
        bean = jsonMapper.readValue('{"ignored":true}', argumentOf(context, 'test.Test'))

        then:
        bean.ignored == true
        bean.value == 'default'

        when:
        bean = jsonMapper.readValue('{}', argumentOf(context, 'test.Test'))

        then:
        bean.ignored == false
        bean.value == 'default'

        cleanup:
        context.close()

    }

    @Requires({ jvm.isJava17Compatible() })
    void "test @JsonProperty records - invalid default value"() {
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable(validate = false)
record Test(
    @JsonProperty(value = "other", defaultValue = "default")
    String value,
    @JsonProperty(access = JsonProperty.Access.READ_ONLY, defaultValue = "junk")
    int number
) {}
""")
        when:
        def bean = newInstance(context, 'test.Test', "test", 10)
        def result = writeJson(jsonMapper, bean)

        then:
        result == '{"other":"test"}'

        when:
        bean = jsonMapper.readValue('{"ignored":true}', argumentOf(context, 'test.Test'))

        then:
        def e = thrown(IntrospectionException)
        e.cause.message.contains("Constructor Argument [int number] of type [test.Test] defines an invalid default value")

        cleanup:
        context.close()

    }


}
