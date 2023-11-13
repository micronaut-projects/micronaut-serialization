package io.micronaut.serde.jackson

abstract class JsonViewSpec extends JsonCompileSpec {

    void 'test JsonView with simple properties'() {
        given:
        def context = buildContext('''
package jsonviews;

import com.fasterxml.jackson.annotation.JsonView;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.jackson.Views;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Item {

    @JsonView(Views.Public.class)
    public int id;

    @JsonView(Views.Public.class)
    public String itemName;

    @JsonView(Views.Internal.class)
    public String ownerName;
}

''')
        def item = newInstance(context, 'jsonviews.Item')
        item.id = 10
        item.itemName = 'Apple'
        item.ownerName = 'Fred'

        when:
        def publicMapper = jsonMapper.cloneWithViewClass(Views.Public)
        def internalMapper = jsonMapper.cloneWithViewClass(Views.Internal)
        def defaultResult = writeJson(jsonMapper, item)
        def publicResult = writeJson(publicMapper, item)
        def internalResult = writeJson(internalMapper, item)

        then:
        publicResult != defaultResult
        publicResult != internalResult
        publicResult == '{"id":10,"itemName":"Apple"}'
        defaultResult == '{"id":10,"itemName":"Apple","ownerName":"Fred"}'
        internalResult == '{"id":10,"itemName":"Apple","ownerName":"Fred"}'

        when:
        def read = publicMapper.readValue(internalResult, argumentOf(context, 'jsonviews.Item'))

        then:
        read.id
        read.itemName
        read.ownerName == null

        cleanup:
        context.close()
    }

    void 'test JsonView with records'() {
        given:
        def context = buildContext('''
package jsonviews;

import com.fasterxml.jackson.annotation.JsonView;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.jackson.Views;

@Serdeable
record Item(

    @JsonView(Views.Public.class)
    int id,

    @JsonView(Views.Public.class)
    String itemName,

    @JsonView(Views.Internal.class)
    @Nullable
    String ownerName
) {}

''')
        def item = newInstance(context, 'jsonviews.Item', 10, "Apple", "Fred")

        when:
        def publicMapper = jsonMapper.cloneWithViewClass(Views.Public)
        def internalMapper = jsonMapper.cloneWithViewClass(Views.Internal)
        def defaultResult = writeJson(jsonMapper, item)
        def publicResult = writeJson(publicMapper, item)
        def internalResult = writeJson(internalMapper, item)

        then:
        publicResult != defaultResult
        publicResult != internalResult
        publicResult == '{"id":10,"itemName":"Apple"}'
        defaultResult == '{"id":10,"itemName":"Apple","ownerName":"Fred"}'
        internalResult == '{"id":10,"itemName":"Apple","ownerName":"Fred"}'

        when:
        def read = publicMapper.readValue(internalResult, argumentOf(context, 'jsonviews.Item'))

        then:
        read.id
        read.itemName
        read.ownerName == null

        cleanup:
        context.close()
    }
}

class Views {
    static class Public {
    }

    static class Internal extends Public {
    }
}
