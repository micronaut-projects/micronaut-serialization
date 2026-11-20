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
@SerdeImport(Imports.Dog.class)
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
        def wrapper = context.classLoader.loadClass('mixintest.Imports$Wrapper')

        when:
        def decoded = jsonMapper.readValue('{"type":"dog","name":"Rex"}', animal)

        then:
        decoded.class.name == 'mixintest.Imports$Dog'
        decoded.name == 'Rex'
        jsonMapper.readValue('"x"', wrapper).value == 'x'

        cleanup:
        context.close()
    }

    void "importing one type twice with mixins that declare two different any setters is rejected"() {
        when:
        buildContext('mixintest.Imports', '''
package mixintest;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.micronaut.serde.annotation.SerdeImport;
import java.util.LinkedHashMap;
import java.util.Map;

@SerdeImport(value = Imports.Target.class, mixin = Imports.FirstMixin.class)
@SerdeImport(value = Imports.Target.class, mixin = Imports.SecondMixin.class)
final class Imports {
    public static class Target {
        public final Map<String, Object> first = new LinkedHashMap<>();
        public final Map<String, Object> second = new LinkedHashMap<>();
        public void putFirst(String name, Object value) { first.put(name, value); }
        public void putSecond(String name, Object value) { second.put(name, value); }
    }
    abstract static class FirstMixin {
        @JsonAnySetter public abstract void putFirst(String name, Object value);
    }
    abstract static class SecondMixin {
        @JsonAnySetter public abstract void putSecond(String name, Object value);
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('JsonAnySetter')
    }

    void "an imported type unwrapping a value with a prefix may share a property name with it"() {
        given:
        def context = buildContext('mixintest.Imports', '''
package mixintest;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(value = Imports.Outer.class, mixin = Imports.OuterMixin.class)
@SerdeImport(Imports.Details.class)
final class Imports {
    public static class Outer {
        private String id;
        private Details details;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public Details getDetails() { return details; }
        public void setDetails(Details details) { this.details = details; }
    }
    public static class Details {
        private String id;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }
    abstract static class OuterMixin {
        @JsonUnwrapped(prefix = "details_") public abstract Details getDetails();
    }
}
''')
        def outerType = context.classLoader.loadClass('mixintest.Imports$Outer')
        def detailsType = context.classLoader.loadClass('mixintest.Imports$Details')
        def details = detailsType.newInstance()
        details.id = 'd'
        def outer = outerType.newInstance()
        outer.id = 'o'
        outer.details = details

        expect:
        jsonMapper.writeValueAsString(outer) == '{"id":"o","details_id":"d"}'
        jsonMapper.readValue('{"id":"o","details_id":"d"}', outerType).details.id == 'd'

        cleanup:
        context.close()
    }

    void "importing one type twice with mixins that mark the same any setter is accepted"() {
        given:
        def context = buildContext('mixintest.Imports', '''
package mixintest;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.micronaut.serde.annotation.SerdeImport;
import java.util.LinkedHashMap;
import java.util.Map;

@SerdeImport(value = Imports.Target.class, mixin = Imports.FirstMixin.class)
@SerdeImport(value = Imports.Target.class, mixin = Imports.SecondMixin.class)
final class Imports {
    public static class Target {
        public final Map<String, Object> other = new LinkedHashMap<>();
        public void put(String name, Object value) { other.put(name, value); }
    }
    abstract static class FirstMixin {
        @JsonAnySetter public abstract void put(String name, Object value);
    }
    abstract static class SecondMixin {
        @JsonAnySetter public abstract void put(String name, Object value);
    }
}
''')
        def target = context.classLoader.loadClass('mixintest.Imports$Target')

        expect:
        jsonMapper.readValue('{"x":1}', target).other == [x: 1]

        cleanup:
        context.close()
    }

    void "a later mixin of a type imported again is still validated"() {
        when:
        buildContext('mixintest.Imports', '''
package mixintest;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(Imports.Target.class)
@SerdeImport(value = Imports.Target.class, mixin = Imports.UnwrapMixin.class)
final class Imports {
    public static class Target {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
    abstract static class UnwrapMixin {
        @JsonUnwrapped public abstract String getName();
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Unwrapped cannot be declared on basic types')
    }

    void "a nested unwrapped property does not collide under its own prefixed name"() {
        given:
        def context = buildContext('mixintest.Outer', '''
package mixintest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Outer {
    private Address address;
    private String note;
    @JsonUnwrapped(prefix = "addr_")
    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }
    @JsonProperty("addr_cityData")
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}

@Serdeable
class Address {
    private CityData cityData;
    @JsonUnwrapped(prefix = "cd_")
    public CityData getCityData() { return cityData; }
    public void setCityData(CityData cityData) { this.cityData = cityData; }
}

@Serdeable
class CityData {
    private String city;
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
}
''')
        def outer = context.classLoader.loadClass('mixintest.Outer').newInstance()
        def address = context.classLoader.loadClass('mixintest.Address').newInstance()
        def cityData = context.classLoader.loadClass('mixintest.CityData').newInstance()
        cityData.city = 'Prague'
        address.cityData = cityData
        outer.address = address
        outer.note = 'n'

        expect: 'it compiles, and the outer property and the nested ones have distinct keys'
        jsonMapper.writeValueAsString(outer) == '{"addr_cd_city":"Prague","addr_cityData":"n"}'

        cleanup:
        context.close()
    }
}
