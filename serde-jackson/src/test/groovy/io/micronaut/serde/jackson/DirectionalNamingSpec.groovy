package io.micronaut.serde.jackson

import io.micronaut.serde.config.naming.SnakeCaseStrategy
import io.micronaut.serde.config.naming.UpperCamelCaseStrategy
import tools.jackson.databind.PropertyNamingStrategies

class DirectionalNamingSpec extends JsonCompileSpec {

    void "test serializeNaming only - serialized output uses snake_case, deserialization uses identity"() {
        given:
        def context = buildContext('test.UserDto', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;

@Serdeable.Serializable(naming = SnakeCaseStrategy.class)
@Serdeable.Deserializable
class UserDto {
    private String firstName;
    private String lastName;
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
}
''', [firstName: 'John', lastName: 'Doe'])

        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"first_name":"John","last_name":"Doe"}'

        when:
        def bean = jsonMapper.readValue('{"firstName":"Jane","lastName":"Smith"}', typeUnderTest)

        then:
        bean.firstName == 'Jane'
        bean.lastName == 'Smith'

        cleanup:
        context.close()
    }

    void "test deserializeNaming only - deserialization accepts snake_case, serialization uses identity"() {
        given:
        def context = buildContext('test.UserDto', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;

@Serdeable.Serializable
@Serdeable.Deserializable(naming = SnakeCaseStrategy.class)
class UserDto {
    private String firstName;
    private String lastName;
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
}
''', [firstName: 'John', lastName: 'Doe'])

        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"firstName":"John","lastName":"Doe"}'

        when:
        def bean = jsonMapper.readValue('{"first_name":"Jane","last_name":"Smith"}', typeUnderTest)

        then:
        bean.firstName == 'Jane'
        bean.lastName == 'Smith'

