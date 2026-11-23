package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Issue
import spock.lang.Unroll
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper as DatabindJsonMapper

@Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/1517")
class SerdeCreatorPropertyNamingSpec extends JsonCompileSpec {

    private static final Map<String, Object> RUNTIME = [
        'micronaut.serde.serialization.disable-generated-serializer'    : true,
        'micronaut.serde.deserialization.disable-generated-deserializer': true
    ]

    private Object read(String source, String json, boolean generated) {
        def context = buildContext('test.Person', source, true, generated ? [:] : RUNTIME)
        try {
            return jsonMapper.readValue(json, argumentOf(context, 'test.Person'))
        } finally {
            context.close()
        }
    }

    private String roundTrip(String source, Closure<Object> create, boolean generated) {
        def context = buildContext('test.Person', source, true, generated ? [:] : RUNTIME)
        try {
            def json = jsonMapper.writeValueAsString(create(context.classLoader.loadClass('test.Person')))
            return json + ' -> ' + jsonMapper.readValue(json, argumentOf(context, 'test.Person')).firstName
        } finally {
            context.close()
        }
    }

    @Unroll
    void "the name of the property does not take the name of another creator parameter (generated: #generated)"() {
        when:
        def bean = read('''
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
    Person(@JsonProperty("first_name") String lastName, @JsonProperty("legacy") String firstName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
}
''', '{"first_name":"Smith","legacy":"Jane"}', generated)

        then:
        bean.lastName == 'Smith'
        bean.firstName == 'Jane'

        where:
        generated << [true, false]
    }

    void "a read-only name of the property is not a name of the creator parameter"() {
        expect:
        read('''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Person {
    private final String firstName;

    @JsonCreator
    Person(@JsonProperty("legacy") String firstName) {
        this.firstName = firstName;
    }

    @JsonProperty(value = "current", access = JsonProperty.Access.READ_ONLY)
    public String getFirstName() { return firstName; }
}
''', '{"current":"Jane"}', false).firstName == null
    }

    @Unroll
    void "a property written with its Java name reads back (generated: #generated)"() {
        expect:
        roundTrip('''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Person {
    private final String firstName;

    @JsonCreator
    Person(@JsonProperty("legacy") String firstName) {
        this.firstName = firstName;
    }

    public String getFirstName() { return firstName; }
}
''', { type -> type.newInstance('Jane') }, generated).endsWith(' -> Jane')

        where:
        generated << [true, false]
    }

    @Unroll
    void "a creator parameter named apart takes its requirement and aliases from itself and the property (generated: #generated)"() {
        given:
        def source = '''
package test;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Person {
    private final String firstName;

    @JsonCreator
    Person(@JsonProperty("legacy") @JsonAlias("oldest") String firstName) {
        this.firstName = firstName;
    }

    @JsonProperty(value = "current", required = true)
    @JsonAlias("nickname")
    public String getFirstName() { return firstName; }
}
'''

        expect:
        read(source, '{}', generated).firstName == null
        read(source, '{"oldest":"A"}', generated).firstName == 'A'
        read(source, '{"nickname":"B"}', generated).firstName == 'B'
        read(source, '{"current":"C"}', generated).firstName == 'C'
        read(source, '{"legacy":"D"}', generated).firstName == 'D'

        where:
        generated << [true, false]
    }

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

    @Unroll
    void "documents read in parallel with either name of a #kind creator parameter (generated: #generated)"() {
        given:
        def context = buildContext('test.Person', source, true, generated ? [:] : [
            'micronaut.serde.serialization.disable-generated-serializer': true,
            'micronaut.serde.deserialization.disable-generated-deserializer': true
        ])
        def type = argumentOf(context, 'test.Person')
        def pool = java.util.concurrent.Executors.newFixedThreadPool(8)
        // a record component is named by its annotation only, so the strategy name is not one of its names
        def keys = kind == 'record' ? ['legacy', 'nickname'] : ['legacy', 'first_name', 'nickname']

        when:
        def results = pool.invokeAll((1..300).collect { i ->
            (java.util.concurrent.Callable<Boolean>) {
                def key = keys[i % keys.size()]
                def bean = jsonMapper.readValue("{\"$key\":\"n$i\",\"last_name\":\"l$i\"}", type)
                bean.firstName == "n$i" && bean.lastName == "l$i"
            }
        }).collect { it.get() }

        then:
        results.every()

        cleanup:
        pool.shutdown()
        context.close()

        where:
        kind          | generated | source
        'constructor' | true      | CONSTRUCTOR
        'constructor' | false     | CONSTRUCTOR
        'record'      | true      | RECORD
        'record'      | false     | RECORD
    }

    @Unroll
    void "a record component named apart from its strategy name matches Jackson Databind (generated: #generated)"() {
        given:
        def context = buildContext('test.Person', RECORD, true, generated ? [:] : RUNTIME)
        def type = argumentOf(context, 'test.Person')
        def databind = DatabindJsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()
        def recordType = context.classLoader.loadClass('test.Person')
        def bean = recordType.newInstance('Jane', 'Smith')

        expect: 'both write the same document'
        jsonMapper.writeValueAsString(bean) == databind.writeValueAsString(bean)

        and: 'both read the same names'
        ['{"legacy":"Jane","last_name":"Smith"}', '{"nickname":"Jane","last_name":"Smith"}'].every { json ->
            def serde = jsonMapper.readValue(json, type)
            def jackson = databind.readValue(json, recordType)
            serde.firstName() == jackson.firstName() && serde.lastName() == jackson.lastName() && serde.firstName() == 'Jane'
        }

        cleanup:
        context.close()

        where:
        generated << [true, false]
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
