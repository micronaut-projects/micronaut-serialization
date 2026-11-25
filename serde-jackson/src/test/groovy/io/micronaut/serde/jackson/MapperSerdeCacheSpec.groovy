package io.micronaut.serde.jackson

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.config.SerializationConfiguration

/**
 * The serdes a mapper keeps between documents.
 */
class MapperSerdeCacheSpec extends JsonCompileSpec {

    void "a user serde whose specific serde keeps the context is not reused by the next document"() {
        given:
        def context = buildContext('test.Token', '''
package test;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import jakarta.inject.Singleton;
import java.io.IOException;

public record Token(String value) {
}

@Singleton
class TokenSerializer implements Serializer<Token> {
    @Override
    public Serializer<Token> createSpecific(EncoderContext context, Argument<? extends Token> type) {
        return new Specific(context);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Token> type, Token value) throws IOException {
        throw new IOException("Specific serializer expected");
    }

    private record Specific(EncoderContext createdWith) implements Serializer<Token> {
        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Token> type, Token value) throws IOException {
            if (createdWith != context) {
                throw new IOException("Used with the context of another document");
            }
            encoder.encodeString(value.value());
        }
    }
}
''', true, [:])
        def token = context.classLoader.loadClass('test.Token').newInstance('a')

        expect:
        jsonMapper.writeValueAsString(token) == '"a"'
        jsonMapper.writeValueAsString(token) == '"a"'

        cleanup:
        context.close()
    }

    void "documents written in parallel with a user serde keeping the context each use their own"() {
        given:
        def context = buildContext('test.Holder', '''
package test;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.List;

@Serdeable
public record Holder(String name, List<Token> tokens) {
}

record Token(String value) {
}

@Singleton
class TokenSerializer implements Serializer<Token> {
    @Override
    public Serializer<Token> createSpecific(EncoderContext context, Argument<? extends Token> type) {
        return new Specific(context);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Token> type, Token value) throws IOException {
        throw new IOException("Specific serializer expected");
    }

    private record Specific(EncoderContext createdWith) implements Serializer<Token> {
        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Token> type, Token value) throws IOException {
            if (createdWith != context) {
                throw new IOException("Used with the context of another document");
            }
            encoder.encodeString(value.value());
        }
    }
}
''', true, [:])
        def holderClass = context.classLoader.loadClass('test.Holder')
        def tokenClass = context.classLoader.loadClass('test.Token')
        def pool = java.util.concurrent.Executors.newFixedThreadPool(8)

        when:
        def results = pool.invokeAll((1..400).collect { i ->
            (java.util.concurrent.Callable<Boolean>) {
                def holder = holderClass.newInstance("h$i".toString(), [tokenClass.newInstance("a$i".toString()), tokenClass.newInstance("b$i".toString())])
                jsonMapper.writeValueAsString(holder) == "{\"name\":\"h$i\",\"tokens\":[\"a$i\",\"b$i\"]}"
            }
        }).collect { it.get() }

        then:
        results.every()

        cleanup:
        pool.shutdown()
        context.close()
    }

