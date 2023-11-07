package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonCompileSpec

class JsonAliasSpec extends JsonCompileSpec {
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
