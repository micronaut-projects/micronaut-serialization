package io.micronaut.serde.jackson

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.config.SerializationConfiguration

/**
 * The fast paths of generated enums and beans, and the serdes of a specific mapper cloned with another configuration.
 */
class SerdeFastPathSpec extends JsonCompileSpec {

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

    void "a generated enum deserializer uses a user string deserializer whose specific deserializer is another class"() {
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
class CustomizableStringDeserializer implements Deserializer<String> {
    @Override
    public Deserializer<String> createSpecific(DecoderContext context, Argument<? super String> type) {
        return new RenamingStringDeserializer();
    }

    @Override
    public String deserialize(Decoder decoder, DecoderContext context, Argument<? super String> type) throws IOException {
        throw new IOException("Specific deserializer expected");
    }
}

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

    void "a generated enum deserializer uses a user string deserializer created with the context"() {
        given:
        def context = buildContext('test.Status', '''
package test;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.Serdeable;
import java.io.IOException;

@Serdeable
public enum Status {
    NEW, OLD
}

@Prototype
@Primary
class RenamingStringDeserializer implements Deserializer<String> {
    RenamingStringDeserializer(@Parameter DecoderContext context, @Parameter Argument<?> type) {
    }

    @Override
    public String deserialize(Decoder decoder, DecoderContext context, Argument<? super String> type) throws IOException {
        String value = decoder.decodeString();
        return "NEW".equals(value) ? "OLD" : value;
    }
}
''', true, [:])
        def type = argumentOf(context, 'test.Status')

        expect:
        // the registry resolves the built-in string serde over a string deserializer created with the context, as on
        // 3.3.x, and the enum reads its values with the same string deserializer as a string property
        jsonMapper.readValue('"NEW"', type).name() == jsonMapper.readValue('"NEW"', String)

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
}
