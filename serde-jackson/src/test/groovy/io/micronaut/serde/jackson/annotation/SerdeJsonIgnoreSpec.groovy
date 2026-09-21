package io.micronaut.serde.jackson.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.core.naming.NameUtils
import io.micronaut.serde.jackson.JsonIgnoreSpec

class SerdeJsonIgnoreSpec extends JsonIgnoreSpec {
    @Override
    protected String unknownPropertyMessage(String propertyName, String className) {
        return "Unknown property [$propertyName] encountered during deserialization of type: ${NameUtils.getSimpleName(className)}"
    }

    @Override
    protected String unknownFieldMessage(String propertyName, String className) {
        return "Unknown field [$propertyName] encountered during deserialization of type: ${NameUtils.getSimpleName(className)}"
    }

    void "json ignore on a constructor parameter"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@JsonIgnoreProperties(ignoreUnknown = true)
class Test{
    @JsonIgnore
    private final Ignored foo;
    private final String bar;

    @JsonCreator
    public Test(@JsonProperty("foo") Ignored foo, @JsonProperty("bar") String bar) {
        this.foo = foo;
        this.bar = bar;
    }

    public example.Ignored getFoo() {
        return foo;
    }

    public String getBar() {
        return bar;
    }

}
class Ignored {
}
''')

            def des = jsonMapper.readValue('{"foo": "1", "bar": "2"}', typeUnderTest)

        expect:
            des.foo == null
            des.bar == "2"

        cleanup:
            context.close()
    }

    void "json ignore on a record parameter"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@JsonIgnoreProperties(ignoreUnknown = true)
record Test(@JsonIgnore @JsonProperty("foo") Ignored foo, @JsonProperty("bar") String bar) {
}
class Ignored {
}
''')

            def des = jsonMapper.readValue('{"foo": "1", "bar": "2"}', typeUnderTest)

        expect:
            des.foo == null
            des.bar == "2"

        cleanup:
            context.close()
    }

    void "json ignore on an overridden getter also ignores the creator parameter with the same name"() {
        given: 'unlike Jackson, an ignored property also ignores the creator parameter with the same Java and JSON name'
            def context = buildContext('example.Sub', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
abstract class Base {
    private String x;

    @JsonProperty("x")
    public String getX() {
        return x;
    }

    @JsonProperty("x")
    public final void setX(String x) {
        this.x = x;
    }
}

@Serdeable
final class Sub extends Base {
    private final String creatorValue;

    @JsonCreator
    Sub(@JsonProperty("x") String x) {
        this.creatorValue = x;
    }

    public String creatorValue() {
        return creatorValue;
    }

    @Override
    @JsonIgnore
    public String getX() {
        return super.getX();
    }
}
''')

        when:
            def des = jsonMapper.readValue('{"x":"value"}', typeUnderTest)

        then:
            des.creatorValue() == null

        cleanup:
            context.close()
    }

    void "jackson compatible ignore: json ignore on an overridden getter doesn't ignore the explicitly named creator parameter"() {
        given:
            def context = buildJacksonCompatibleIgnoreContext('example.Sub', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
abstract class Base {
    private String x;

    @JsonProperty("x")
    public String getX() {
        return x;
    }

    @JsonProperty("x")
    public final void setX(String x) {
        this.x = x;
    }
}

@Serdeable
final class Sub extends Base {
    private final String creatorValue;

    @JsonCreator
    Sub(@JsonProperty("x") String x) {
        this.creatorValue = x;
    }

    public String creatorValue() {
        return creatorValue;
    }

    @Override
    @JsonIgnore
    public String getX() {
        return super.getX();
    }
}
''')

        when:
            def des = jsonMapper.readValue('{"x":"value"}', typeUnderTest)

        then:
            des.creatorValue() == "value"

        cleanup:
            context.close()
    }

    void "jackson compatible ignore: json ignore on a field doesn't ignore the explicitly named creator parameter"() {
        given:
            def context = buildJacksonCompatibleIgnoreContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonIgnore
    private final String foo;
    private final String bar;

    @JsonCreator
    Test(@JsonProperty("foo") String foo, @JsonProperty("bar") String bar) {
        this.foo = foo;
        this.bar = bar;
    }

    public String getFoo() {
        return foo;
    }

    public String getBar() {
        return bar;
    }
}
''')

        when:
            def des = jsonMapper.readValue('{"foo":"1","bar":"2"}', typeUnderTest)

        then:
            des.foo == "1"
            des.bar == "2"

        cleanup:
            context.close()
    }

    void "jackson compatible ignore: json ignore still ignores a creator parameter that is not explicitly included"() {
        given:
            def context = buildJacksonCompatibleIgnoreContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonIgnore
    private final String foo;
    private final String bar;

    @JsonCreator
    Test(String foo, String bar) {
        this.foo = foo;
        this.bar = bar;
    }

    public String getFoo() {
        return foo;
    }

    public String getBar() {
        return bar;
    }
}
''')

        when:
            def des = jsonMapper.readValue('{"foo":"1","bar":"2"}', typeUnderTest)

        then:
            des.foo == null
            des.bar == "2"

        cleanup:
            context.close()
    }

    void "jackson compatible ignore: json ignore still ignores a record component"() {
        given:
            def context = buildJacksonCompatibleIgnoreContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Test(@JsonIgnore @JsonProperty("foo") String foo, @JsonProperty("bar") String bar) {
}
''')

        when:
            def des = jsonMapper.readValue('{"foo":"1","bar":"2"}', typeUnderTest)

        then:
            des.foo() == null
            des.bar() == "2"

        cleanup:
            context.close()
    }

    private ApplicationContext buildJacksonCompatibleIgnoreContext(String className, String source) {
        def context = buildContext(className, source, true, ['micronaut.serde.deserialization.jackson-compatible-ignore': true])
        typeUnderTest = argumentOf(context, className)
        return context
    }
}
