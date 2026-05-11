package io.micronaut.serde.jackson.annotation


import io.micronaut.serde.jackson.JsonSerializeDeserializeSpec

class SerdeJsonSerializeDeserializeSpec extends JsonSerializeDeserializeSpec {

    void 'test errors'() {
        when:
            buildContext('test.Test', """
package test;

import tools.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.serde.annotation.Serdeable;
import java.util.LinkedList;
import java.util.List;
import java.time.LocalDate;

@Serdeable.Deserializable(as = LinkedList.class)
@Serdeable.Serializable(as = LocalDate.class)
public interface Test {}

""")
        then:
        def e = thrown(RuntimeException)
        e.message.contains "Type to serialize as [java.time.LocalDate], must be a subtype of the annotated type: test.Test"
    }

    void 'test json deserialize on collection'() {
        given:
        def context = buildContext('test.Test', """
package test;

import tools.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.serde.annotation.Serdeable;
import java.util.LinkedList;
import java.util.List;

@Serdeable.Deserializable
record Test(
    @JsonDeserialize(as = LinkedList.class) List<Integer> list
) {}

""")

        when:
        def result = jsonMapper.readValue('{"list": [1, 2, 3]}', typeUnderTest);
        then:
        result.getClass().name == 'test.Test'
        result.list instanceof LinkedList
        result.list == [1, 2, 3] as LinkedList

        cleanup:
        context.close()
    }

    void 'test basic json deserialize on collection'() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.LinkedList;
import java.util.List;

@Serdeable.Deserializable
record Test(
    @Serdeable.Deserializable(as = LinkedList.class) List<Integer> list
) {}

""")

        when:
        def result = jsonMapper.readValue('{"list": [1, 2, 3]}', typeUnderTest);
        then:
        result.getClass().name == 'test.Test'
        result.list instanceof LinkedList
        result.list == [1, 2, 3] as LinkedList

        cleanup:
        context.close()
    }

    void 'test json deserialize primitives'() {
        given:
            def context = buildContext('test.CustomIntegerDeserializer', """
package test;

import java.io.IOException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.util.LinkedList;
import java.util.List;

@Singleton
class CustomIntegerDeserializer implements Deserializer<Integer> {
    @Override
    public @Nullable Integer deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super Integer> type) throws IOException {
        return decoder.decodeInt();
    }
}

@Serdeable.Deserializable
record RecordWithPrimitive(int value) {}

@Serdeable.Deserializable
record RecordWithBoxed(Integer value) {}

""")

        when:
            def resultPrimitive = jsonMapper.readValue('{"value": 123}', context.getClassLoader().loadClass("test.RecordWithPrimitive"))
            def resultBoxed = jsonMapper.readValue('{"value": 123}', context.getClassLoader().loadClass("test.RecordWithBoxed"))
        then:
            resultPrimitive.value == 123
            resultBoxed.value == 123

        cleanup:
            context.close()
    }

    void 'test json deserialize a custom container'() {
        given:
            def context = buildContext('test.SomeModel', """
package test;

import java.io.IOException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.util.LinkedList;
import java.util.List;

record Something(String s) {}

@Serdeable.Deserializable
record SomeModel(List<Something> specificList, List<String> genericList) {}

@Singleton
class ListSomethingDeserializer implements Deserializer<List<Something>> {
    @Override
    public @Nullable List<Something> deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super List<Something>> type) throws IOException {
        var stringValue = decoder.decodeString();
        return java.util.Arrays.stream(stringValue.split("\\\\|"))
            .map(Something::new)
            .toList();
    }
}
""")

        when:
            def result = jsonMapper.readValue("""{
                "specificList": "a|b|c",
                "genericList": ["a", "b", "c"]
            }""", context.getClassLoader().loadClass("test.SomeModel"))
        then:
            result

        cleanup:
            context.close()
    }

    void 'test #scopeAnnotation serializer and deserializer bean constructors receive context and argument'() {
        given:
            def context = buildContext('test.ContextualValue', """
package test;

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.io.IOException;

@Serdeable
public record ContextualValue(String value) {
}

@Primary
@${scopeAnnotation}
final class ContextualValueSerializer implements Serializer<ContextualValue> {
    private final Serializer.EncoderContext constructorContext;
    private final Argument<? extends ContextualValue> constructorType;

    ContextualValueSerializer(@Parameter Serializer.EncoderContext constructorContext,
                              @Parameter Argument<? extends ContextualValue> constructorType) {
        this.constructorContext = constructorContext;
        this.constructorType = constructorType;
    }

    @Override
    public void serialize(Encoder encoder,
                          Serializer.EncoderContext context,
                          Argument<? extends ContextualValue> type,
                          ContextualValue value) throws IOException {
        if (constructorContext != context) {
            throw new IOException("EncoderContext was not passed to the serializer constructor");
        }
        if (constructorType != type) {
            throw new IOException("Argument was not passed to the serializer constructor");
        }
        encoder.encodeString(type.getType().getSimpleName() + ":" + value.value());
    }
}

@Primary
@${scopeAnnotation}
final class ContextualValueDeserializer implements Deserializer<ContextualValue> {
    private final Deserializer.DecoderContext constructorContext;
    private final Argument<? super ContextualValue> constructorType;

    ContextualValueDeserializer(@Parameter Deserializer.DecoderContext constructorContext,
                                @Parameter Argument<? super ContextualValue> constructorType) {
        this.constructorContext = constructorContext;
        this.constructorType = constructorType;
    }

    @Override
    public ContextualValue deserialize(Decoder decoder,
                                       Deserializer.DecoderContext context,
                                       Argument<? super ContextualValue> type) throws IOException {
        if (constructorContext != context) {
            throw new IOException("DecoderContext was not passed to the deserializer constructor");
        }
        if (constructorType != type) {
            throw new IOException("Argument was not passed to the deserializer constructor");
        }
        return new ContextualValue(type.getType().getSimpleName() + ":" + decoder.decodeString());
    }
}
""")
            def value = newInstance(context, 'test.ContextualValue', ['encoded'] as Object[])

        expect:
            writeJson(jsonMapper, value) == '"ContextualValue:encoded"'

        when:
            def decoded = jsonMapper.readValue('"decoded"', typeUnderTest)

        then:
            decoded.value() == 'ContextualValue:decoded'

        cleanup:
            context?.close()

        where:
            scopeAnnotation << ['Prototype', 'Singleton']
    }
}
