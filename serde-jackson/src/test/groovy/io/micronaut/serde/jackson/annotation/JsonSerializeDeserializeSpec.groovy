package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonCompileSpec

class JsonSerializeDeserializeSpec extends JsonCompileSpec {

    void 'test json deserialize builder'() {
        given:
        def context = buildContext('test.TestBuildMe', """
package test;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.serde.annotation.Serdeable;

@JsonDeserialize(builder = TestBuildMe.Builder.class)
class TestBuildMe {
    private final String name;
    private final int age;

    private TestBuildMe(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public static final class Builder {
        private String name;
        private int age;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public TestBuildMe build() {
            return new TestBuildMe(
                name,
                age
            );
        }
    }
}
""")

        when:
        def result = jsonMapper.readValue('{"name":"Fred", "age": 30}', typeUnderTest)

        then:
        result.name == 'Fred'
        result.age == '30'

        when:
        def json = writeJson(jsonMapper, result)

        then:
        json == '{"name":"Fred", "age": 30}'
    }

    void 'test json serialize/deserialize as'() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.serde.annotation.Serdeable;

@JsonSerialize(as = TestImpl.class)
@JsonDeserialize(as = TestImpl.class)
interface Test {
    String getValue();
}

@Serdeable
class TestImpl implements Test {
    private final String value;
    TestImpl(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}
""")

        when:
        def result = jsonMapper.readValue('{"value":"test"}', typeUnderTest)

        then:
        result.getClass().name == 'test.TestImpl'
        result.value == 'test'

        when:
        def json = writeJson(jsonMapper, result)

        then:
        json == '{"value":"test"}'
    }

    void 'test json serialize/deserialize as different impls'() {
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.serde.annotation.Serdeable;

@JsonSerialize(as = ServerAuthentication.class)
@JsonDeserialize(as = ClientAuthentication.class)
interface Authentication {
    String getValue();
}

@Serdeable
class ClientAuthentication extends ServerAuthentication implements Authentication {
    ClientAuthentication(String value) {
        super(value);
    }

    public String getAnother() {
        return "Shouldn't appear in serialization output";
    }
}

@Serdeable
class ServerAuthentication implements Authentication {
    private final String value;
    ServerAuthentication(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}
""")

        when:
        def authType = argumentOf(context, "test.Authentication")
        def result = jsonMapper.readValue('{"value":"test"}', authType)

        then:
        result.getClass().name == 'test.ClientAuthentication'
        result.value == 'test'

        when:
        def json = jsonMapper.writeValueAsString(
                authType,
                result
        )

        then:
        json == '{"value":"test"}'

        cleanup:
        context.close()
    }
}
