package io.micronaut.serde.jackson.annotation

import io.micronaut.core.type.Argument
import io.micronaut.serde.jackson.JsonUnwrappedSpec

class SerdeJsonUnwrappedSpec extends JsonUnwrappedSpec {

    // This cases are not supported by Databind

     void 'unwrapped ignore unknown neither'() {
        given:
        def context = buildContext('example.Outer', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonIgnoreProperties(ignoreUnknown = false)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Outer {
    @JsonUnwrapped B b;
}

@Serdeable
@JsonIgnoreProperties(ignoreUnknown = false)
class B {
}
''')

        when:
        jsonMapper.readValue('{"foo":"bar"}', typeUnderTest)

        then:
        def e = thrown Exception
        expect: 'error should have the name of the outer class'
        e.message.contains("Outer")

        cleanup:
        context.close()
    }

   void 'unwrapped ignore unknown outer'() {
        given:
        def context = buildContext('example.A', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
class A {
    @JsonUnwrapped B b;
}
@Serdeable
@JsonIgnoreProperties(ignoreUnknown = false)
class B {
}
''')

        expect:
        jsonMapper.readValue('{"foo":"bar"}', typeUnderTest) != null

        cleanup:
        context.close()
    }

    void 'unwrapped ignore unknown inner'() {
        given:
        def context = buildContext('example.A', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonIgnoreProperties(ignoreUnknown = false)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class A {
    @JsonUnwrapped B b;
}

@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class B {
}
''')

        when:
        jsonMapper.readValue('{"foo":"bar"}', typeUnderTest) != null

        then:
        def e = thrown Exception
        expect: 'error should have the name of the outer class'
        e.message.contains("A")

        cleanup:
        context.close()
    }

     // TODO: Correct properties order in the Databind output (unwrappeded should keep it's position)
     void "test @JsonUnwrapped - levels 2 - Serde"() {
        given:
        def ctx = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.Serdeable;
import java.sql.Timestamp;
import java.util.Date;
import io.micronaut.core.annotation.NonNull;

@Serdeable
class NestedEntity {

    @JsonUnwrapped(prefix = "hk_")
    private NestedEntityId hashKey;

    private String value;

    @JsonUnwrapped(prefix = "addr_")
    private Address address;

    @JsonUnwrapped
    private Audit audit = new Audit();

    public NestedEntityId getHashKey() {
        return hashKey;
    }

    public void setHashKey(NestedEntityId hashKey) {
        this.hashKey = hashKey;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Audit getAudit() {
        return audit;
    }

    public void setAudit(Audit audit) {
        this.audit = audit;
    }

}

@Serdeable
class NestedEntityId {

    private Integer theInt;

    private String theString;

    public Integer getTheInt() {
        return theInt;
    }

    public void setTheInt(Integer theInt) {
        this.theInt = theInt;
    }

    public String getTheString() {
        return theString;
    }

    public void setTheString(String theString) {
        this.theString = theString;
    }
}

@Serdeable
class Address {

    @JsonUnwrapped(prefix = "cd_")
    private CityData cityData = new CityData();

    private String street;

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public CityData getCityData() {
        return cityData;
    }

    public void setCityData(CityData cityData) {
        this.cityData = cityData;
    }
}

@Serdeable
class Audit {

    static final Timestamp MIN_TIMESTAMP = new Timestamp(new Date(0).getTime());

    private Long version  = 1L;

    // Init manually because cannot be nullable and not getting populated by the event
    private Timestamp dateCreated = MIN_TIMESTAMP;

    private Timestamp dateUpdated = MIN_TIMESTAMP;

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Timestamp getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Timestamp dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Timestamp getDateUpdated() {
        return dateUpdated;
    }

    public void setDateUpdated(Timestamp dateUpdated) {
        this.dateUpdated = dateUpdated;
    }
}

@Serdeable
class CityData {

    @NonNull
    private String zipCode;

    private String city;

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}


""")

        when:
        def nestedJsonStr = '{"hk_theInt":100,"hk_theString":"MyString","value":"test1","addr_street":"Blvd 11","addr_cd_zipCode":"22000","addr_cd_city":"NY","version":1,"dateCreated":"1970-01-01T00:00:00Z","dateUpdated":"1970-01-01T00:00:00Z"}'
        def deserNestedEntity = jsonMapper.readValue(nestedJsonStr, Argument.of(ctx.classLoader.loadClass('unwrapped.NestedEntity')))

        then:
        deserNestedEntity
        deserNestedEntity.hashKey.theInt == 100
        deserNestedEntity.hashKey.theString == "MyString"
        deserNestedEntity.value == "test1"
        deserNestedEntity.address.cityData.zipCode == "22000"
        deserNestedEntity.address.cityData.city == "NY"
        deserNestedEntity.address.street == "Blvd 11"
        deserNestedEntity.audit.version == 1
        deserNestedEntity.audit.dateCreated
        deserNestedEntity.audit.dateUpdated == deserNestedEntity.audit.dateCreated

        when:
        def result = jsonMapper.writeValueAsString(deserNestedEntity)

        then:
        result == nestedJsonStr

        cleanup:
        ctx.close()
    }

    void "test @JsonUnwrapped records"() {
        given:
        def context = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Parent(
  int age,
  @JsonUnwrapped
  Name name) {
}

@Serdeable
record Name(
  String first, String last
) {}
""")

        when:
        def name = newInstance(context, 'unwrapped.Name', "Fred", "Flinstone")
        def parent = newInstance(context, 'unwrapped.Parent', 10, name)

        def result = writeJson(jsonMapper, parent)

        then:
        result == '{"age":10,"first":"Fred","last":"Flinstone"}'

        when:
        def read = jsonMapper.readValue(result, Argument.of(context.classLoader.loadClass('unwrapped.Parent')))

        then:
        read.age == 10
        read.name.first == 'Fred'
        read.name.last == "Flinstone"

        cleanup:
        context.close()
    }


    void "test @JsonUnwrapped - parent constructor args"() {
        given:
        def context = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Parent {
  public final int age;
  @JsonUnwrapped
  public final Name name;

  Parent(int age, @JsonUnwrapped Name name) {
      this.age = age;
      this.name = name;
  }
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Name {
  public final String first, last;
  Name(String first, String last) {
      this.first = first;
      this.last = last;
  }
}
""")

        when:
        def name = newInstance(context, 'unwrapped.Name', "Fred", "Flinstone")
        def parent = newInstance(context, 'unwrapped.Parent', 10, name)

        def result = writeJson(jsonMapper, parent)

        then:
        result == '{"age":10,"first":"Fred","last":"Flinstone"}'

        when:
        def read = jsonMapper.readValue(result, Argument.of(context.classLoader.loadClass('unwrapped.Parent')))

        then:
        read.age == 10
        read.name.first == 'Fred'
        read.name.last == "Flinstone"

        cleanup:
        context.close()
    }


    void 'test wrapped subtype with property info'() {
        given:
            def context = buildContext('test.Base', """
package test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Wrapper {
  public final String foo;
  @JsonUnwrapped
  public final Base base;

  Wrapper(String foo, @JsonUnwrapped Base base) {
      this.base = base;
      this.foo = foo;
  }
}

@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    @JsonSubTypes.Type(value = Sub.class, name = "sub-class")
)
class Base {
    private String string;

    public Base(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }
}

@Serdeable
class Sub extends Base {
    private Integer integer;

    public Sub(String string, Integer integer) {
        super(string);
        this.integer = integer;
    }

    public Integer getInteger() {
        return integer;
    }
}
""")
        when:
            def base = newInstance(context, 'test.Sub', "a", 1)
            def wrapper = newInstance(context, 'test.Wrapper', "bar", base)

            def result = writeJson(jsonMapper, wrapper)

        then:
            result == '{"foo":"bar","type":"sub-class","string":"a","integer":1}'

        when:
            result = jsonMapper.readValue(result, argumentOf(context, "test.Wrapper"))

        then:
            result.foo == 'bar'
            result.base.getClass().name == 'test.Sub'
            result.base.string == 'a'
            result.base.integer == 1

        when:
            result = jsonMapper.readValue('{"string":"a","integer":1,"type":"sub-class","foo":"bar"}', argumentOf(context, "test.Wrapper"))

        then:
            result.foo == 'bar'
            result.base.getClass().name == 'test.Sub'
            result.base.string == 'a'
            result.base.integer == 1

        when:
            result = jsonMapper.readValue('{"foo":"bar", "type":"some-other-type","string":"a","integer":1}', argumentOf(context, "test.Wrapper"))

        then:
            result.getClass().name != 'test.Sub'

        when:
            result = jsonMapper.readValue('{"string":"a","integer":1,"foo":"bar","type":"Sub"}', argumentOf(context, "test.Wrapper"))

        then:
            result.getClass().name != 'test.Sub'
    }

    void 'test wrapped subtype with wrapper info'() {
        given:
            def context = buildContext('test.Base', """
package test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Wrapper {
  public final String foo;
  @JsonUnwrapped
  public final Base base;

  Wrapper(String foo, @JsonUnwrapped Base base) {
      this.base = base;
      this.foo = foo;
  }
}

@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes(
    @JsonSubTypes.Type(value = Sub.class, name = "subClass")
)
class Base {
    private String string;

    public Base(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }
}

@Serdeable
class Sub extends Base {
    private Integer integer;

    public Sub(String string, Integer integer) {
        super(string);
        this.integer = integer;
    }

    public Integer getInteger() {
        return integer;
    }
}
""")
        when:
            def result = jsonMapper.readValue('{"foo":"bar","subClass":{"string":"a","integer":1}}', argumentOf(context, "test.Wrapper"))

        then:
            result.foo == 'bar'
            result.base.getClass().name == 'test.Sub'
            result.base.string == 'a'
            result.base.integer == 1

        when:
            result = jsonMapper.readValue('{"subClass":{"string":"a","integer":1}, "foo":"bar"}', argumentOf(context, "test.Wrapper"))

        then:
            result.foo == 'bar'
            result.base.getClass().name == 'test.Sub'
            result.base.string == 'a'
            result.base.integer == 1

        when:
            def json = writeJson(jsonMapper, result)

        then:
            json == '{"foo":"bar","subClass":{"string":"a","integer":1}}'
    }

}
