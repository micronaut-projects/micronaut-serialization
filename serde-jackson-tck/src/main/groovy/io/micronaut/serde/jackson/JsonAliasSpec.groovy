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

abstract class JsonAliasSpec extends JsonCompileSpec {

    void "aliases"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonAlias("bar")
    public String foo;
}
''')

        expect:
        jsonMapper.readValue('{"foo": "42"}', typeUnderTest).foo == '42'
        jsonMapper.readValue('{"bar": "42"}', typeUnderTest).foo == '42'

        cleanup:
        context.close()
    }

    void 'test JsonAlias with simple properties'() {
        given:
        def context = buildContext('''
package jsonviews;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Item {
    @JsonAlias({"itm", "it"})
    public String itemName;
}

''')
        def item = newInstance(context, 'jsonviews.Item')
        item.itemName = 'Apple'

        when:
        def defaultResult = writeJson(jsonMapper, item)

        then:
        defaultResult == '{"itemName":"Apple"}'

        when:
        def read = jsonMapper.readValue(defaultResult, argumentOf(context, 'jsonviews.Item'))

        then:
        read.itemName == 'Apple'

        when:
        read = jsonMapper.readValue('{"itm":"Apple"}', argumentOf(context, 'jsonviews.Item'))

        then:
        read.itemName == 'Apple'

        when:
        read = jsonMapper.readValue('{"it":"Apple"}', argumentOf(context, 'jsonviews.Item'))

        then:
        read.itemName == 'Apple'

        cleanup:
        context.close()
    }

    void 'test JsonAlias with records'() {
        given:
        def context = buildContext('''
package jsonviews;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Item(
    @JsonAlias({"itm", "it"})
    String itemName
) {}

''')
        def item = newInstance(context, 'jsonviews.Item', "Apple")

        when:
        def defaultResult = writeJson(jsonMapper, item)

        then:
        defaultResult == '{"itemName":"Apple"}'

        when:
        def read = jsonMapper.readValue(defaultResult, argumentOf(context, 'jsonviews.Item'))

        then:
        read.itemName == 'Apple'

        when:
        read = jsonMapper.readValue('{"itm":"Apple"}', argumentOf(context, 'jsonviews.Item'))

        then:
        read.itemName == 'Apple'

        when:
        read = jsonMapper.readValue('{"it":"Apple"}', argumentOf(context, 'jsonviews.Item'))

        then:
        read.itemName == 'Apple'

        cleanup:
        context.close()
    }
}
