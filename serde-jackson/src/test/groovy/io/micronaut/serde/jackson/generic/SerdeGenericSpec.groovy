package io.micronaut.serde.jackson.generic

import io.micronaut.core.type.Argument
import io.micronaut.serde.jackson.JsonCompileSpec

class SerdeGenericSpec extends JsonCompileSpec {

    void "test generic constructor property"() {
        given:
            def context = buildContext('''
package example;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StringBox.class, name = "STRING"),
    @JsonSubTypes.Type(value = IntegerBox.class, name = "INTEGER"),
    @JsonSubTypes.Type(value = UserBox.class, name = "USER"),
    @JsonSubTypes.Type(value = GenericBox.class, name = "GENERIC")
})
@Serdeable
interface Box<T> {

    T value();

}
@Serdeable
record StringBox(String value) implements Box<String> {
}
@Serdeable
record IntegerBox(Integer value) implements Box<Integer> {
}
@Serdeable
record UserBox(User value) implements Box<User> {
}
@Serdeable
record GenericBox<T>(T value) implements Box<T> {
}

@Serdeable
record User(String name, int age) {
}
''')
            def boxClass = context.getClassLoader().loadClass("example.Box")
            def stringBoxClass = context.getClassLoader().loadClass("example.StringBox")
            def integerBoxClass = context.getClassLoader().loadClass("example.IntegerBox")
            def userBoxClass = context.getClassLoader().loadClass("example.UserBox")
            def userClass = context.getClassLoader().loadClass("example.User")
        when:
            def stringBox = jsonMapper.writeValueAsString(stringBoxClass.newInstance("Hello World"))

        then:
            stringBox == """{"type":"STRING","value":"Hello World"}"""

        when:
            def result = jsonMapper.readValue(stringBox, Argument.of(boxClass, String))
        then:
            result.value == "Hello World"

        when:
            def userBox = jsonMapper.writeValueAsString(userBoxClass.newInstance(
                    userClass.newInstance("Josh", 123)
            ))

        then:
            userBox == """{"type":"USER","value":{"name":"Josh","age":123}}"""

        when:
            result = jsonMapper.readValue(userBox, Argument.of(boxClass, userClass))
        then:
            result.value.name == "Josh"
            result.value.age == 123

        when:
            result = jsonMapper.readValue(userBox, Argument.of(boxClass, userBoxClass))
        then:
            userClass.isInstance(result.value)
            result.value.name == "Josh"
            result.value.age == 123

        when:
            result = jsonMapper.readValue("""{"type":"GENERIC","value":"Hello World"}""", Argument.of(boxClass, String))
        then:
            result.value == "Hello World"

        when:
            result = jsonMapper.readValue("""{"type":"GENERIC","value":{"name":"Josh","age":123}}""", Argument.of(boxClass, userClass))
        then:
            userClass.isInstance(result.value)
            result.value.name == "Josh"
            result.value.age == 123

        cleanup:
            context.close()
    }

    void "test generic setter property"() {
        given:
            def context = buildContext('''
package example;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StringBox.class, name = "STRING"),
        @JsonSubTypes.Type(value = IntegerBox.class, name = "INTEGER"),
        @JsonSubTypes.Type(value = UserBox.class, name = "USER"),
        @JsonSubTypes.Type(value = GenericBox.class, name = "GENERIC")
})
@Serdeable
interface Box<T> {

    T getValue();

    void setValue(T value);

}

@Serdeable
class StringBox implements Box<String> {

    String value;

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}

@Serdeable
class IntegerBox implements Box<Integer> {

    private Integer value;

    @Override
    public Integer getValue() {
        return value;
    }

    @Override
    public void setValue(Integer value) {
        this.value = value;
    }
}

@Serdeable
class UserBox implements Box<User> {

    private User value;

    @Override
    public User getValue() {
        return value;
    }

    @Override
    public void setValue(User value) {
        this.value = value;
    }

}

@Serdeable
class GenericBox<T> implements Box<T> {

    private T value;

    @Override
    public T getValue() {
        return value;
    }

    @Override
    public void setValue(T value) {
        this.value = value;
    }

}

@Serdeable
record User(String name, int age) {
}
''')
            def boxClass = context.getClassLoader().loadClass("example.Box")
            def stringBoxClass = context.getClassLoader().loadClass("example.StringBox")
            def integerBoxClass = context.getClassLoader().loadClass("example.IntegerBox")
            def userBoxClass = context.getClassLoader().loadClass("example.UserBox")
            def userClass = context.getClassLoader().loadClass("example.User")
        when:
            def stringBoxInstance = stringBoxClass.newInstance()
            stringBoxInstance.setValue("Hello World")
            def stringBox = jsonMapper.writeValueAsString(stringBoxInstance)

        then:
            stringBox == """{"type":"STRING","value":"Hello World"}"""

        when:
            def result = jsonMapper.readValue(stringBox, Argument.of(boxClass, String))
        then:
            result.value == "Hello World"
        when:
            def userBoxInstance = userBoxClass.newInstance()
            userBoxInstance.setValue(userClass.newInstance("Josh", 123))
            def userBox = jsonMapper.writeValueAsString(userBoxInstance)
        then:
            userBox == """{"type":"USER","value":{"name":"Josh","age":123}}"""

        when:
            result = jsonMapper.readValue(userBox, Argument.of(boxClass, userClass))
        then:
            result.value.name == "Josh"
            result.value.age == 123

        when:
            result = jsonMapper.readValue(userBox, Argument.of(boxClass, userBoxClass))
        then:
            userClass.isInstance(result.value)
            result.value.name == "Josh"
            result.value.age == 123

        when:
            result = jsonMapper.readValue("""{"type":"GENERIC","value":"Hello World"}""", Argument.of(boxClass, String))
        then:
            result.value == "Hello World"

        when:
            result = jsonMapper.readValue("""{"type":"GENERIC","value":{"name":"Josh","age":123}}""", Argument.of(boxClass, userClass))
        then:
            userClass.isInstance(result.value)
            result.value.name == "Josh"
            result.value.age == 123

        cleanup:
            context.close()
    }

}


