/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.jackson

abstract class JsonSerializeDeserializeSpec extends JsonCompileSpec {

    void 'test json serialize/deserialize as self'() {
        given:
            def context = buildContext('test.TestImpl', """
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.serde.annotation.Serdeable;

interface Test {
    String getValue();
}

@Serdeable
@JsonSerialize(as = TestImpl.class)
@JsonDeserialize(as = TestImpl.class)
class TestImpl implements Test {

    private final String value;

    @JsonCreator
    TestImpl(@JsonProperty("value") String value) {
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

    void 'test json serialize/deserialize as'() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    TestImpl(@JsonProperty("value") String value) {
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    ClientAuthentication(@JsonProperty("value") String value) {
        super(value);
    }

    public String getAnother() {
        return "Shouldn't appear in serialization output";
    }
}

@Serdeable
class ServerAuthentication implements Authentication {

    private final String value;

    @JsonCreator
    ServerAuthentication(@JsonProperty("value") String value) {
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
