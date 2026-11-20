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
        def expected = context.getBean(JsonMapper).cloneWithViewClass(Object).writeValueAsString(variable, child)

        when:
        jsonMapper.writeValueAsString(Argument.of(base, 'x'), child)

        then:
        jsonMapper.writeValueAsString(variable, child) == expected

        cleanup:
        context.close()
    }
}
