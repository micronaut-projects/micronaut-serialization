package io.micronaut.serde.jackson.annotation

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.serde.jackson.JsonEnumSpec

class SerdeJsonEnumSpec extends JsonEnumSpec {

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of(
                        "micronaut.serde.deserialization.accept-case-insensitive-enums", "true"
                )
        ))
    }

    def 'test deserialize EnumSet for Enum with @JsonCreator'() {
        given:
            def context = buildContext('''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Objects;
import java.util.Arrays;
import java.util.Set;

@Serdeable
class Test {
    private Set<MyEnum> enumSet;

    public Set<MyEnum> getEnumSet() {
        return enumSet;
    }

    public void setEnumSet(Set<MyEnum> enumSet) {
        this.enumSet = enumSet;
    }
}

@Serdeable
enum MyEnum {
    VALUE1("value_1"),
    VALUE2("value_2"),
    VALUE3("value_3");

    private final String value;

    MyEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
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
            def json = '{"enumSet":["value_1","value_3"]}'
            def result = jsonMapper.readValue(json, argumentOf(context, 'test.Test'))

        then:
            result.enumSet instanceof EnumSet
            result.enumSet == EnumSet.of(getEnum(context, 'test.MyEnum.VALUE1'), getEnum(context, 'test.MyEnum.VALUE3'))

        cleanup:
            context.close()
    }

    def 'test deserialize EnumSet for Enum with @JsonValue on property'() {
        given:
            def context = buildContext('''
package test;

import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Set;

@Serdeable
class Test {
    private Set<MyEnum> enumSet;

    public Set<MyEnum> getEnumSet() {
        return enumSet;
    }

    public void setEnumSet(Set<MyEnum> enumSet) {
        this.enumSet = enumSet;
    }
}

@Serdeable
enum MyEnum {
    VALUE1("value_1"),
    VALUE2("value_2"),
    VALUE3("value_3");

    @JsonValue
    private final String value;

    MyEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
''')
        when:
            def json = '{"enumSet":["value_1","value_3"]}'
            def result = jsonMapper.readValue(json, argumentOf(context, 'test.Test'))

        then:
            result.enumSet instanceof EnumSet
            result.enumSet == EnumSet.of(getEnum(context, 'test.MyEnum.VALUE1'), getEnum(context, 'test.MyEnum.VALUE3'))

        cleanup:
            context.close()
    }

    def 'test deserialize EnumSet for Enum with @JsonValue on getter'() {
        given:
            def context = buildContext('''
package test;

import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Set;

@Serdeable
class Test {
    private Set<MyEnum> enumSet;

    public Set<MyEnum> getEnumSet() {
        return enumSet;
    }

    public void setEnumSet(Set<MyEnum> enumSet) {
        this.enumSet = enumSet;
    }
}

@Serdeable
enum MyEnum {
    VALUE1("value_1"),
    VALUE2("value_2"),
    VALUE3("value_3");

    private final String value;

    MyEnum(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
''')
        when:
            def json = '{"enumSet":["value_1","value_3"]}'
            def result = jsonMapper.readValue(json, argumentOf(context, 'test.Test'))

        then:
            result.enumSet instanceof EnumSet
            result.enumSet == EnumSet.of(getEnum(context, 'test.MyEnum.VALUE1'), getEnum(context, 'test.MyEnum.VALUE3'))

        cleanup:
            context.close()
    }


    def 'test deserialize EnumSet for Enum with @JsonProperty'() {
        given:
            def context = buildContext('''
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Set;

@Serdeable
class Test {
    private Set<MyEnum> enumSet;

    public Set<MyEnum> getEnumSet() {
        return enumSet;
    }

    public void setEnumSet(Set<MyEnum> enumSet) {
        this.enumSet = enumSet;
    }
}

@Serdeable
enum MyEnum {
    @JsonProperty("value_1") VALUE1,
    @JsonProperty("value_2") VALUE2,
    @JsonProperty("value_3") VALUE3
}
''')
        when:
            def json = '{"enumSet":["value_1","value_3"]}'
            def result = jsonMapper.readValue(json, argumentOf(context, 'test.Test'))

        then:
            result.enumSet instanceof EnumSet
            result.enumSet == EnumSet.of(getEnum(context, 'test.MyEnum.VALUE1'), getEnum(context, 'test.MyEnum.VALUE3'))

        cleanup:
            context.close()
    }


    def 'test deserialize EnumMap for Enum with @JsonValue on property'() {
        given:
            def context = buildContext('''
package test;

import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Objects;
import java.util.Arrays;
import java.util.Map;

@Serdeable
class Test {
    private Map<MyEnum, Object> enumMap;

    public Map<MyEnum, Object> getEnumMap() {
        return enumMap;
    }

    public void setEnumMap(Map<MyEnum, Object> enumMap) {
        this.enumMap = enumMap;
    }
}

@Serdeable
enum MyEnum {
    VALUE1("value_1"),
    VALUE2("value_2"),
    VALUE3("value_3");

    @JsonValue
    private final String value;

    MyEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
''')
        when:
            def json = '{"enumMap":{"value_1":"abc","value_3":123}}'
            def result = jsonMapper.readValue(json, argumentOf(context, 'test.Test'))

        then:
            result.enumMap instanceof EnumMap
            result.enumMap == new EnumMap([(getEnum(context, 'test.MyEnum.VALUE1')): "abc", (getEnum(context, 'test.MyEnum.VALUE3')): 123])

        cleanup:
            context.close()
    }

    def 'test deserialize EnumMap for Enum with @JsonValue on getter'() {
        given:
            def context = buildContext('''
package test;

import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Objects;
import java.util.Arrays;
import java.util.Map;

@Serdeable
class Test {
    private Map<MyEnum, Object> enumMap;

    public Map<MyEnum, Object> getEnumMap() {
        return enumMap;
    }

    public void setEnumMap(Map<MyEnum, Object> enumMap) {
        this.enumMap = enumMap;
    }
}

@Serdeable
enum MyEnum {
    VALUE1("value_1"),
    VALUE2("value_2"),
    VALUE3("value_3");

    private final String value;

    MyEnum(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
''')
        when:
            def json = '{"enumMap":{"value_1":"abc","value_3":123}}'
            def result = jsonMapper.readValue(json, argumentOf(context, 'test.Test'))

        then:
            result.enumMap instanceof EnumMap
            result.enumMap == new EnumMap([(getEnum(context, 'test.MyEnum.VALUE1')): "abc", (getEnum(context, 'test.MyEnum.VALUE3')): 123])

        cleanup:
            context.close()
    }

    def 'test deserialize EnumMap for Enum with @JsonProperty'() {
        given:
            def context = buildContext('''
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Objects;
import java.util.Arrays;
import java.util.Map;

@Serdeable
class Test {
    private Map<MyEnum, Object> enumMap;

    public Map<MyEnum, Object> getEnumMap() {
        return enumMap;
    }

    public void setEnumMap(Map<MyEnum, Object> enumMap) {
        this.enumMap = enumMap;
    }
}

@Serdeable
enum MyEnum {
    @JsonProperty("value_1") VALUE1,
    @JsonProperty("value_2") VALUE2,
    @JsonProperty("value_3") VALUE3
}
''')
        when:
            def json = '{"enumMap":{"value_1":"abc","value_3":123}}'
            def result = jsonMapper.readValue(json, argumentOf(context, 'test.Test'))

        then:
            result.enumMap instanceof EnumMap
            result.enumMap == new EnumMap([(getEnum(context, 'test.MyEnum.VALUE1')): "abc", (getEnum(context, 'test.MyEnum.VALUE3')): 123])

        cleanup:
            context.close()
    }

    def 'test deserialize EnumMap for Enum with @JsonCreator'() {
        given:
            def context = buildContext('''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Objects;
import java.util.Arrays;
import java.util.Map;

@Serdeable
class Test {
    private Map<MyEnum, Object> enumMap;

    public Map<MyEnum, Object> getEnumMap() {
        return enumMap;
    }

    public void setEnumMap(Map<MyEnum, Object> enumMap) {
        this.enumMap = enumMap;
    }
}

@Serdeable
enum MyEnum {
    VALUE1("value_1"),
    VALUE2("value_2"),
    VALUE3("value_3");

    private final String value;

    MyEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
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
            def json = '{"enumMap":{"value_1":"abc","value_3":123}}'
            def result = jsonMapper.readValue(json, argumentOf(context, 'test.Test'))

        then:
            result.enumMap instanceof EnumMap
            result.enumMap == new EnumMap([(getEnum(context, 'test.MyEnum.VALUE1')): "abc", (getEnum(context, 'test.MyEnum.VALUE3')): 123])

        cleanup:
            context.close()
    }

}
