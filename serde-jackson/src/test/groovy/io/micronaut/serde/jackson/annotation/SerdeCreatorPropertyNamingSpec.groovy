package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Issue
import spock.lang.Unroll

@Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/1517")
class SerdeCreatorPropertyNamingSpec extends JsonCompileSpec {

    @Unroll
    void "an explicit @JsonProperty on a #kind creator parameter wins over the naming strategy (generated: #generated)"() {
        given:
        def context = buildContext('test.Person', source, true, generated ? [:] : [
            'micronaut.serde.serialization.disable-generated-serializer': true,
            'micronaut.serde.deserialization.disable-generated-deserializer': true
        ])
        def type = argumentOf(context, 'test.Person')

        when:
        def bean = jsonMapper.readValue('{"legacy":"Jane","last_name":"Smith"}', type)

        then:
        bean.firstName == 'Jane'
        bean.lastName == 'Smith'

        cleanup:
        context.close()

        where:
        kind          | generated | source
        'constructor' | true      | CONSTRUCTOR
        'constructor' | false     | CONSTRUCTOR
        'record'      | true      | RECORD
        'record'      | false     | RECORD
    }

    private static final String CONSTRUCTOR = '''
package test;

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

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
}
'''

    private static final String RECORD = '''
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;

@Serdeable(naming = SnakeCaseStrategy.class)
record Person(@JsonProperty("legacy") String firstName, String lastName) {
}
'''
}