        cleanup:
        context.close()
    }

    void "test both serializeNaming and deserializeNaming with different strategies"() {
        given:
        def context = buildContext('test.UserDto', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;
import io.micronaut.serde.config.naming.UpperCamelCaseStrategy;

@Serdeable.Serializable(naming = SnakeCaseStrategy.class)
@Serdeable.Deserializable(naming = UpperCamelCaseStrategy.class)
class UserDto {
    private String firstName;
    private String lastName;
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
}
''', [firstName: 'John', lastName: 'Doe'])

        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"first_name":"John","last_name":"Doe"}'

        when:
        def bean = jsonMapper.readValue('{"FirstName":"Jane","LastName":"Smith"}', typeUnderTest)

        then:
        bean.firstName == 'Jane'
        bean.lastName == 'Smith'

        cleanup:
        context.close()
    }

    void "test directional naming uses runtime serde when source generation is skipped"() {
        given:
        def context = buildContext('test.UserDto', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.annotation.SerdeableGenerated;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;
import io.micronaut.serde.config.naming.UpperCamelCaseStrategy;

@SerdeableGenerated(skip = true)
@Serdeable.Serializable(naming = SnakeCaseStrategy.class)
@Serdeable.Deserializable(naming = UpperCamelCaseStrategy.class)
class UserDto {
    private String firstName;
    private String lastName;
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
}
''', [firstName: 'John', lastName: 'Doe'])

        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"first_name":"John","last_name":"Doe"}'

        when:
        def bean = jsonMapper.readValue('{"FirstName":"Jane","LastName":"Smith"}', typeUnderTest)

        then:
        bean.firstName == 'Jane'
        bean.lastName == 'Smith'

        cleanup:
        context.close()
    }

    void "test unified naming with directional override - directional takes precedence"() {
        given:
        def context = buildContext('test.UserDto', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;
import io.micronaut.serde.config.naming.UpperCamelCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(${PropertyNamingStrategies.SNAKE_CASE.class.canonicalName}.class)
@Serdeable.Serializable(naming = UpperCamelCaseStrategy.class)
@Serdeable.Deserializable
class UserDto {
    private String firstName;
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
}
""", [firstName: 'John'])

        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"FirstName":"John"}'

        when:
        def bean = jsonMapper.readValue('{"first_name":"Jane"}', typeUnderTest)

        then:
        bean.firstName == 'Jane'

        cleanup:
        context.close()
    }

    void "test backward compatibility - class with only unified naming behaves as before"() {
        given:
        def context = buildContext('test.UserDto', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import tools.jackson.databind.annotation.JsonNaming;

@Serdeable
@JsonNaming(${PropertyNamingStrategies.SNAKE_CASE.class.canonicalName}.class)
class UserDto {
    private String firstName;
    private String lastName;
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
}
""", [firstName: 'John', lastName: 'Doe'])

        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"first_name":"John","last_name":"Doe"}'

        when:
        def bean = jsonMapper.readValue('{"first_name":"Jane","last_name":"Smith"}', typeUnderTest)

        then:
        bean.firstName == 'Jane'
        bean.lastName == 'Smith'

        cleanup:
        context.close()
    }

    void "test explicit property annotation overrides directional strategy"() {
        given:
        def context = buildContext('test.UserDto', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;
import com.fasterxml.jackson.annotation.JsonProperty;

@Serdeable.Serializable(naming = SnakeCaseStrategy.class)
@Serdeable.Deserializable(naming = SnakeCaseStrategy.class)
class UserDto {
    @JsonProperty("custom_name")
    private String firstName;
    private String lastName;
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
}
''', [firstName: 'John', lastName: 'Doe'])

        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"custom_name":"John","last_name":"Doe"}'

        when:
        def bean = jsonMapper.readValue('{"custom_name":"Jane","last_name":"Smith"}', typeUnderTest)

        then:
        bean.firstName == 'Jane'
        bean.lastName == 'Smith'

        cleanup:
        context.close()
    }

    void "test runtime naming strategy applied directionally via Serializable annotation"() {
        given:
        def context = buildContext('test.UserDto', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;
import io.micronaut.serde.config.naming.UpperCamelCaseStrategy;

@Serdeable.Serializable(naming = UpperCamelCaseStrategy.class)
@Serdeable.Deserializable(naming = SnakeCaseStrategy.class)
class UserDto {
    private String firstName;
    private String homeTown;
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getHomeTown() { return homeTown; }
    public void setHomeTown(String homeTown) { this.homeTown = homeTown; }
}
''', [firstName: 'John', homeTown: 'Paris'])

        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"FirstName":"John","HomeTown":"Paris"}'

        when:
        def bean = jsonMapper.readValue('{"first_name":"Jane","home_town":"London"}', typeUnderTest)

        then:
        bean.firstName == 'Jane'
        bean.homeTown == 'London'

        cleanup:
        context.close()
    }

    void "test read camelCase write snake_case - real migration use case"() {
        given:
        def context = buildContext('test.UserDto', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.LowerCamelCaseStrategy;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;

@Serdeable.Serializable(naming = SnakeCaseStrategy.class)
@Serdeable.Deserializable(naming = LowerCamelCaseStrategy.class)
class UserDto {
    private String firstName;
    private String lastName;
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
}
''', [firstName: 'Hamza', lastName: 'Mousrij'])

        when: "serialized output uses snake_case"
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"first_name":"Hamza","last_name":"Mousrij"}'

        when: "deserialization accepts camelCase input"
        def bean = jsonMapper.readValue('{"firstName":"Hamza","lastName":"Mousrij"}', typeUnderTest)

        then:
        bean.firstName == 'Hamza'
        bean.lastName == 'Mousrij'

        cleanup:
        context.close()
    }

    void "test read legacy snake_case write new camelCase - reverse migration"() {
        given:
        def context = buildContext('test.UserDto', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.LowerCamelCaseStrategy;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;

@Serdeable.Serializable(naming = LowerCamelCaseStrategy.class)
@Serdeable.Deserializable(naming = SnakeCaseStrategy.class)
class UserDto {
    private String firstName;
    private String lastName;
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
}
''', [firstName: 'Hamza', lastName: 'Mousrij'])

        when: "serialized output uses camelCase"
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"firstName":"Hamza","lastName":"Mousrij"}'

        when: "deserialization accepts legacy snake_case input"
        def bean = jsonMapper.readValue('{"first_name":"Hamza","last_name":"Mousrij"}', typeUnderTest)

        then:
        bean.firstName == 'Hamza'
        bean.lastName == 'Mousrij'

        cleanup:
        context.close()
    }

    void "test serialize naming does not affect deserialization - wrong format rejected"() {
        given:
        def context = buildContext('test.UserDto', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.LowerCamelCaseStrategy;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;

@Serdeable.Serializable(naming = SnakeCaseStrategy.class)
@Serdeable.Deserializable(naming = LowerCamelCaseStrategy.class)
class UserDto {
    private String firstName;
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
}
''', [:])

        when: "snake_case is only for serialization, not deserialization"
        def bean = jsonMapper.readValue('{"first_name":"Hamza"}', typeUnderTest)

        then: "snake_case input is NOT accepted when deser uses camelCase"
        bean.firstName == null

        when: "camelCase input IS accepted"
        def bean2 = jsonMapper.readValue('{"firstName":"Hamza"}', typeUnderTest)

        then:
        bean2.firstName == 'Hamza'

        cleanup:
        context.close()
    }

    void "test JsonGetter and JsonSetter with different property names - directional resolution"() {
        given:
        def context = buildContext('test.UserDto', '''
package test;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class UserDto {
    private String firstName;

    @JsonGetter("first_name_out")
    public String getFirstName() { return firstName; }

    @JsonSetter("firstNameIn")
    public void setFirstName(String firstName) { this.firstName = firstName; }
}
''', [:])

        when: "serialized using @JsonGetter name"
        def bean = newInstance(context, 'test.UserDto', [:])
        bean.firstName = "Hamza"
        def json = writeJson(jsonMapper, bean)

        then:
        json == '{"first_name_out":"Hamza"}'

        when: "deserialized using @JsonSetter name"
        def read = jsonMapper.readValue('{"firstNameIn":"Hamza"}', typeUnderTest)

        then:
        read.firstName == 'Hamza'

        cleanup:
        context.close()
    }

    void "test backward compat - existing JsonProperty still works as unified fallback"() {
        given:
        def context = buildContext('test.UserDto', '''
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class UserDto {
    @JsonProperty("first_name")
    private String firstName;
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
}
''', [firstName: 'Hamza'])

        when: "serialized with JsonProperty name"
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"first_name":"Hamza"}'

        when: "deserialized with same JsonProperty name"
        def bean = jsonMapper.readValue('{"first_name":"Hamza"}', typeUnderTest)

        then:
        bean.firstName == 'Hamza'

        cleanup:
        context.close()
    }

    void "test explicit @JsonProperty wins over class-level @JsonNaming on serialization"() {
        given:
        def context = buildContext('test.Account', """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(${PropertyNamingStrategies.SNAKE_CASE.class.canonicalName}.class)
class Account {
    @JsonProperty("explicit_name")
    private String displayName;
    private String homeTown;
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getHomeTown() { return homeTown; }
    public void setHomeTown(String homeTown) { this.homeTown = homeTown; }
}
""", [displayName: 'x', homeTown: 'y'])

        when: "the explicit @JsonProperty name is kept, the non-annotated one follows the strategy"
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"explicit_name":"x","home_town":"y"}'

        cleanup:
        context.close()
    }

    void "test explicit @JsonProperty name is used for deserialization and the strategy name is rejected"() {
        given:
        def context = buildContext('test.Account', """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(${PropertyNamingStrategies.SNAKE_CASE.class.canonicalName}.class)
class Account {
    @JsonProperty("explicit_name")
    private String displayName;
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
""", [:])

        when: "the explicit name is accepted"
        def bean = jsonMapper.readValue('{"explicit_name":"x"}', typeUnderTest)

        then:
        bean.displayName == "x"

        when: "the strategy-derived name is not accepted"
        def bean2 = jsonMapper.readValue('{"display_name":"y"}', typeUnderTest)

        then:
        bean2.displayName == null

        cleanup:
        context.close()
    }

    void "test directional explicit names: @JsonGetter and @JsonSetter with different names"() {
        given:
        def context = buildContext('test.User', '''
package test;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class User {
    private String name;
    @JsonGetter("name_out")
    public String getName() { return name; }
    @JsonSetter("name_in")
    public void setName(String name) { this.name = name; }
}
''', [:])

        when: "serialized under the getter name"
        def bean = newInstance(context, 'test.User', [:])
        bean.name = "Hamza"
        def json = writeJson(jsonMapper, bean)

        then:
        json == '{"name_out":"Hamza"}'

        when: "deserialized under the setter name"
        def read = jsonMapper.readValue('{"name_in":"Hamza"}', typeUnderTest)

        then:
        read.name == "Hamza"

        when: "the serialize name is also accepted on input (lenient, round-trip-safe)"
        def read2 = jsonMapper.readValue('{"name_out":"Hamza"}', typeUnderTest)

        then: "Micronaut accepts both the @JsonSetter name and the @JsonGetter name on input"
        read2.name == "Hamza"

        cleanup:
        context.close()
    }

    void "Per-property both Serializable+Deserializable with different strategies"() {
        given:
        def context = buildContext('test.UserDto', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;
import io.micronaut.serde.config.naming.UpperCamelCaseStrategy;

@Serdeable
class UserDto {
    @Serdeable.Serializable(naming = SnakeCaseStrategy.class)
    @Serdeable.Deserializable(naming = UpperCamelCaseStrategy.class)
    private String firstName;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
}
''', [firstName: 'John'])

        when: "serialization uses the Serializable strategy (snake_case)"
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"first_name":"John"}'

        when: "deserialization SHOULD use the Deserializable strategy (UpperCamel)"
        def bean = jsonMapper.readValue('{"FirstName":"Jane"}', typeUnderTest)

        then:
        bean.firstName == 'Jane'

        cleanup:
        context.close()
    }

    void "Per-property Deserializable-only naming must NOT affect serialization"() {
        given:
        def context = buildContext('test.UserDto', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.UpperCamelCaseStrategy;

@Serdeable
class UserDto {
    @Serdeable.Deserializable(naming = UpperCamelCaseStrategy.class)
    private String firstName;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
}
''', [firstName: 'John'])

        when: "serialization ignores the Deserializable-only strategy"
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"firstName":"John"}'

        when: "deserialization SHOULD use the Deserializable strategy (UpperCamel)"
        def bean = jsonMapper.readValue('{"FirstName":"Jane"}', typeUnderTest)

        then: "today this fails: deser only listens to 'first_name'"
        bean.firstName == 'Jane'

        cleanup:
        context.close()
    }

    void "Per-property Serializable-only naming must NOT affect deserialization"() {
        given:
        def context = buildContext('test.UserDto', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;

@Serdeable
class UserDto {
    @Serdeable.Serializable(naming = SnakeCaseStrategy.class)
    private String firstName;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
}
''', [firstName: 'John'])

        when: "serialization uses the Serializable strategy"
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"first_name":"John"}'

        when: "deserialization ignores the Serializable-only strategy"
        def bean = jsonMapper.readValue('{"firstName":"Jane"}', typeUnderTest)

        then:
        bean.firstName == 'Jane'

        cleanup:
        context.close()
    }
}