    @spock.lang.Unroll
    void "a runtime #shape holding a user serde that keeps the context stays bound when it is reused (#direction)"() {
        given:
        def context = buildContext('test.Holder', '''
package test;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.io.IOException;

@Serdeable
public class Holder {
    private Token token;
    public Token getToken() { return token; }
    public void setToken(Token token) { this.token = token; }
}

record Token(String value) {
}

@Serdeable
@com.fasterxml.jackson.annotation.JsonTypeInfo(use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME, property = "type")
@com.fasterxml.jackson.annotation.JsonSubTypes({
    @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = Circle.class, name = "circle"),
    @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = Square.class, name = "square")
})
interface Shape {
}

@Serdeable
record Circle(Token token) implements Shape {
}

@Serdeable
record Square(String side) implements Shape {
}

@Serdeable
record Inner(Token token) {
}

@Serdeable
record Wrapper(@com.fasterxml.jackson.annotation.JsonUnwrapped Inner inner) {
}

@Serdeable
class First {
    private Token token;
    private Second second;
    public Token getToken() { return token; }
    public void setToken(Token token) { this.token = token; }
    public Second getSecond() { return second; }
    public void setSecond(Second second) { this.second = second; }
}

@Serdeable
class Second {
    private First first;
    public First getFirst() { return first; }
    public void setFirst(First first) { this.first = first; }
}

@Singleton
class TokenSerde implements Serializer<Token>, Deserializer<Token> {
    @Override
    public Serializer<Token> createSpecific(EncoderContext context, Argument<? extends Token> type) {
        return new Specific(context);
    }

    @Override
    public Deserializer<Token> createSpecific(DecoderContext context, Argument<? super Token> type) {
        return new SpecificDeserializer(context);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Token> type, Token value) throws IOException {
        throw new IOException("Specific serializer expected");
    }

    @Override
    public Token deserialize(Decoder decoder, DecoderContext context, Argument<? super Token> type) throws IOException {
        throw new IOException("Specific deserializer expected");
    }

    private record Specific(EncoderContext createdWith) implements Serializer<Token> {
        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Token> type, Token value) throws IOException {
            encoder.encodeString(value.value());
        }
    }

    private record SpecificDeserializer(DecoderContext createdWith) implements Deserializer<Token> {
        @Override
        public Token deserialize(Decoder decoder, DecoderContext context, Argument<? super Token> type) throws IOException {
            return new Token(decoder.decodeString());
        }
    }
}
''', true, [
            'micronaut.serde.serialization.disable-generated-serializer': true,
            'micronaut.serde.deserialization.disable-generated-deserializer': true
        ])
        def registry = context.getBean(io.micronaut.serde.SerdeRegistry)
        def types = documentTypes.collect { argumentOf(context, it) }

        expect: 'the second document reuses the cached beans, and is still marked bound so that a mapper does not cache it'
        types.every { type ->
            def documentContext = direction == 'serialize' ? registry.newEncoderContext(null) : registry.newDecoderContext(null)
            // the runtime bean serdes, which keep the bean of a type between documents
            if (direction == 'serialize') {
                context.getBean(io.micronaut.serde.support.serializers.ObjectSerializer).createSpecific(documentContext, type)
            } else {
                context.getBean(io.micronaut.serde.support.deserializers.ObjectDeserializer).createSpecific(documentContext, type)
            }
            io.micronaut.serde.util.DocumentContextTracker.isDocumentBound(documentContext)
        }

        cleanup:
        context.close()

        where:
        [direction, shape, documentTypes] << [['serialize', 'deserialize'], [
            ['bean', ['test.Holder', 'test.Holder']],
            ['subtyped bean', ['test.Shape', 'test.Shape']],
            ['unwrapped creator parameter', ['test.Wrapper', 'test.Wrapper']],
            ['cycle of beans', ['test.First', 'test.Second']]
        ]].combinations()
            // a subtyped bean is written by the serializer of each value's runtime type, resolved when it is written
            .findAll { d, s -> !(d == 'serialize' && s[0] == 'subtyped bean') }
            .collect { d, s -> [d, s[0], s[1]] }
    }

    @spock.lang.Unroll
    void "an element type variable and a declared element type are cached apart (#order)"() {
        given:
        def context = buildContext('test.Base', '''
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Base {
    private String name;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

@Serdeable
class Child extends Base {
    private String extra;
    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
}
''', true, [
            'micronaut.serde.serialization.disable-generated-serializer': true,
            'micronaut.serde.deserialization.disable-generated-deserializer': true
        ])
        def base = context.classLoader.loadClass('test.Base')
        def child = context.classLoader.loadClass('test.Child').newInstance()
        child.name = 'a'
        child.extra = 'x'
        def byRuntimeType = Argument.of(List, base)
        def byDeclaredType = Argument.listOf(base)
        def arguments = order == 'type variable first' ? [byRuntimeType, byDeclaredType] : [byDeclaredType, byRuntimeType]

        expect: 'each argument keeps the serializer of its own shape'
        (1..2).every {
            arguments.every { argument ->
                jsonMapper.writeValueAsString(argument, [child]) == (argument.is(byRuntimeType) ? '[{"name":"a","extra":"x"}]' : '[{"name":"a"}]')
            }
        }

        cleanup:
        context.close()

        where:
        order << ['type variable first', 'declared type first']
    }

