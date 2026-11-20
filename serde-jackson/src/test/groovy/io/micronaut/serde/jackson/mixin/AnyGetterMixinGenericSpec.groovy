package io.micronaut.serde.jackson.mixin

import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Issue
import spock.lang.Unroll

@Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/1506")
class AnyGetterMixinGenericSpec extends JsonCompileSpec {

    @Unroll
    void "any getter/setter mixin on #target"() {
        given:
        def context = buildContext('mixintest.Imp', '''
package mixintest;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.micronaut.serde.annotation.SerdeImport;
import java.util.Map;

@SerdeImport(value = io.micronaut.serde.jackson.mixin.''' + target + '''.class, mixin = BaseMixin.class)
class Imp {}

abstract class BaseMixin {
    @JsonAnyGetter public abstract Map<String, Object> getExtensions();
    @JsonAnySetter public abstract void addExtension(String name, Object value);
}
''')
        def type = context.classLoader.loadClass("io.micronaut.serde.jackson.mixin.${target}")
        def bean = type.newInstance()
        bean.addExtension("x-a", 1)

        expect:
        jsonMapper.writeValueAsString(bean) == '{"x-a":1}'
        jsonMapper.readValue('{"x-b":2}', type).extensions == ['x-b': 2]

        cleanup:
        context.close()

        where:
        target << ['AnyExtPlain', 'AnyExtBase', 'AnyExtSub']
    }

    void "repeated @SerdeImport each with an any getter/setter mixin"() {
        given:
        def context = buildContext('mixintest.Imports', '''
package mixintest;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.micronaut.serde.annotation.SerdeImport;
import java.util.Map;

@SerdeImport(value = io.micronaut.serde.jackson.mixin.AnyExtPlain.class, mixin = Imports.PlainMixin.class)
@SerdeImport(value = io.micronaut.serde.jackson.mixin.AnyExtBase.class, mixin = Imports.BaseMixin.class)
final class Imports {
    abstract static class PlainMixin {
        @JsonAnyGetter public abstract Map<String, Object> getExtensions();
        @JsonAnySetter public abstract void addExtension(String name, Object value);
    }
    abstract static class BaseMixin {
        @JsonAnyGetter public abstract Map<String, Object> getExtensions();
        @JsonAnySetter public abstract void addExtension(String name, Object value);
    }
}
''')
        def plain = context.classLoader.loadClass("io.micronaut.serde.jackson.mixin.AnyExtPlain")
        def generic = context.classLoader.loadClass("io.micronaut.serde.jackson.mixin.AnyExtBase")
        def a = plain.newInstance()
        a.addExtension("x-a", 1)
        def b = generic.newInstance()
        b.addExtension("x-b", 2)

        expect:
        jsonMapper.writeValueAsString(a) == '{"x-a":1}'
        jsonMapper.writeValueAsString(b) == '{"x-b":2}'
        jsonMapper.readValue('{"x-c":3}', plain).extensions == ['x-c': 3]
        jsonMapper.readValue('{"x-d":4}', generic).extensions == ['x-d': 4]

        cleanup:
        context.close()
    }

    void "the importing class does not see the any getter/setter of the last imported type"() {
        given:
        def context = buildContext('mixintest.Host', '''
package mixintest;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.micronaut.serde.annotation.SerdeImport;
import io.micronaut.serde.annotation.Serdeable;
import java.util.LinkedHashMap;
import java.util.Map;

@Serdeable
@SerdeImport(value = io.micronaut.serde.jackson.mixin.AnyExtPlain.class, mixin = Host.PlainMixin.class)
public class Host {
    private final Map<String, Object> other = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getOther() {
        return other;
    }

    @JsonAnySetter
    public void put(String key, Object value) {
        other.put(key, value);
    }

    abstract static class PlainMixin {
        @JsonAnyGetter public abstract Map<String, Object> getExtensions();
        @JsonAnySetter public abstract void addExtension(String name, Object value);
    }
}
''')
        def plain = context.classLoader.loadClass("io.micronaut.serde.jackson.mixin.AnyExtPlain")
        def a = plain.newInstance()
        a.addExtension("x-a", 1)

        expect:
        jsonMapper.writeValueAsString(a) == '{"x-a":1}'
        jsonMapper.readValue('{"x-b":2}', plain).extensions == ['x-b': 2]

        cleanup:
        context.close()
    }

    void "a delegating creator of one imported type does not apply to the next imported type"() {
        given:
        def context = buildContext('mixintest.Imports', '''
package mixintest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(Imports.Wrapper.class)
@SerdeImport(Imports.Animal.class)
public final class Imports {
    public static class Wrapper {
        private final String value;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Wrapper(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(@JsonSubTypes.Type(value = Dog.class, name = "dog"))
    public static class Animal {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Dog extends Animal {
    }
}
''')
        def animal = context.classLoader.loadClass('mixintest.Imports$Animal')
        def dog = context.classLoader.loadClass('mixintest.Imports$Dog').newInstance()
        dog.name = 'Rex'

        expect:
        jsonMapper.writeValueAsString(dog) == '{"type":"dog","name":"Rex"}'
        jsonMapper.readValue('{"type":"dog","name":"Rex"}', animal).name == 'Rex'

        cleanup:
        context.close()
    }
}
