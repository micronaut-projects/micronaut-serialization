package io.micronaut.serde.jackson

import spock.lang.Ignore

abstract class JsonViewSpec extends JsonCompileSpec {

    @Ignore // TODO: Align with Jackson
    def 'simple views'() {
        given:
        def ctx = buildContext('''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonView(Public.class)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class WithViews {
    public String firstName;
    public String lastName;
    @JsonView(Internal.class)
    public String birthdate;
    @JsonView(Admin.class)
    public String password; // don't do plaintext passwords at home please
}

class Public {}

class Internal extends Public {}

class Admin extends Internal {}
''')

        def withViews = newInstance(ctx, 'example.WithViews')
        withViews.firstName = 'Bob'
        withViews.lastName = 'Jones'
        withViews.birthdate = '08/01/1980'
        withViews.password = 'secret'

        def viewPublic = ctx.classLoader.loadClass('example.Public')
        def viewInternal = ctx.classLoader.loadClass('example.Internal')
        def viewAdmin = ctx.classLoader.loadClass('example.Admin')

        expect:
        serializeToString(jsonMapper, withViews, viewAdmin) ==
                '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}'
        serializeToString(jsonMapper, withViews, viewInternal) ==
                '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980"}'
        serializeToString(jsonMapper, withViews, viewPublic) ==
                '{"firstName":"Bob","lastName":"Jones"}'
        serializeToString(jsonMapper, withViews) == '{}'

        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}')
                .firstName == null

        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewPublic)
                .firstName == 'Bob'
        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewPublic)
                .birthdate == null

        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewInternal)
                .firstName == 'Bob'
        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewInternal)
                .birthdate == '08/01/1980'
        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewInternal)
                .password == null

        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewAdmin)
                .firstName == 'Bob'
        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewAdmin)
                .birthdate == '08/01/1980'
        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewAdmin)
                .password == 'secret'

        cleanup:
        ctx.close()
    }

    def 'unwrapped view'() {
        given:
        def ctx = buildContext('example.Outer', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Outer {
    public String a;
    @JsonView(Runnable.class)
    @JsonUnwrapped public Nested nested;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Nested {
    public String b;
}
''')

        def outer = newInstance(ctx, 'example.Outer')
        outer.a = 'a'
        outer.nested = newInstance(ctx, 'example.Nested')
        outer.nested.b = 'b'

        expect:
        serializeToString(jsonMapper, outer) ==  '{"a":"a","b":"b"}'
        // abuse Runnable as the view class
        serializeToString(jsonMapper, outer, Runnable) == '{"a":"a","b":"b"}'

        jsonMapper.readValue('{"a":"a","b":"b"}', typeUnderTest).nested?.b == 'b'
        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.Outer"), '{"a":"a","b":"b"}', Runnable).nested.b == 'b'
    }

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
