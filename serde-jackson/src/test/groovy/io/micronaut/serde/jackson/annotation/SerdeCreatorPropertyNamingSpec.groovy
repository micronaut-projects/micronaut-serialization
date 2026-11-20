package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Issue
import spock.lang.Unroll

@Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/1517")
class SerdeCreatorPropertyNamingSpec extends JsonCompileSpec {

    @Unroll
    void "a #kind creator parameter named apart from its property is read with both names (generated: #generated)"() {
        given:
        def context = buildContext('test.Person', source, true, generated ? [:] : [
            'micronaut.serde.serialization.disable-generated-serializer': true,
            'micronaut.serde.deserialization.disable-generated-deserializer': true
        ])
        def type = argumentOf(context, 'test.Person')

        when: 'the explicit name of the parameter'
        def bean = jsonMapper.readValue('{"legacy":"Jane","last_name":"Smith"}', type)

        then:
        bean.firstName == 'Jane'
        bean.lastName == 'Smith'

        when: 'what it writes'
        def json = jsonMapper.writeValueAsString(bean)

        then:
        json == written
        jsonMapper.readValue(json, type).firstName == 'Jane'

        when: 'an alias of the property'
        def aliased = jsonMapper.readValue('{"nickname":"Jo","last_name":"Smith"}', type)

        then:
        aliased.firstName == 'Jo'

        and: 'the generated serdes are still used when generation is enabled'
        def registry = context.getBean(SerdeRegistry)
        registry.findSerializer(type).createSpecific(registry.newEncoderContext(Object), type).class.name.contains('SerdePerson') == generated
        registry.findDeserializer(type).createSpecific(registry.newDecoderContext(Object), type).class.name.contains('SerdePerson') == generated

        cleanup:
        context.close()

        where:
        kind          | generated | source      | written
        'constructor' | true      | CONSTRUCTOR | '{"first_name":"Jane","last_name":"Smith"}'
        'constructor' | false     | CONSTRUCTOR | '{"first_name":"Jane","last_name":"Smith"}'
        'record'      | true      | RECORD      | '{"legacy":"Jane","last_name":"Smith"}'
        'record'      | false     | RECORD      | '{"legacy":"Jane","last_name":"Smith"}'
    }

    private static final String CONSTRUCTOR = '''
package test;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;

@Serdeable(naming = SnakeCaseStrategy.class)
class Person {
    private final String firstName;
    private final String lastName;

    @JsonCreator
    Person(@JsonProperty("legacy") String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @JsonAlias("nickname")
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
}
'''

    private static final String RECORD = '''
package test;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;

@Serdeable(naming = SnakeCaseStrategy.class)
record Person(@JsonProperty("legacy") @JsonAlias("nickname") String firstName, String lastName) {
}
'''
}
