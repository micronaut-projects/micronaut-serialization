package io.micronaut.serde.jackson.annotation

class SerdeEmptyStringNullableSerdeSpec extends SerdeJsonEnumSpec {

    void 'test nullable temporal empty string deserializes to null'() {
        given:
            def context = buildContext('''
package test;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.time.LocalDateTime;

@Serdeable
class NullableTemporalHolder {
    @Nullable
    LocalDateTime publishedAt;
}
''')

        when:
            def nullableResult = jsonMapper.readValue('{"publishedAt":""}', argumentOf(context, 'test.NullableTemporalHolder'))

        then:
            nullableResult.publishedAt == null

        cleanup:
            context.close()
    }

    void 'test nullable enum creator empty string deserializes to null'() {
        given:
            def context = buildContext('''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Arrays;
import java.util.Objects;

@Serdeable
class Test {
    @Nullable
    MyEnum value;
}

@Serdeable
enum MyEnum {
    VALUE1("value_1"),
    VALUE2("value_2");

    private final String value;

    MyEnum(String value) {
        this.value = value;
    }

    @JsonCreator
    public static MyEnum create(String value) {
        return Arrays.stream(values())
            .filter(val -> Objects.equals(val.value, value))
            .findFirst()
            .orElse(null);
    }
}
''')

        when:
            def result = jsonMapper.readValue('{"value":""}', argumentOf(context, 'test.Test'))

        then:
            result.value == null

        cleanup:
            context.close()
    }
}