    void "a polymorphic type with a generic subtype resolves that subtype with the context of each document"() {
        given:
        def context = buildContext('test.Result', '''
package test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.io.IOException;

@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = Err.class, name = "err")
})
public sealed interface Result<T> permits Ok, Err {
}

@Serdeable
record Ok<T>(T value) implements Result<T> {
}

@Serdeable
record Err(String message) implements Result<Object> {
}

record Token(String value) {
}

@Singleton
class TokenDeserializer implements Deserializer<Token> {
    @Override
    public Deserializer<Token> createSpecific(DecoderContext context, Argument<? super Token> type) {
        return new Specific(context);
    }

    @Override
    public Token deserialize(Decoder decoder, DecoderContext context, Argument<? super Token> type) throws IOException {
        throw new IOException("Specific deserializer expected");
    }

    private record Specific(DecoderContext createdWith) implements Deserializer<Token> {
        @Override
        public Token deserialize(Decoder decoder, DecoderContext context, Argument<? super Token> type) throws IOException {
            if (createdWith != context) {
                throw new IOException("Used with the context of another document");
            }
            return new Token(decoder.decodeString());
        }
    }
}
''', true, [
            'micronaut.serde.serialization.disable-generated-serializer': true,
            'micronaut.serde.deserialization.disable-generated-deserializer': true
        ])
        def type = Argument.of(context.classLoader.loadClass('test.Result'), context.classLoader.loadClass('test.Token'))

        when: 'the first document needs no generic subtype, the second does'
        def err = jsonMapper.readValue('{"type":"err","message":"m"}', type)
        def ok = jsonMapper.readValue('{"type":"ok","value":"v"}', type)

        then:
        err.message() == 'm'
        ok.value().value() == 'v'

        cleanup:
        context.close()
    }

    void "a type variable is not served the cached serializer of its concrete type"() {
        given:
        def context = buildContext('test.Base', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class Base {
    private String name;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

@Introspected
class Child extends Base {
    private int age;
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
}
''', true, [
            'micronaut.serde.serialization.disable-generated-serializer': true,
            'micronaut.serde.deserialization.disable-generated-deserializer': true
        ])
        def base = context.classLoader.loadClass('test.Base')
        def child = context.classLoader.loadClass('test.Child').newInstance()
        child.name = 'n'
        child.age = 3
        Argument variable = Argument.ofTypeVariable(base, 'x')

        when:
        def concrete = jsonMapper.writeValueAsString(Argument.of(base, 'x'), child)
        def runtime = jsonMapper.writeValueAsString(variable, child)

        then: 'the declared type writes its own properties, the type variable the properties of the runtime type'
        !concrete.contains('"age"')
        runtime.contains('"age":3')
        runtime.contains('"name":"n"')

        cleanup:
        context.close()
    }

    void "a user serde in a package of Micronaut Serialization is still a user serde"() {
        given:
        def context = buildContext('io.micronaut.serde.extensions.Token', '''
package io.micronaut.serde.extensions;

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.io.IOException;

public record Token(String value) {
}

@Serdeable
enum Status {
    NEW, OLD
}

@Singleton
class TokenSerializer implements Serializer<Token> {
    @Override
    public Serializer<Token> createSpecific(EncoderContext context, Argument<? extends Token> type) {
        return new Specific(context);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Token> type, Token value) throws IOException {
        throw new IOException("Specific serializer expected");
    }

    private record Specific(EncoderContext createdWith) implements Serializer<Token> {
        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Token> type, Token value) throws IOException {
            if (createdWith != context) {
                throw new IOException("Used with the context of another document");
            }
            encoder.encodeString(value.value());
        }
    }
}

@Singleton
@Primary
class RenamingStringDeserializer implements Deserializer<String> {
    @Override
    public String deserialize(Decoder decoder, DecoderContext context, Argument<? super String> type) throws IOException {
        String value = decoder.decodeString();
        return "NEW".equals(value) ? "OLD" : value;
    }
}
''', true, [:])
        def token = context.classLoader.loadClass('io.micronaut.serde.extensions.Token').newInstance('a')
        def status = argumentOf(context, 'io.micronaut.serde.extensions.Status')

        expect:
        jsonMapper.writeValueAsString(token) == '"a"'
        jsonMapper.writeValueAsString(token) == '"a"'
        jsonMapper.readValue('"NEW"', status).name() == 'OLD'

        cleanup:
        context.close()
    }

    void "a typed collection is read with a cached deserializer"() {
        given:
        def context = ApplicationContext.run()
        def mapper = context.getBean(JsonMapper)
        def type = Argument.of(ArrayList, Integer)

        when:
        mapper.readValue('[1]', type)

        then:
        mapper.@deserializers.containsKey(type)
        mapper.readValue('[1,2]', type) == [1, 2]

        cleanup:
        context.close()
    }
}
