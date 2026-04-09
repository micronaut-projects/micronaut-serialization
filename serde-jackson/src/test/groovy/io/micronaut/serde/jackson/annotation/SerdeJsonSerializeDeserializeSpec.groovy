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

    // Test for https://github.com/micronaut-projects/micronaut-serialization/issues/1187
    void 'test json serialize a custom container'() {
        given:
            def context = buildContext('test.SomeModel', """
package test;

import java.io.IOException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

record Something(String s) {}

@Serdeable
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

@Singleton
class ListSomethingSerializer implements Serializer<List<Something>> {
    @Override
    public void serialize(@NonNull Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends List<Something>> type, @NonNull List<Something> value) throws IOException {
        encoder.encodeString(value.stream().map(Something::s).collect(Collectors.joining("|")));
    }
}
""")

        when:
            def json = '{"specificList":"a|b|c","genericList":["a","b","c"]}'
            def SomeModel = context.getClassLoader().loadClass("test.SomeModel")
            def result = jsonMapper.readValue(json, SomeModel)
        then:
            result != null

        when:
            def serialized = writeJson(jsonMapper, result)
        then:
            serialized == json

        when:
            // Serialize a plain List<String> - must NOT use ListSomethingSerializer
            def strings = jsonMapper.writeValueAsString(List.of("a", "b", "c"))
        then:
            strings == '["a","b","c"]'

        cleanup:
            context.close()
    }
}
