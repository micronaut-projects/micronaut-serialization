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
    void "a runtime bean holding a user serde that keeps the context stays bound when it is reused (#direction)"() {
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
        def type = argumentOf(context, 'test.Holder')

        expect: 'the second document reuses the cached bean, and is still marked bound so that a mapper does not cache it'
        (1..2).every {
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
        direction << ['serialize', 'deserialize']
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
