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

import com.fasterxml.jackson.annotation.JsonInclude
import spock.lang.Unroll

import static com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL

abstract class JsonIncludeSpec extends JsonCompileSpec {

    void "@JsonInclude String"() {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String alwaysString;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String nonNullString;
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public String nonAbsentString;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String nonEmptyString;
}
''')
            def presentString = newInstance(context, 'example.Test')
            presentString.alwaysString = 'a'
            presentString.nonNullString = 'a'
            presentString.nonAbsentString = 'a'
            presentString.nonEmptyString = 'a'

            def emptyString = newInstance(context, 'example.Test')
            emptyString.alwaysString = ""
            emptyString.nonNullString = ""
            emptyString.nonAbsentString = ""
            emptyString.nonEmptyString = ""

            def nullString = newInstance(context, 'example.Test')
            nullString.alwaysString = null
            nullString.nonNullString = null
            nullString.nonAbsentString = null
            nullString.nonEmptyString = null

        expect:
            writeJson(jsonMapper, presentString) == '{"alwaysString":"a","nonNullString":"a","nonAbsentString":"a","nonEmptyString":"a"}'
            writeJson(jsonMapper, emptyString) == '{"alwaysString":"","nonNullString":"","nonAbsentString":""}'
            writeJson(jsonMapper, nullString) == '{"alwaysString":null}'
    }

    void "@JsonInclude Integer"() {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Integer alwaysInteger;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer nonNullInteger;
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Integer nonAbsentInteger;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Integer nonEmptyInteger;
}
''')
            def presentInteger = newInstance(context, 'example.Test')
            presentInteger.alwaysInteger = 1
            presentInteger.nonNullInteger = 1
            presentInteger.nonAbsentInteger = 1
            presentInteger.nonEmptyInteger = 1

            def zeroInteger = newInstance(context, 'example.Test')
            zeroInteger.alwaysInteger = 0
            zeroInteger.nonNullInteger = 0
            zeroInteger.nonAbsentInteger = 0
            zeroInteger.nonEmptyInteger = 0

            def nullInteger = newInstance(context, 'example.Test')
            nullInteger.alwaysInteger = null
            nullInteger.nonNullInteger = null
            nullInteger.nonAbsentInteger = null
            nullInteger.nonEmptyInteger = null

        expect:
            writeJson(jsonMapper, presentInteger) == '{"alwaysInteger":1,"nonNullInteger":1,"nonAbsentInteger":1,"nonEmptyInteger":1}'
            writeJson(jsonMapper, zeroInteger) == '{"alwaysInteger":0,"nonNullInteger":0,"nonAbsentInteger":0,"nonEmptyInteger":0}'
            writeJson(jsonMapper, nullInteger) == '{"alwaysInteger":null}'
    }

    void "@JsonInclude Boolean"() {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Boolean alwaysBoolean;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean nonNullBoolean;
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Boolean nonAbsentBoolean;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Boolean nonEmptyBoolean;
}
''')
            def trueBoolean = newInstance(context, 'example.Test')
            trueBoolean.alwaysBoolean = true
            trueBoolean.nonNullBoolean = true
            trueBoolean.nonAbsentBoolean = true
            trueBoolean.nonEmptyBoolean = true

            def falseBoolean = newInstance(context, 'example.Test')
            falseBoolean.alwaysBoolean = false
            falseBoolean.nonNullBoolean = false
            falseBoolean.nonAbsentBoolean = false
            falseBoolean.nonEmptyBoolean = false

            def nullBoolean = newInstance(context, 'example.Test')
            nullBoolean.alwaysBoolean = null
            nullBoolean.nonNullBoolean = null
            nullBoolean.nonAbsentBoolean = null
            nullBoolean.nonEmptyBoolean = null

        expect:
            writeJson(jsonMapper, trueBoolean) == '{"alwaysBoolean":true,"nonNullBoolean":true,"nonAbsentBoolean":true,"nonEmptyBoolean":true}'
            writeJson(jsonMapper, falseBoolean) == '{"alwaysBoolean":false,"nonNullBoolean":false,"nonAbsentBoolean":false,"nonEmptyBoolean":false}'
            writeJson(jsonMapper, nullBoolean) == '{"alwaysBoolean":null}'
    }

    void "@JsonInclude on map"() {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Map<String, String> always;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, String> nonNull;
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Map<String, String> nonAbsent;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> nonEmpty;
}
''')
            def setMap = newInstance(context, 'example.Test')
            setMap.always = Map.of("foo", "bar")
            setMap.nonNull = Map.of("foo", "bar")
            setMap.nonAbsent = Map.of("foo", "bar")
            setMap.nonAbsent = Map.of("foo", "bar")
            setMap.nonEmpty = Map.of("foo", "bar")

            def emptyMap = newInstance(context, 'example.Test')
            emptyMap.always = Map.of()
            emptyMap.nonNull = Map.of()
            emptyMap.nonAbsent = Map.of()
            emptyMap.nonEmpty = Map.of()

            def nullMap = newInstance(context, 'example.Test')
            nullMap.always = null
            nullMap.nonNull = null
            nullMap.nonAbsent = null
            nullMap.nonEmpty = null

        expect:
            writeJson(jsonMapper, setMap) == '{"always":{"foo":"bar"},"nonNull":{"foo":"bar"},"nonAbsent":{"foo":"bar"},"nonEmpty":{"foo":"bar"}}'
            writeJson(jsonMapper, emptyMap) == '{"always":{},"nonNull":{},"nonAbsent":{}}'
            writeJson(jsonMapper, nullMap) == '{"always":null}'
    }

    void "@JsonInclude class with map"() {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
@JsonInclude(JsonInclude.Include.ALWAYS)
class Test {
    public Map<String, String> always;
}
''')
            def setMap = newInstance(context, 'example.Test')
            setMap.always = Map.of("foo", "bar")

            def emptyMap = newInstance(context, 'example.Test')
            emptyMap.always = Map.of()

            def nullMap = newInstance(context, 'example.Test')
            nullMap.always = null

        expect:
            writeJson(jsonMapper, setMap) == '{"always":{"foo":"bar"}}'
            writeJson(jsonMapper, emptyMap) == '{"always":{}}'
            writeJson(jsonMapper, nullMap) == '{"always":null}'
    }

    void "@JsonInclude class with map 2"() {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
@JsonInclude(JsonInclude.Include.ALWAYS)
class Test {
    public Map<String, Object> always;
}
''')
            def setMap = newInstance(context, 'example.Test')
            setMap.always = Map.of("foo", "bar")

            def emptyMap = newInstance(context, 'example.Test')
            emptyMap.always = Map.of()

            def nullMap = newInstance(context, 'example.Test')
            nullMap.always = null

        expect:
            writeJson(jsonMapper, setMap) == '{"always":{"foo":"bar"}}'
            writeJson(jsonMapper, emptyMap) == '{"always":{}}'
            writeJson(jsonMapper, nullMap) == '{"always":null}'
    }

    void "@JsonInclude on Map<String, String> with content type"(JsonInclude.Include contentType) {
        given:
            def context = buildContext("""
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import java.util.*;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonInclude(value = JsonInclude.Include.ALWAYS, content = JsonInclude.Include.$contentType)
    public Map<String, String> always;
    @JsonInclude(value = JsonInclude.Include.NON_NULL, content = JsonInclude.Include.$contentType)
    public Map<String, String> nonNull;
    @JsonInclude(value = JsonInclude.Include.NON_ABSENT, content = JsonInclude.Include.$contentType)
    public Map<String, String> nonAbsent;
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY, content = JsonInclude.Include.$contentType)
    public Map<String, String> nonEmpty;
}
""")
            def map1 = newInstance(context, 'example.Test')
            map1.always = Map.of("foo", "bar")
            map1.nonNull = Map.of("foo", "bar")
            map1.nonAbsent = Map.of("foo", "bar")
            map1.nonAbsent = Map.of("foo", "bar")
            map1.nonEmpty = Map.of("foo", "bar")

            def map2 = newInstance(context, 'example.Test')
            map2.always = Map.of("foo", "")
            map2.nonNull = Map.of("foo", "")
            map2.nonAbsent = Map.of("foo", "")
            map2.nonAbsent = Map.of("foo", "")
            map2.nonEmpty = Map.of("foo", "")

            def nullValueMap = new HashMap()
            nullValueMap.put( "foobar", null)
            def map3 = newInstance(context, 'example.Test')
            map3.always = nullValueMap
            map3.nonNull = nullValueMap
            map3.nonAbsent = nullValueMap
            map3.nonAbsent = nullValueMap
            map3.nonEmpty = nullValueMap

        expect:
            writeJson(jsonMapper, map1) == map1Value
            writeJson(jsonMapper, map2) == map2Value
            writeJson(jsonMapper, map3) == map3Value

        where:
            contentType << [
                    JsonInclude.Include.ALWAYS,
                    JsonInclude.Include.NON_NULL,
                    JsonInclude.Include.NON_ABSENT,
                    JsonInclude.Include.NON_EMPTY
            ]
            map1Value << [
                    '{"always":{"foo":"bar"},"nonNull":{"foo":"bar"},"nonAbsent":{"foo":"bar"},"nonEmpty":{"foo":"bar"}}',
                    '{"always":{"foo":"bar"},"nonNull":{"foo":"bar"},"nonAbsent":{"foo":"bar"},"nonEmpty":{"foo":"bar"}}',
                    '{"always":{"foo":"bar"},"nonNull":{"foo":"bar"},"nonAbsent":{"foo":"bar"},"nonEmpty":{"foo":"bar"}}',
                    '{"always":{"foo":"bar"},"nonNull":{"foo":"bar"},"nonAbsent":{"foo":"bar"},"nonEmpty":{"foo":"bar"}}'
            ]
            map2Value << [
                    '{"always":{"foo":""},"nonNull":{"foo":""},"nonAbsent":{"foo":""},"nonEmpty":{"foo":""}}',
                    '{"always":{"foo":""},"nonNull":{"foo":""},"nonAbsent":{"foo":""},"nonEmpty":{"foo":""}}',
                    '{"always":{"foo":""},"nonNull":{"foo":""},"nonAbsent":{"foo":""},"nonEmpty":{"foo":""}}',
                    '{"always":{},"nonNull":{},"nonAbsent":{}}'
            ]
            map3Value << [
                    '{"always":{"foobar":null},"nonNull":{"foobar":null},"nonAbsent":{"foobar":null},"nonEmpty":{"foobar":null}}',
                    '{"always":{},"nonNull":{},"nonAbsent":{}}',
                    '{"always":{},"nonNull":{},"nonAbsent":{}}',
                    '{"always":{},"nonNull":{},"nonAbsent":{}}',
            ]

    }

    void "@JsonInclude on Map with content type"(JsonInclude.Include contentType) {
        given:
            def context = buildContext("""
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import java.util.*;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonInclude(value = JsonInclude.Include.ALWAYS, content = JsonInclude.Include.$contentType)
    public Map always;
    @JsonInclude(value = JsonInclude.Include.NON_NULL, content = JsonInclude.Include.$contentType)
    public Map nonNull;
    @JsonInclude(value = JsonInclude.Include.NON_ABSENT, content = JsonInclude.Include.$contentType)
    public Map nonAbsent;
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY, content = JsonInclude.Include.$contentType)
    public Map nonEmpty;
}
""")
            def map1 = newInstance(context, 'example.Test')
            map1.always = Map.of("foo", "bar")
            map1.nonNull = Map.of("foo", "bar")
            map1.nonAbsent = Map.of("foo", "bar")
            map1.nonAbsent = Map.of("foo", "bar")
            map1.nonEmpty = Map.of("foo", "bar")

            def map2 = newInstance(context, 'example.Test')
            map2.always = Map.of("foo", "")
            map2.nonNull = Map.of("foo", "")
            map2.nonAbsent = Map.of("foo", "")
            map2.nonAbsent = Map.of("foo", "")
            map2.nonEmpty = Map.of("foo", "")

            def nullValueMap = new HashMap()
            nullValueMap.put( "foobar", null)
            def map3 = newInstance(context, 'example.Test')
            map3.always = nullValueMap
            map3.nonNull = nullValueMap
            map3.nonAbsent = nullValueMap
            map3.nonAbsent = nullValueMap
            map3.nonEmpty = nullValueMap

        expect:
            writeJson(jsonMapper, map1) == map1Value
            writeJson(jsonMapper, map2) == map2Value
            writeJson(jsonMapper, map3) == map3Value

        where:
            contentType << [
                    JsonInclude.Include.ALWAYS,
                    JsonInclude.Include.NON_NULL,
                    JsonInclude.Include.NON_ABSENT,
                    JsonInclude.Include.NON_EMPTY
            ]
            map1Value << [
                    '{"always":{"foo":"bar"},"nonNull":{"foo":"bar"},"nonAbsent":{"foo":"bar"},"nonEmpty":{"foo":"bar"}}',
                    '{"always":{"foo":"bar"},"nonNull":{"foo":"bar"},"nonAbsent":{"foo":"bar"},"nonEmpty":{"foo":"bar"}}',
                    '{"always":{"foo":"bar"},"nonNull":{"foo":"bar"},"nonAbsent":{"foo":"bar"},"nonEmpty":{"foo":"bar"}}',
                    '{"always":{"foo":"bar"},"nonNull":{"foo":"bar"},"nonAbsent":{"foo":"bar"},"nonEmpty":{"foo":"bar"}}'
            ]
            map2Value << [
                    '{"always":{"foo":""},"nonNull":{"foo":""},"nonAbsent":{"foo":""},"nonEmpty":{"foo":""}}',
                    '{"always":{"foo":""},"nonNull":{"foo":""},"nonAbsent":{"foo":""},"nonEmpty":{"foo":""}}',
                    '{"always":{"foo":""},"nonNull":{"foo":""},"nonAbsent":{"foo":""},"nonEmpty":{"foo":""}}',
                    '{"always":{},"nonNull":{},"nonAbsent":{}}'
            ]
            map3Value << [
                    '{"always":{"foobar":null},"nonNull":{"foobar":null},"nonAbsent":{"foobar":null},"nonEmpty":{"foobar":null}}',
                    '{"always":{},"nonNull":{},"nonAbsent":{}}',
                    '{"always":{},"nonNull":{},"nonAbsent":{}}',
                    '{"always":{},"nonNull":{},"nonAbsent":{}}',
            ]
    }

    void "@JsonInclude on optional with content type"() {
        given:
            def context = buildContext("""
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonInclude(value = JsonInclude.Include.ALWAYS, content = JsonInclude.Include.$contentType)
    public Optional<String> always;
    @JsonInclude(value = JsonInclude.Include.NON_NULL, content = JsonInclude.Include.$contentType)
    public Optional<String> nonNull;
    @JsonInclude(value = JsonInclude.Include.NON_ABSENT, content = JsonInclude.Include.$contentType)
    public Optional<String> nonAbsent;
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY, content = JsonInclude.Include.$contentType)
    public Optional<String> nonEmpty;
}
""")
            def setOptional = newInstance(context, 'example.Test')
            setOptional.always = Optional.of("foobar")
            setOptional.nonNull = Optional.of("foobar")
            setOptional.nonAbsent = Optional.of("foobar")
            setOptional.nonAbsent = Optional.of("foobar")
            setOptional.nonEmpty = Optional.of("foobar")

            def setEmptyStringOptional = newInstance(context, 'example.Test')
            setEmptyStringOptional.always = Optional.of("")
            setEmptyStringOptional.nonNull = Optional.of("")
            setEmptyStringOptional.nonAbsent = Optional.of("")
            setEmptyStringOptional.nonAbsent = Optional.of("")
            setEmptyStringOptional.nonEmpty = Optional.of("")

            def emptyOptional = newInstance(context, 'example.Test')
            emptyOptional.always = Optional.empty()
            emptyOptional.nonNull = Optional.empty()
            emptyOptional.nonAbsent = Optional.empty()
            emptyOptional.nonEmpty = Optional.empty()

            def nullOptional = newInstance(context, 'example.Test')
            nullOptional.always = null
            nullOptional.nonNull = null
            nullOptional.nonAbsent = null
            nullOptional.nonEmpty = null

        expect:
            writeJson(jsonMapper, setOptional) == setOptionalValue
            writeJson(jsonMapper, setEmptyStringOptional) == setEmptyStringOptionalValue
            writeJson(jsonMapper, emptyOptional) == emptyOptionalValue
            writeJson(jsonMapper, nullOptional) == nullOptionalValue

        where:
            contentType << [
                    JsonInclude.Include.ALWAYS,
                    JsonInclude.Include.NON_NULL,
                    JsonInclude.Include.NON_ABSENT,
                    JsonInclude.Include.NON_EMPTY
            ]
            setOptionalValue << [
                    '{"always":"foobar","nonNull":"foobar","nonAbsent":"foobar","nonEmpty":"foobar"}',
                    '{"always":"foobar","nonNull":"foobar","nonAbsent":"foobar","nonEmpty":"foobar"}',
                    '{"always":"foobar","nonNull":"foobar","nonAbsent":"foobar","nonEmpty":"foobar"}',
                    '{"always":"foobar","nonNull":"foobar","nonAbsent":"foobar","nonEmpty":"foobar"}',
            ]
            setEmptyStringOptionalValue << [
                    '{"always":"","nonNull":"","nonAbsent":"","nonEmpty":""}',
                    '{"always":"","nonNull":"","nonAbsent":"","nonEmpty":""}',
                    '{"always":"","nonNull":"","nonAbsent":"","nonEmpty":""}',
                    '{"always":"","nonNull":""}',
            ]
            emptyOptionalValue << [
                    '{"always":null,"nonNull":null}',
                    '{"always":null,"nonNull":null}',
                    '{"always":null,"nonNull":null}',
                    '{"always":null,"nonNull":null}',
            ]
            nullOptionalValue << [
                    '{"always":null}',
                    '{"always":null}',
                    '{"always":null}',
                    '{"always":null}',
            ]
    }

    void "@JsonInclude on optional with content type"() {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Optional<String> always;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Optional<String> nonNull;
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> nonAbsent;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<String> nonEmpty;
}
''')
            def setOptional = newInstance(context, 'example.Test')
            setOptional.always = Optional.of("foobar")
            setOptional.nonNull = Optional.of("foobar")
            setOptional.nonAbsent = Optional.of("foobar")
            setOptional.nonAbsent = Optional.of("foobar")
            setOptional.nonEmpty = Optional.of("foobar")

            def emptyOptional = newInstance(context, 'example.Test')
            emptyOptional.always = Optional.empty()
            emptyOptional.nonNull = Optional.empty()
            emptyOptional.nonAbsent = Optional.empty()
            emptyOptional.nonEmpty = Optional.empty()

            def nullOptional = newInstance(context, 'example.Test')
            nullOptional.always = null
            nullOptional.nonNull = null
            nullOptional.nonAbsent = null
            nullOptional.nonEmpty = null

        expect:
            writeJson(jsonMapper, setOptional) == '{"always":"foobar","nonNull":"foobar","nonAbsent":"foobar","nonEmpty":"foobar"}'
            writeJson(jsonMapper, emptyOptional) == '{"always":null,"nonNull":null}'
            writeJson(jsonMapper, nullOptional) == '{"always":null}'
    }

    void "@JsonInclude on optional list"() {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Optional<List<String>> always;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Optional<List<String>> nonNull;
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<List<String>> nonAbsent;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<List<String>> nonEmpty;
}
''')
            def setOptional = newInstance(context, 'example.Test')
            setOptional.always = Optional.of(List.of("foo", "bar"))
            setOptional.nonNull = Optional.of(List.of("foo", "bar"))
            setOptional.nonAbsent = Optional.of(List.of("foo", "bar"))
            setOptional.nonAbsent = Optional.of(List.of("foo", "bar"))
            setOptional.nonEmpty = Optional.of(List.of("foo", "bar"))

            def emptyOptional = newInstance(context, 'example.Test')
            emptyOptional.always = Optional.empty()
            emptyOptional.nonNull = Optional.empty()
            emptyOptional.nonAbsent = Optional.empty()
            emptyOptional.nonEmpty = Optional.empty()

            def emptyListOptional = newInstance(context, 'example.Test')
            emptyListOptional.always = Optional.of(List.of())
            emptyListOptional.nonNull = Optional.of(List.of())
            emptyListOptional.nonAbsent = Optional.of(List.of())
            emptyListOptional.nonEmpty = Optional.of(List.of())

            def nullOptional = newInstance(context, 'example.Test')
            nullOptional.always = null
            nullOptional.nonNull = null
            nullOptional.nonAbsent = null
            nullOptional.nonEmpty = null

        expect:
            writeJson(jsonMapper, setOptional) == '{"always":["foo","bar"],"nonNull":["foo","bar"],"nonAbsent":["foo","bar"],"nonEmpty":["foo","bar"]}'
            writeJson(jsonMapper, emptyOptional) == '{"always":null,"nonNull":null}'
            writeJson(jsonMapper, nullOptional) == '{"always":null}'
            writeJson(jsonMapper, emptyListOptional) == '{"always":[],"nonNull":[],"nonAbsent":[],"nonEmpty":[]}'
    }

    void "@JsonInclude on list"() {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public List<String> always;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<String> nonNull;
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public List<String> nonAbsent;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> nonEmpty;
}
''')
            def setList = newInstance(context, 'example.Test')
            setList.always = List.of("foo", "bar")
            setList.nonNull = List.of("foo", "bar")
            setList.nonAbsent = List.of("foo", "bar")
            setList.nonAbsent = List.of("foo", "bar")
            setList.nonEmpty = List.of("foo", "bar")

            def emptyList = newInstance(context, 'example.Test')
            emptyList.always = List.of()
            emptyList.nonNull = List.of()
            emptyList.nonAbsent = List.of()
            emptyList.nonEmpty = List.of()

            def nullList = newInstance(context, 'example.Test')
            nullList.always = null
            nullList.nonNull = null
            nullList.nonAbsent = null
            nullList.nonEmpty = null

        expect:
            writeJson(jsonMapper, setList) == '{"always":["foo","bar"],"nonNull":["foo","bar"],"nonAbsent":["foo","bar"],"nonEmpty":["foo","bar"]}'
            writeJson(jsonMapper, emptyList) == '{"always":[],"nonNull":[],"nonAbsent":[]}'
            writeJson(jsonMapper, nullList) == '{"always":null}'
    }

    void "@JsonInclude on list with content type"() {
        given:
            def context = buildContext("""
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonInclude(value = JsonInclude.Include.ALWAYS, content = JsonInclude.Include.$contentType)
    public List<String> always;
    @JsonInclude(value = JsonInclude.Include.NON_NULL, content = JsonInclude.Include.$contentType)
    public List<String> nonNull;
    @JsonInclude(value = JsonInclude.Include.NON_ABSENT, content = JsonInclude.Include.$contentType)
    public List<String> nonAbsent;
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY, content = JsonInclude.Include.$contentType)
    public List<String> nonEmpty;
}
""")
            def setList = newInstance(context, 'example.Test')
            setList.always = List.of("foo", "bar")
            setList.nonNull = List.of("foo", "bar")
            setList.nonAbsent = List.of("foo", "bar")
            setList.nonAbsent = List.of("foo", "bar")
            setList.nonEmpty = List.of("foo", "bar")

            def emptyList = newInstance(context, 'example.Test')
            emptyList.always = List.of()
            emptyList.nonNull = List.of()
            emptyList.nonAbsent = List.of()
            emptyList.nonEmpty = List.of()

            def emptyStringList = newInstance(context, 'example.Test')
            emptyStringList.always = List.of("")
            emptyStringList.nonNull = List.of("")
            emptyStringList.nonAbsent = List.of("")
            emptyStringList.nonEmpty = List.of("")

            List<String> l = new ArrayList<>()
            l.add(null)
            def nullsList = newInstance(context, 'example.Test')
            nullsList.always = l
            nullsList.nonNull = l
            nullsList.nonAbsent = l
            nullsList.nonEmpty = l

            def nullList = newInstance(context, 'example.Test')
            nullList.always = null
            nullList.nonNull = null
            nullList.nonAbsent = null
            nullList.nonEmpty = null

        expect:
            writeJson(jsonMapper, setList) == setListValue
            writeJson(jsonMapper, emptyStringList) == emptyStringListValue
            writeJson(jsonMapper, nullsList) == nullsListValue
            writeJson(jsonMapper, emptyList) == emptyListValue
            writeJson(jsonMapper, nullList) == nullListValue

        where:
            contentType << [
                    JsonInclude.Include.ALWAYS,
                    JsonInclude.Include.NON_NULL,
                    JsonInclude.Include.NON_ABSENT,
                    JsonInclude.Include.NON_EMPTY
            ]
            setListValue << [
                    '{"always":["foo","bar"],"nonNull":["foo","bar"],"nonAbsent":["foo","bar"],"nonEmpty":["foo","bar"]}',
                    '{"always":["foo","bar"],"nonNull":["foo","bar"],"nonAbsent":["foo","bar"],"nonEmpty":["foo","bar"]}',
                    '{"always":["foo","bar"],"nonNull":["foo","bar"],"nonAbsent":["foo","bar"],"nonEmpty":["foo","bar"]}',
                    '{"always":["foo","bar"],"nonNull":["foo","bar"],"nonAbsent":["foo","bar"],"nonEmpty":["foo","bar"]}'
            ]
            emptyListValue << [
                    '{"always":[],"nonNull":[],"nonAbsent":[]}',
                    '{"always":[],"nonNull":[],"nonAbsent":[]}',
                    '{"always":[],"nonNull":[],"nonAbsent":[]}',
                    '{"always":[],"nonNull":[],"nonAbsent":[]}'
            ]
            emptyStringListValue << [
                    '{"always":[""],"nonNull":[""],"nonAbsent":[""],"nonEmpty":[""]}',
                    '{"always":[""],"nonNull":[""],"nonAbsent":[""],"nonEmpty":[""]}',
                    '{"always":[""],"nonNull":[""],"nonAbsent":[""],"nonEmpty":[""]}',
                    '{"always":[""],"nonNull":[""],"nonAbsent":[""],"nonEmpty":[""]}'
            ]
            nullsListValue << [
                    '{"always":[null],"nonNull":[null],"nonAbsent":[null],"nonEmpty":[null]}',
                    '{"always":[null],"nonNull":[null],"nonAbsent":[null],"nonEmpty":[null]}',
                    '{"always":[null],"nonNull":[null],"nonAbsent":[null],"nonEmpty":[null]}',
                    '{"always":[null],"nonNull":[null],"nonAbsent":[null],"nonEmpty":[null]}'
            ]
            nullListValue << [
                    '{"always":null}',
                    '{"always":null}',
                    '{"always":null}',
                    '{"always":null}'
            ]
    }
    void "@JsonInclude on list with content type"() {
        given:
            def context = buildContext("""
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonInclude(value = JsonInclude.Include.ALWAYS, content = JsonInclude.Include.$contentType)
    public List<String> always;
    @JsonInclude(value = JsonInclude.Include.NON_NULL, content = JsonInclude.Include.$contentType)
    public List<String> nonNull;
    @JsonInclude(value = JsonInclude.Include.NON_ABSENT, content = JsonInclude.Include.$contentType)
    public List<String> nonAbsent;
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY, content = JsonInclude.Include.$contentType)
    public List<String> nonEmpty;
}
""")
            def setList = newInstance(context, 'example.Test')
            setList.always = List.of("foo", "bar")
            setList.nonNull = List.of("foo", "bar")
            setList.nonAbsent = List.of("foo", "bar")
            setList.nonAbsent = List.of("foo", "bar")
            setList.nonEmpty = List.of("foo", "bar")

            def emptyList = newInstance(context, 'example.Test')
            emptyList.always = List.of()
            emptyList.nonNull = List.of()
            emptyList.nonAbsent = List.of()
            emptyList.nonEmpty = List.of()

            def emptyStringList = newInstance(context, 'example.Test')
            emptyStringList.always = List.of("")
            emptyStringList.nonNull = List.of("")
            emptyStringList.nonAbsent = List.of("")
            emptyStringList.nonEmpty = List.of("")

            List<String> l = new ArrayList<>()
            l.add(null)
            def nullsList = newInstance(context, 'example.Test')
            nullsList.always = l
            nullsList.nonNull = l
            nullsList.nonAbsent = l
            nullsList.nonEmpty = l

            def nullList = newInstance(context, 'example.Test')
            nullList.always = null
            nullList.nonNull = null
            nullList.nonAbsent = null
            nullList.nonEmpty = null

        expect:
            writeJson(jsonMapper, setList) == setListValue
            writeJson(jsonMapper, emptyStringList) == emptyStringListValue
            writeJson(jsonMapper, nullsList) == nullsListValue
            writeJson(jsonMapper, emptyList) == emptyListValue
            writeJson(jsonMapper, nullList) == nullListValue

        where:
            contentType << [
                    JsonInclude.Include.ALWAYS,
                    JsonInclude.Include.NON_NULL,
                    JsonInclude.Include.NON_ABSENT,
                    JsonInclude.Include.NON_EMPTY
            ]
            setListValue << [
                    '{"always":["foo","bar"],"nonNull":["foo","bar"],"nonAbsent":["foo","bar"],"nonEmpty":["foo","bar"]}',
                    '{"always":["foo","bar"],"nonNull":["foo","bar"],"nonAbsent":["foo","bar"],"nonEmpty":["foo","bar"]}',
                    '{"always":["foo","bar"],"nonNull":["foo","bar"],"nonAbsent":["foo","bar"],"nonEmpty":["foo","bar"]}',
                    '{"always":["foo","bar"],"nonNull":["foo","bar"],"nonAbsent":["foo","bar"],"nonEmpty":["foo","bar"]}'
            ]
            emptyListValue << [
                    '{"always":[],"nonNull":[],"nonAbsent":[]}',
                    '{"always":[],"nonNull":[],"nonAbsent":[]}',
                    '{"always":[],"nonNull":[],"nonAbsent":[]}',
                    '{"always":[],"nonNull":[],"nonAbsent":[]}'
            ]
            emptyStringListValue << [
                    '{"always":[""],"nonNull":[""],"nonAbsent":[""],"nonEmpty":[""]}',
                    '{"always":[""],"nonNull":[""],"nonAbsent":[""],"nonEmpty":[""]}',
                    '{"always":[""],"nonNull":[""],"nonAbsent":[""],"nonEmpty":[""]}',
                    '{"always":[""],"nonNull":[""],"nonAbsent":[""],"nonEmpty":[""]}'
            ]
            nullsListValue << [
                    '{"always":[null],"nonNull":[null],"nonAbsent":[null],"nonEmpty":[null]}',
                    '{"always":[null],"nonNull":[null],"nonAbsent":[null],"nonEmpty":[null]}',
                    '{"always":[null],"nonNull":[null],"nonAbsent":[null],"nonEmpty":[null]}',
                    '{"always":[null],"nonNull":[null],"nonAbsent":[null],"nonEmpty":[null]}'
            ]
            nullListValue << [
                    '{"always":null}',
                    '{"always":null}',
                    '{"always":null}',
                    '{"always":null}'
            ]
    }

    void "@JsonInclude on array with content type"() {
        given:
            def context = buildContext("""
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonInclude(value = JsonInclude.Include.ALWAYS, content = JsonInclude.Include.$contentType)
    public String[] always;
    @JsonInclude(value = JsonInclude.Include.NON_NULL, content = JsonInclude.Include.$contentType)
    public String[] nonNull;
    @JsonInclude(value = JsonInclude.Include.NON_ABSENT, content = JsonInclude.Include.$contentType)
    public String[] nonAbsent;
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY, content = JsonInclude.Include.$contentType)
    public String[] nonEmpty;
}
""")
            def fullArray = newInstance(context, 'example.Test')
            fullArray.always = new String[] {"foo", "bar"}
            fullArray.nonNull = new String[] {"foo", "bar"}
            fullArray.nonAbsent = new String[] {"foo", "bar"}
            fullArray.nonAbsent = new String[] {"foo", "bar"}
            fullArray.nonEmpty = new String[] {"foo", "bar"}

            def emptyArray = newInstance(context, 'example.Test')
            emptyArray.always = new String[] {}
            emptyArray.nonNull = new String[] {}
            emptyArray.nonAbsent = new String[] {}
            emptyArray.nonEmpty = new String[] {}

            def emptyStringArray = newInstance(context, 'example.Test')
            emptyStringArray.always = new String[] {""}
            emptyStringArray.nonNull = new String[] {""}
            emptyStringArray.nonAbsent = new String[] {""}
            emptyStringArray.nonEmpty = new String[] {""}

            String[] arrayWithNulls = new String[] { null }
            def nullsArray = newInstance(context, 'example.Test')
            nullsArray.always = arrayWithNulls
            nullsArray.nonNull = arrayWithNulls
            nullsArray.nonAbsent = arrayWithNulls
            nullsArray.nonEmpty = arrayWithNulls

            def nullArray = newInstance(context, 'example.Test')
            nullArray.always = null
            nullArray.nonNull = null
            nullArray.nonAbsent = null
            nullArray.nonEmpty = null

        expect:
            writeJson(jsonMapper, fullArray) == fullArrayValue
            writeJson(jsonMapper, emptyStringArray) == emptyStringArrayValue
            writeJson(jsonMapper, nullsArray) == nullsArrayValue
            writeJson(jsonMapper, emptyArray) == emptyArrayValue
            writeJson(jsonMapper, nullArray) == nullArrayValue

        where:
            contentType << [
                    JsonInclude.Include.ALWAYS,
                    JsonInclude.Include.NON_NULL,
                    JsonInclude.Include.NON_ABSENT,
                    JsonInclude.Include.NON_EMPTY
            ]
            fullArrayValue << [
                    '{"always":["foo","bar"],"nonNull":["foo","bar"],"nonAbsent":["foo","bar"],"nonEmpty":["foo","bar"]}',
                    '{"always":["foo","bar"],"nonNull":["foo","bar"],"nonAbsent":["foo","bar"],"nonEmpty":["foo","bar"]}',
                    '{"always":["foo","bar"],"nonNull":["foo","bar"],"nonAbsent":["foo","bar"],"nonEmpty":["foo","bar"]}',
                    '{"always":["foo","bar"],"nonNull":["foo","bar"],"nonAbsent":["foo","bar"],"nonEmpty":["foo","bar"]}'
            ]
            emptyArrayValue << [
                    '{"always":[],"nonNull":[],"nonAbsent":[]}',
                    '{"always":[],"nonNull":[],"nonAbsent":[]}',
                    '{"always":[],"nonNull":[],"nonAbsent":[]}',
                    '{"always":[],"nonNull":[],"nonAbsent":[]}'
            ]
            emptyStringArrayValue << [
                    '{"always":[""],"nonNull":[""],"nonAbsent":[""],"nonEmpty":[""]}',
                    '{"always":[""],"nonNull":[""],"nonAbsent":[""],"nonEmpty":[""]}',
                    '{"always":[""],"nonNull":[""],"nonAbsent":[""],"nonEmpty":[""]}',
                    '{"always":[""],"nonNull":[""],"nonAbsent":[""],"nonEmpty":[""]}'
            ]
            nullsArrayValue << [
                    '{"always":[null],"nonNull":[null],"nonAbsent":[null],"nonEmpty":[null]}',
                    '{"always":[null],"nonNull":[null],"nonAbsent":[null],"nonEmpty":[null]}',
                    '{"always":[null],"nonNull":[null],"nonAbsent":[null],"nonEmpty":[null]}',
                    '{"always":[null],"nonNull":[null],"nonAbsent":[null],"nonEmpty":[null]}'
            ]
            nullArrayValue << [
                    '{"always":null}',
                    '{"always":null}',
                    '{"always":null}',
                    '{"always":null}'
            ]
    }

    void "@JsonInclude on array"() {
        given:
            def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String[] always;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String[] nonNull;
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public String[] nonAbsent;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String[] nonEmpty;
}
''')
            def setList = newInstance(context, 'example.Test')
            setList.always = ["foo", "bar"] as String[]
            setList.nonNull = ["foo", "bar"] as String[]
            setList.nonAbsent = ["foo", "bar"] as String[]
            setList.nonAbsent = ["foo", "bar"] as String[]
            setList.nonEmpty = ["foo", "bar"] as String[]

            def emptyList = newInstance(context, 'example.Test')
            emptyList.always = [] as String[]
            emptyList.nonNull = [] as String[]
            emptyList.nonAbsent = [] as String[]
            emptyList.nonEmpty = [] as String[]

            def nullList = newInstance(context, 'example.Test')
            nullList.always = null
            nullList.nonNull = null
            nullList.nonAbsent = null
            nullList.nonEmpty = null

        expect:
            writeJson(jsonMapper, setList) == '{"always":["foo","bar"],"nonNull":["foo","bar"],"nonAbsent":["foo","bar"],"nonEmpty":["foo","bar"]}'
            writeJson(jsonMapper, emptyList) == '{"always":[],"nonNull":[],"nonAbsent":[]}'
            writeJson(jsonMapper, nullList) == '{"always":null}'
    }

    @Unroll
    void "test basic deserialize #result of type #type"() {
        given:
            def context = buildContext('test.Test', """
package test;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.*;

@com.fasterxml.jackson.annotation.JsonClassDescription
class Test {
    private $type value;
    public void setValue($type value) {
        this.value = value;
    }
    public $type getValue() {
        return value;
    }
}
""", data)
        when:
            def bean = jsonMapper.readValue(result, beanUnderTest.class)
        then:
            bean.value == data.value

        cleanup:
            context.close()

        where:
            type                           | data                            | result
            "Optional<String>"             | [value: Optional.empty()]       | '{"value":null}'
            "OptionalInt"                  | [value: OptionalInt.empty()]    | '{"value":null}'
            "OptionalDouble"               | [value: OptionalDouble.empty()] | '{"value":null}'
            "OptionalLong"                 | [value: OptionalLong.empty()]   | '{"value":null}'
            "List<String>"                 | [value: ["Test"]]               | '{"value":["Test"]}'
            "Optional<String>"             | [value: Optional.of("Test")]    | '{"value":"Test"}'
            "List<? extends CharSequence>" | [value: ["Test"]]               | '{"value":["Test"]}'
            "List<Boolean>"                | [value: [true]]                 | '{"value":[true]}'
            "Iterable<String>"             | [value: ["Test"]]               | '{"value":["Test"]}'
            "Iterable<Boolean>"            | [value: [true]]                 | '{"value":[true]}'
            "Set<String>"                  | [value: ["Test"] as Set]        | '{"value":["Test"]}'
            "Set<Boolean>"                 | [value: [true] as Set]          | '{"value":[true]}'
            "Collection<String>"           | [value: ["Test"]]               | '{"value":["Test"]}'
            "Collection<Boolean>"          | [value: [true]]                 | '{"value":[true]}'
            "Map<String, Boolean>"         | [value: [foo: true]]            | '{"value":{"foo":true}}'
            "Collection<Boolean>"          | [value: []]                     | '{"value":[]}'
            "Map<String, Boolean>"         | [value: [:]]                    | '{"value":{}}'
            "Collection<Boolean>"          | [value: null]                   | '{}'
            "Map<String, Boolean>"         | [value: null]                   | '{}'
    }

    @Unroll
    void "test serialize #data of type #type with include #include"() {
        given:
            def context = buildContext('test.Test', """
package test;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.*;

@com.fasterxml.jackson.annotation.JsonClassDescription
class Test {
    @JsonInclude(${include.name()})
    private $type value;
    public void setValue($type value) {
        this.value = value;
    }
    public $type getValue() {
        return value;
    }
}
""", data)
        when:
            def bean = newInstance(context, "test.Test")
            bean.value = data.value
            def json = jsonMapper.writeValueAsString(bean)
        then:
            json == result

        cleanup:
            context.close()

        where:
            include    | type                           | data                            | result
            NON_ABSENT | "Optional<String>"             | [value: null]                   | '{}'
            NON_ABSENT | "OptionalInt"                  | [value: null]                   | '{}'
            NON_ABSENT | "OptionalDouble"               | [value: null]                   | '{}'
            NON_ABSENT | "OptionalLong"                 | [value: null]                   | '{}'
            NON_ABSENT | "Optional<String>"             | [value: Optional.empty()]       | '{}'
            NON_ABSENT | "OptionalInt"                  | [value: OptionalInt.empty()]    | '{}'
            NON_ABSENT | "OptionalDouble"               | [value: OptionalDouble.empty()] | '{}'
            NON_ABSENT | "OptionalLong"                 | [value: OptionalLong.empty()]   | '{}'
            NON_ABSENT | "List<String>"                 | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_ABSENT | "Optional<String>"             | [value: Optional.of("Test")]    | '{"value":"Test"}'
            NON_ABSENT | "List<? extends CharSequence>" | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_ABSENT | "List<Boolean>"                | [value: [true]]                 | '{"value":[true]}'
            NON_ABSENT | "Iterable<String>"             | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_ABSENT | "Iterable<Boolean>"            | [value: [true]]                 | '{"value":[true]}'
            NON_ABSENT | "Set<String>"                  | [value: ["Test"] as Set]        | '{"value":["Test"]}'
            NON_ABSENT | "Set<Boolean>"                 | [value: [true] as Set]          | '{"value":[true]}'
            NON_ABSENT | "Collection<String>"           | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_ABSENT | "Collection<Boolean>"          | [value: [true]]                 | '{"value":[true]}'
            NON_ABSENT | "Map<String, Boolean>"         | [value: [foo: true]]            | '{"value":{"foo":true}}'
            NON_ABSENT | "Collection<Boolean>"          | [value: []]                     | '{"value":[]}'
            NON_ABSENT | "Map<String, Boolean>"         | [value: [:]]                    | '{"value":{}}'
            NON_ABSENT | "Collection<Boolean>"          | [value: null]                   | '{}'
            NON_ABSENT | "Map<String, Boolean>"         | [value: null]                   | '{}'

            NON_NULL   | "Optional<String>"             | [value: null]                   | '{}'
            NON_NULL   | "OptionalInt"                  | [value: null]                   | '{}'
            NON_NULL   | "OptionalDouble"               | [value: null]                   | '{}'
            NON_NULL   | "OptionalLong"                 | [value: null]                   | '{}'
            NON_NULL   | "Optional<String>"             | [value: Optional.empty()]       | '{"value":null}'
            NON_NULL   | "OptionalInt"                  | [value: OptionalInt.empty()]    | '{"value":null}'
            NON_NULL   | "OptionalDouble"               | [value: OptionalDouble.empty()] | '{"value":null}'
            NON_NULL   | "OptionalLong"                 | [value: OptionalLong.empty()]   | '{"value":null}'
            NON_NULL   | "List<String>"                 | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_NULL   | "Optional<String>"             | [value: Optional.of("Test")]    | '{"value":"Test"}'
            NON_NULL   | "List<? extends CharSequence>" | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_NULL   | "List<Boolean>"                | [value: [true]]                 | '{"value":[true]}'
            NON_NULL   | "Iterable<String>"             | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_NULL   | "Iterable<Boolean>"            | [value: [true]]                 | '{"value":[true]}'
            NON_NULL   | "Set<String>"                  | [value: ["Test"] as Set]        | '{"value":["Test"]}'
            NON_NULL   | "Set<Boolean>"                 | [value: [true] as Set]          | '{"value":[true]}'
            NON_NULL   | "Collection<String>"           | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_NULL   | "Collection<Boolean>"          | [value: [true]]                 | '{"value":[true]}'
            NON_NULL   | "Map<String, Boolean>"         | [value: [foo: true]]            | '{"value":{"foo":true}}'
            NON_NULL   | "Collection<Boolean>"          | [value: []]                     | '{"value":[]}'
            NON_NULL   | "Map<String, Boolean>"         | [value: [:]]                    | '{"value":{}}'
            NON_NULL   | "Collection<Boolean>"          | [value: null]                   | '{}'
            NON_NULL   | "Map<String, Boolean>"         | [value: null]                   | '{}'

            NON_EMPTY  | "Optional<String>"             | [value: null]                   | '{}'
            NON_EMPTY  | "OptionalInt"                  | [value: null]                   | '{}'
            NON_EMPTY  | "OptionalDouble"               | [value: null]                   | '{}'
            NON_EMPTY  | "OptionalLong"                 | [value: null]                   | '{}'
            NON_EMPTY  | "Optional<String>"             | [value: Optional.empty()]       | '{}'
            NON_EMPTY  | "OptionalInt"                  | [value: OptionalInt.empty()]    | '{}'
            NON_EMPTY  | "OptionalDouble"               | [value: OptionalDouble.empty()] | '{}'
            NON_EMPTY  | "OptionalLong"                 | [value: OptionalLong.empty()]   | '{}'
            NON_EMPTY  | "List<String>"                 | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_EMPTY  | "Optional<String>"             | [value: Optional.of("Test")]    | '{"value":"Test"}'
            NON_EMPTY  | "List<? extends CharSequence>" | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_EMPTY  | "List<Boolean>"                | [value: [true]]                 | '{"value":[true]}'
            NON_EMPTY  | "Iterable<String>"             | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_EMPTY  | "Iterable<Boolean>"            | [value: [true]]                 | '{"value":[true]}'
            NON_EMPTY  | "Set<String>"                  | [value: ["Test"] as Set]        | '{"value":["Test"]}'
            NON_EMPTY  | "Set<Boolean>"                 | [value: [true] as Set]          | '{"value":[true]}'
            NON_EMPTY  | "Collection<String>"           | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_EMPTY  | "Collection<Boolean>"          | [value: [true]]                 | '{"value":[true]}'
            NON_EMPTY  | "Map<String, Boolean>"         | [value: [foo: true]]            | '{"value":{"foo":true}}'
            NON_EMPTY  | "Collection<Boolean>"          | [value: []]                     | '{}'
            NON_EMPTY  | "Map<String, Boolean>"         | [value: [:]]                    | '{}'
            NON_EMPTY  | "Collection<Boolean>"          | [value: null]                   | '{}'
            NON_EMPTY  | "Map<String, Boolean>"         | [value: null]                   | '{}'

            ALWAYS     | "Optional<String>"             | [value: Optional.empty()]       | '{"value":null}'
            ALWAYS     | "OptionalInt"                  | [value: OptionalInt.empty()]    | '{"value":null}'
            ALWAYS     | "OptionalDouble"               | [value: OptionalDouble.empty()] | '{"value":null}'
            ALWAYS     | "OptionalLong"                 | [value: OptionalLong.empty()]   | '{"value":null}'
            ALWAYS     | "Optional<String>"             | [value: Optional.empty()]       | '{"value":null}'
            ALWAYS     | "OptionalInt"                  | [value: OptionalInt.empty()]    | '{"value":null}'
            ALWAYS     | "OptionalDouble"               | [value: OptionalDouble.empty()] | '{"value":null}'
            ALWAYS     | "OptionalLong"                 | [value: OptionalLong.empty()]   | '{"value":null}'
            ALWAYS     | "List<String>"                 | [value: ["Test"]]               | '{"value":["Test"]}'
            ALWAYS     | "Optional<String>"             | [value: Optional.of("Test")]    | '{"value":"Test"}'
            ALWAYS     | "List<? extends CharSequence>" | [value: ["Test"]]               | '{"value":["Test"]}'
            ALWAYS     | "List<Boolean>"                | [value: [true]]                 | '{"value":[true]}'
            ALWAYS     | "Iterable<String>"             | [value: ["Test"]]               | '{"value":["Test"]}'
            ALWAYS     | "Iterable<Boolean>"            | [value: [true]]                 | '{"value":[true]}'
            ALWAYS     | "Set<String>"                  | [value: ["Test"] as Set]        | '{"value":["Test"]}'
            ALWAYS     | "Set<Boolean>"                 | [value: [true] as Set]          | '{"value":[true]}'
            ALWAYS     | "Collection<String>"           | [value: ["Test"]]               | '{"value":["Test"]}'
            ALWAYS     | "Collection<Boolean>"          | [value: [true]]                 | '{"value":[true]}'
            ALWAYS     | "Map<String, Boolean>"         | [value: [foo: true]]            | '{"value":{"foo":true}}'
            ALWAYS     | "Collection<Boolean>"          | [value: []]                     | '{"value":[]}'
            ALWAYS     | "Map<String, Boolean>"         | [value: [:]]                    | '{"value":{}}'
            ALWAYS     | "Collection<Boolean>"          | [value: null]                   | '{"value":null}'
            ALWAYS     | "Map<String, Boolean>"         | [value: null]                   | '{"value":null}'
    }

    @Unroll
    void "test @JsonInclude(#include) for #type with #value"() {
        given:
        def context = buildContext("""
package jsoninclude;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.*;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.*;
import java.util.*;

@Serdeable
record Test(
    @JsonInclude(${include.name()})
    $type test
) {}
""")
        def bean = newInstance(context, 'jsoninclude.Test', value)
        String json = writeJson(jsonMapper, bean)

        expect:
        json == result

        cleanup:
        context.close()

        where:
        include    | type                  | value            | result
        ALWAYS     | "String"              | ""               | '{"test":""}'
        ALWAYS     | "String"              | null             | '{"test":null}'
        ALWAYS     | "String"              | "test"           | '{"test":"test"}'
        NON_NULL   | "String"              | ""               | '{"test":""}'
        NON_NULL   | "String"              | null             | '{}'
        NON_NULL   | "String"              | "test"           | '{"test":"test"}'
        NON_ABSENT | "String"              | ""               | '{"test":""}'
        NON_ABSENT | "String"              | null             | '{}'
        NON_ABSENT | "String"              | "test"           | '{"test":"test"}'
        NON_EMPTY  | "String"              | ""               | '{}'
        NON_EMPTY  | "String"              | null             | '{}'
        NON_EMPTY  | "String"              | "test"           | '{"test":"test"}'

        ALWAYS     | "List<String>"        | []               | '{"test":[]}'
        ALWAYS     | "List<String>"        | null             | '{"test":null}'
        ALWAYS     | "List<String>"        | ["test"]         | '{"test":["test"]}'
        NON_NULL   | "List<String>"        | []               | '{"test":[]}'
        NON_NULL   | "List<String>"        | null             | '{}'
        NON_NULL   | "List<String>"        | ["test"]         | '{"test":["test"]}'
        NON_ABSENT | "List<String>"        | []               | '{"test":[]}'
        NON_ABSENT | "List<String>"        | null             | '{}'
        NON_ABSENT | "List<String>"        | ["test"]         | '{"test":["test"]}'
        NON_EMPTY  | "List<String>"        | []               | '{}'
        NON_EMPTY  | "List<String>"        | null             | '{}'
        NON_EMPTY  | "List<String>"        | ["test"]         | '{"test":["test"]}'

        ALWAYS     | "Map<String, String>" | [:]              | '{"test":{}}'
        ALWAYS     | "Map<String, String>" | null             | '{"test":null}'
        ALWAYS     | "Map<String, String>" | ["test": "test"] | '{"test":{"test":"test"}}'
        NON_NULL   | "Map<String, String>" | [:]              | '{"test":{}}'
        NON_NULL   | "Map<String, String>" | null             | '{}'
        NON_NULL   | "Map<String, String>" | ["test": "test"] | '{"test":{"test":"test"}}'
        NON_ABSENT | "Map<String, String>" | [:]              | '{"test":{}}'
        NON_ABSENT | "Map<String, String>" | null             | '{}'
        NON_ABSENT | "Map<String, String>" | ["test": "test"] | '{"test":{"test":"test"}}'
        NON_EMPTY  | "Map<String, String>" | [:]              | '{}'
        NON_EMPTY  | "Map<String, String>" | null             | '{}'
        NON_EMPTY  | "Map<String, String>" | ["test": "test"] | '{"test":{"test":"test"}}'

    }

    @Unroll
    void "test @JsonInclude(#include) on class for #type with #value"() {
        given:
        def context = buildContext("""
package jsoninclude;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.*;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.*;

@Serdeable
@JsonInclude(${include.name()})
record Test(
    $type test
) {}
""")
        def bean = newInstance(context, 'jsoninclude.Test', value)
        String json = writeJson(jsonMapper, bean)

        expect:
        json == result

        cleanup:
        context.close()

        where:
        include    | type                  | value            | result
        ALWAYS     | "String"              | ""               | '{"test":""}'
        ALWAYS     | "String"              | null             | '{"test":null}'
        ALWAYS     | "String"              | "test"           | '{"test":"test"}'
        NON_NULL   | "String"              | ""               | '{"test":""}'
        NON_NULL   | "String"              | null             | '{}'
        NON_NULL   | "String"              | "test"           | '{"test":"test"}'
        NON_ABSENT | "String"              | ""               | '{"test":""}'
        NON_ABSENT | "String"              | null             | '{}'
        NON_ABSENT | "String"              | "test"           | '{"test":"test"}'
        NON_EMPTY  | "String"              | ""               | '{}'
        NON_EMPTY  | "String"              | null             | '{}'
        NON_EMPTY  | "String"              | "test"           | '{"test":"test"}'

    }
}
