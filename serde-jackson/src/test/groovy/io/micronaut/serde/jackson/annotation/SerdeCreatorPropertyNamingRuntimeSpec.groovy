package io.micronaut.serde.jackson.annotation

import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/1517")
@MicronautTest
class SerdeCreatorPropertyNamingRuntimeSpec extends Specification {

    @Inject
    JsonMapper jsonMapper

    void "a runtime naming strategy names the property a creator parameter named apart is also read with"() {
        expect: 'the name the strategy derives at runtime'
        jsonMapper.readValue('{"FIRSTNAME":"Jane","lastName":"Smith"}', CreatorPropertyNamingTypes.RuntimeNamed).firstName == 'Jane'
        jsonMapper.readValue('{"legacy":"Jane"}', CreatorPropertyNamingTypes.RuntimeNamed).firstName == 'Jane'

        when:
        def json = jsonMapper.writeValueAsString(new CreatorPropertyNamingTypes.RuntimeNamed('Jane', 'Smith'))

        then:
        json.contains('"FIRSTNAME":"Jane"')
        jsonMapper.readValue(json, CreatorPropertyNamingTypes.RuntimeNamed).firstName == 'Jane'
    }

    void "a builder argument named apart from its property is not read with the name of the property"() {
        expect:
        jsonMapper.readValue('{"legacy":"Jane"}', CreatorPropertyNamingTypes.BuilderNamed).firstName == 'Jane'
        jsonMapper.readValue('{"current":"Jane"}', CreatorPropertyNamingTypes.BuilderNamed).firstName == null
    }
}
