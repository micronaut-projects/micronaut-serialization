package io.micronaut.serde.jackson

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.config.SerializationConfiguration

/**
 * The serdes a mapper keeps between documents, and the fast paths of generated enums.
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

    void "enum values honour the escaping of the generator"() {
        given:
        def context = buildContext('test.Season', '''
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public enum Season {
    ÉTÉ, HIVER
}
''', true, ['micronaut.serde.jackson.json-write-features.ESCAPE_NON_ASCII': 'true'])
        def season = context.classLoader.loadClass('test.Season').getEnumConstants()[0]

        expect:
        jsonMapper.writeValueAsString(season) == '"\\u00C9T\\u00C9"'

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

    void "generated enums are read from a tree and from a stream alike"() {
        given:
        def context = buildContext('test.Holder', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record Holder(List<Status> statuses) {
}

@Serdeable
enum Status {
    NEW, OLD
}
''', true, [:])
        def type = argumentOf(context, 'test.Holder')
        def json = '{"statuses":["OLD","NEW","OLD"]}'

        when:
        def fromStream = jsonMapper.readValue(json, type)
        def fromTree = jsonMapper.readValueFromTree(jsonMapper.readValue(json, io.micronaut.json.tree.JsonNode), type)

        then:
        fromStream.statuses()*.name() == ['OLD', 'NEW', 'OLD']
        fromTree.statuses()*.name() == ['OLD', 'NEW', 'OLD']

        cleanup:
        context.close()
    }

    void "a generated bean read through an alias reports a duplicate under the alias"() {
        given:
        def context = buildContext('test.Person', '''
package test;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Person {
    @JsonAlias("nickname")
    private String name;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
''', true, ['micronaut.serde.deserialization.fail-on-duplicate-properties': true])
        def type = argumentOf(context, 'test.Person')

        expect:
        jsonMapper.readValue('{"nickname":"Jo"}', type).name == 'Jo'

        when:
        jsonMapper.readValue('{"name":"A","nickname":"B"}', type)

        then:
        def e = thrown(Exception)
        e.message.contains('nickname')

        cleanup:
        context.close()
    }

    void "a generated enum deserializer uses a user string deserializer"() {
        given:
        def context = buildContext('test.Status', '''
package test;

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.io.IOException;

@Serdeable
public enum Status {
    NEW, OLD
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
        def type = argumentOf(context, 'test.Status')

        expect:
        jsonMapper.readValue('"NEW"', type).name() == 'OLD'

        cleanup:
        context.close()
    }

    void "a specific mapper cloned with another configuration resolves its serdes again"() {
        given:
        def context = ApplicationContext.run()
        def unwrappingContext = ApplicationContext.run(['micronaut.serde.serialization.write-single-elem-arrays-unwrapped': true])
        def unwrapping = unwrappingContext.getBean(SerializationConfiguration)
        def type = Argument.mapOf(String, Object)
        JsonMapper specific = context.getBean(JsonMapper).createSpecific(type)
        def value = [items: [[1]]]

        when:
        specific.writeValueAsString(type, value)
        def warmedClone = ((ObjectMapper) specific).cloneWithConfiguration(null, unwrapping, null)
        def freshClone = ((ObjectMapper) context.getBean(JsonMapper).createSpecific(type)).cloneWithConfiguration(null, unwrapping, null)

        then:
        warmedClone.writeValueAsString(type, value) == freshClone.writeValueAsString(type, value)
        warmedClone.writeValueAsString(type, value) != '{"items":[[1]]}'

        cleanup:
        context.close()
        unwrappingContext.close()
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

    void "enum values honour every escaping feature of the generator"() {
        given:
        def context = buildContext('test.Path', '''
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public enum Path {
    @JsonProperty("a/b") SLASHED
}
''', true, ['micronaut.serde.jackson.json-write-features.ESCAPE_FORWARD_SLASHES': 'true'])
        def path = context.classLoader.loadClass('test.Path').getEnumConstants()[0]

        expect:
        jsonMapper.writeValueAsString(path) == '"a\\/b"'

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
        mapper.@serdeCache.@deserializers.get(type) != null
        mapper.readValue('[1,2]', type) == [1, 2]

        cleanup:
        context.close()
    }
}
