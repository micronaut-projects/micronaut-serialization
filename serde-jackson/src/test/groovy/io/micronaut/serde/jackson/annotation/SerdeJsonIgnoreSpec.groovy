package io.micronaut.serde.jackson.annotation


import io.micronaut.core.naming.NameUtils
import io.micronaut.serde.jackson.JsonIgnoreSpec

class SerdeJsonIgnoreSpec extends JsonIgnoreSpec {
    @Override
    protected String unknownPropertyMessage(String propertyName, String className) {
        return "Unknown property [$propertyName] encountered during deserialization of type: ${NameUtils.getSimpleName(className)}"
    }

    void "json ignore on a constructor parameter"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
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
import io.micronaut.core.annotation.Nullable;
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
}
