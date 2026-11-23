package io.micronaut.serde.jackson.annotation

import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Issue
import spock.lang.Specification
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper as DatabindJsonMapper

@Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/1517")
@MicronautTest
class SerdeCreatorPropertyNamingRuntimeSpec extends Specification {

    @Inject
    JsonMapper jsonMapper

    void "a creator parameter named apart is written with its own name when the property has only a runtime strategy"() {
        when:
        def json = jsonMapper.writeValueAsString(new CreatorPropertyNamingTypes.RuntimeNamed('Jane', 'Smith'))

        then: 'the explicit name of the parameter wins over the runtime strategy of the property'
        json.contains('"legacy":"Jane"')
        !json.contains('FIRSTNAME')
        jsonMapper.readValue(json, CreatorPropertyNamingTypes.RuntimeNamed).firstName == 'Jane'

        and: 'a name nothing writes is not read'
        jsonMapper.readValue('{"FIRSTNAME":"Jane"}', CreatorPropertyNamingTypes.RuntimeNamed).firstName == null
    }

    void "a constructor parameter named apart from its strategy-named property differs from Jackson Databind"() {
        given:
        def databind = DatabindJsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()
        def bean = new CreatorPropertyNamingTypes.ConstructorNamed('Jane')
        // Databind links the creator parameter to its property only when the parameter names are compiled in (-parameters)
        boolean parameterNamesCompiled = CreatorPropertyNamingTypes.ConstructorNamed.getConstructor(String).parameters[0].namePresent

        expect: 'Serde always writes the name of the property, whether or not the parameter names are compiled in'
        jsonMapper.writeValueAsString(bean) == '{"first_name":"Jane"}'

        and: 'Databind renames the property after the creator parameter when it can link the two'
        databind.writeValueAsString(bean) == (parameterNamesCompiled ? '{"legacy":"Jane"}' : '{"first_name":"Jane"}')

        and: 'both read the name of the parameter, and Serde also reads the name it writes'
        jsonMapper.readValue('{"legacy":"Jane"}', CreatorPropertyNamingTypes.ConstructorNamed).firstName == 'Jane'
        databind.readValue('{"legacy":"Jane"}', CreatorPropertyNamingTypes.ConstructorNamed).firstName == 'Jane'
        jsonMapper.readValue('{"first_name":"Jane"}', CreatorPropertyNamingTypes.ConstructorNamed).firstName == 'Jane'
    }

    void "a type deserialized through a builder reads the names of the builder, not those of its constructor"() {
        expect:
        jsonMapper.readValue('{"firstName":"Jane"}', CreatorPropertyNamingTypes.BuilderNamed).firstName == 'Jane'
    }
}
