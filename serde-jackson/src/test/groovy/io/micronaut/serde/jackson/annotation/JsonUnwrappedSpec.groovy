package io.micronaut.serde.jackson.annotation

import io.micronaut.core.type.Argument
import io.micronaut.serde.jackson.JsonCompileSpec
import io.micronaut.serde.jackson.nested.Address
import io.micronaut.serde.jackson.nested.NestedEntity
import io.micronaut.serde.jackson.nested.NestedEntityId
import spock.lang.Requires

class JsonUnwrappedSpec extends JsonCompileSpec {

    void "test @JsonUnwrapped conflict"() {
        when:
        def context = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Parent {
  public int age;
  public String first;
  @JsonUnwrapped
  public Name name;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Name {
  public String first, last;
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Unwrapped property contains a property [first] that conflicts with an existing property of the outer type: unwrapped.Parent")
    }

    void "test @JsonUnwrapped conflict methods"() {
        when:
        def context = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Parent {
  private String first;
  private Name name;
  public void setFirst(String first) {
    this.first = first;
  }
  public String getFirst() {
    return first;
  }
  @JsonUnwrapped
  public void setName(unwrapped.Name name) {
        this.name = name;
  }
  @JsonUnwrapped
  public unwrapped.Name getName() {
    return name;
  }
}

@Serdeable
class Name {
  private String first;
  public void setFirst(String first) {
    this.first = first;
  }
  public String getFirst() {
    return first;
  }
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Unwrapped property contains a property [first] that conflicts with an existing property of the outer type: unwrapped.Parent")
    }

    @Requires({ jvm.isJava17Compatible() })
    void "test @JsonUnwrapped conflict records"() {
        when:
        def context = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Parent (
  String first,
  @JsonUnwrapped
  Name name
) {}

@Serdeable
record Name (String first) {}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Unwrapped property contains a property [first] that conflicts with an existing property of the outer type: unwrapped.Parent")
    }

    void "test @JsonUnwrapped"() {
        given:
        def context = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Parent {
  public int age;
  @JsonUnwrapped
  public Name name;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Name {
  public String first, last;
}
""")

        when:
        def name = newInstance(context, 'unwrapped.Name', [first:"Fred", last:"Flinstone"])
        def parent = newInstance(context, 'unwrapped.Parent', [age:10, name:name])

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

    void "test @JsonUnwrapped with @JsonIgnoreProperties"() {
        given:
        def context = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Parent {
  public int age;
  @JsonUnwrapped
  @JsonIgnoreProperties("ignored")
  public Name name;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Name {
  public String first, last;
  public String ignored;
}
""")

        when:
        def name = newInstance(context, 'unwrapped.Name', [first:"Fred", last:"Flinstone", ignored:"Ignored"])
        def parent = newInstance(context, 'unwrapped.Parent', [age:10, name:name])

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

    void "test @JsonUnwrapped with @JsonIgnoreProperties and colliding properties"() {
        given:
        def context = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Parent {
  public String ignored;
  @JsonUnwrapped
  @JsonIgnoreProperties("ignored")
  public Name name;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Name {
  public String first, last;
  public String ignored;
}
""")

        when:
        def name = newInstance(context, 'unwrapped.Name', [first:"Fred", last:"Flinstone", ignored:"Ignored"])
        def parent = newInstance(context, 'unwrapped.Parent', [ignored:'foo', name:name])

        def result = writeJson(jsonMapper, parent)

        then:
        result == '{"ignored":"foo","first":"Fred","last":"Flinstone"}'

        when:
        def read = jsonMapper.readValue(result, Argument.of(context.classLoader.loadClass('unwrapped.Parent')))

        then:
        read.ignored == 'foo'
        read.name.first == 'Fred'
        read.name.last == "Flinstone"

        cleanup:
        context.close()
    }

    void "test @JsonUnwrapped with @JsonIgnoreProperties on class"() {
        given:
        def context = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Parent {
  public int age;
  @JsonUnwrapped
  public Name name;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
@JsonIgnoreProperties("ignored")
class Name {
  public String first, last;
  public String ignored;
}
""")

        when:
        def name = newInstance(context, 'unwrapped.Name', [first:"Fred", last:"Flinstone", ignored:"Ignored"])
        def parent = newInstance(context, 'unwrapped.Parent', [age:10, name:name])

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

    void "test @JsonUnwrapped with readOnly field"() {
        given:
        def context = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Parent {
  public int age;
  @JsonUnwrapped
  public Name name;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Name {
  public String first, last;
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public String ssn;
}
""")

        when:
        def name = newInstance(context, 'unwrapped.Name', [first:"Fred", last:"Flinstone", ssn:"abc-123"])
        def parent = newInstance(context, 'unwrapped.Parent', [age:10, name:name])

        def result = writeJson(jsonMapper, parent)

        then:
        result == '{"age":10,"first":"Fred","last":"Flinstone"}'

        when:
        def read = jsonMapper.readValue(result, Argument.of(context.classLoader.loadClass('unwrapped.Parent')))

        then:
        read.age == 10
        read.name.first == 'Fred'
        read.name.last == "Flinstone"
        read.name.ssn == null

        when:
        def jsonStr = '{"age":15,"first":"Barney","last":"Rubble","ssn":"def-789"}'
        read = jsonMapper.readValue(jsonStr, Argument.of(context.classLoader.loadClass('unwrapped.Parent')))

        then:
        read.age == 15
        read.name.first == 'Barney'
        read.name.last == "Rubble"
        read.name.ssn == 'def-789'

        cleanup:
        context.close()
    }

    @Requires({ jvm.isJava17Compatible() })
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

    void "test @JsonUnwrapped - prefix/suffix"() {
        given:
        def context = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Parent {
  public int age;
  @JsonUnwrapped(prefix = "n_", suffix = "_x")
  public Name name;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Name {
  public String first, last;
}
""")

        when:
        def name = newInstance(context, 'unwrapped.Name', [first:"Fred", last:"Flinstone"])
        def parent = newInstance(context, 'unwrapped.Parent', [age:10, name:name])

        def result = writeJson(jsonMapper, parent)

        then:
        result == '{"age":10,"n_first_x":"Fred","n_last_x":"Flinstone"}'

        when:
        def read = jsonMapper.readValue(result, Argument.of(context.classLoader.loadClass('unwrapped.Parent')))

        then:
        read.age == 10
        read.name.first == 'Fred'
        read.name.last == "Flinstone"

        cleanup:
        context.close()
    }

    void "test @JsonUnwrapped - constructor args"() {
        given:
        def context = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Parent {
  public int age;
  @JsonUnwrapped
  public Name name;
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
        def parent = newInstance(context, 'unwrapped.Parent', [age:10, name:name])

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

    void "test @JsonUnwrapped - constructor args - prefix/suffix"() {
        given:
        def context = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Parent {
  public int age;
  @JsonUnwrapped(prefix = "n_", suffix = "_x")
  public Name name;
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
        def parent = newInstance(context, 'unwrapped.Parent', [age:10, name:name])

        def result = writeJson(jsonMapper, parent)

        then:
        result == '{"age":10,"n_first_x":"Fred","n_last_x":"Flinstone"}'

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

    void "test @JsonUnwrapped - levels"() {
        given:
        def context = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Foo {

    @JsonUnwrapped(prefix = "hk_", suffix = "_out")
    private ComplexFooId hashKey;

    private String value;

    public ComplexFooId getHashKey() {
        return hashKey;
    }

    public void setHashKey(ComplexFooId hashKey) {
        this.hashKey = hashKey;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
@Serdeable
class ComplexFooId {

    private Integer theInt;

    @JsonUnwrapped(prefix = "foo_", suffix = "_in")
    private InnerFooId nested;

    public Integer getTheInt() {
        return theInt;
    }

    public void setTheInt(Integer theInt) {
        this.theInt = theInt;
    }

    public InnerFooId getNested() {
        return nested;
    }

    public void setNested(InnerFooId nested) {
        this.nested = nested;
    }
}
@Serdeable
class InnerFooId {

    private Long theLong;

    private String theString;

    public Long getTheLong() {
        return theLong;
    }

    public void setTheLong(Long theLong) {
        this.theLong = theLong;
    }

    public String getTheString() {
        return theString;
    }

    public void setTheString(String theString) {
        this.theString = theString;
    }
}
""")

        when:
        def foo = newInstance(context, 'unwrapped.Foo', [value: "TheValue", hashKey: newInstance(context, 'unwrapped.ComplexFooId', [theInt: 10,
            nested: newInstance(context, 'unwrapped.InnerFooId', [theLong: 200L, theString: 'MyString'])])])

        def result = writeJson(jsonMapper, foo)

        then:
        result == '{"hk_theInt_out":10,"hk_foo_theLong_in_out":200,"hk_foo_theString_in_out":"MyString","value":"TheValue"}'

        when:
        def read = jsonMapper.readValue(result, Argument.of(context.classLoader.loadClass('unwrapped.Foo')))

        then:
        read
        read.value == 'TheValue'
        read.hashKey.theInt == 10
        read.hashKey.nested.theLong == 200
        read.hashKey.nested.theString == 'MyString'

        cleanup:
        context.close()
    }


    void "test @JsonUnwrapped - levels 2"() {
        given:
        def ctx = buildContext("")

        when:
        def nestedEntity = new NestedEntity();
        nestedEntity.setValue("test1");
        NestedEntityId hashKey = new NestedEntityId();
        hashKey.setTheInt(100);
        hashKey.setTheString("MyString");
        nestedEntity.setHashKey(hashKey);
        Address address = new Address();
        address.getCityData().setCity("NY");
        address.getCityData().setZipCode("22000");
        address.setStreet("Blvd 11");
        nestedEntity.setAddress(address);
        def nestedJsonStr = writeJson(jsonMapper, nestedEntity)

        then:
        nestedJsonStr == '{"hk_theInt":100,"hk_theString":"MyString","value":"test1","addr_street":"Blvd 11","addr_cd_zipCode":"22000","addr_cd_city":"NY","version":1,"dateCreated":"1970-01-01T00:00:00Z","dateUpdated":"1970-01-01T00:00:00Z"}'

        when:
        def deserNestedEntity = jsonMapper.readValue(nestedJsonStr, NestedEntity.class)

        then:
        deserNestedEntity
        deserNestedEntity.hashKey.theInt == nestedEntity.hashKey.theInt
        deserNestedEntity.value == nestedEntity.value
        deserNestedEntity.audit.dateCreated == nestedEntity.audit.dateCreated
        deserNestedEntity.address.cityData.zipCode == nestedEntity.address.cityData.zipCode
        deserNestedEntity.address.street == nestedEntity.address.street

        cleanup:
        ctx.close()
    }
}
