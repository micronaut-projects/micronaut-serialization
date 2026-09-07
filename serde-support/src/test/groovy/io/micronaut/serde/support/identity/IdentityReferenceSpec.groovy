package io.micronaut.serde.support.identity

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class IdentityReferenceSpec extends Specification {

    @Inject
    ObjectMapper jsonMapper

    void "always as id references are written as identifiers"() {
        given:
        def person = new IdentityPerson(id: 1, name: 'Ada')
        def team = new IdentityTeam(
            person: person,
            manager: person,
            members: [person],
            admins: [person] as IdentityPerson[],
            guests: [person] as Set
        )

        when:
        def json = jsonMapper.writeValueAsString(team)

        then:
        json == '{"person":{"id":1,"name":"Ada"},"manager":1,"members":[1],"admins":[1],"guests":[1]}'

        when:
        def decoded = jsonMapper.readValue(json, Argument.of(IdentityTeam))

        then:
        decoded.manager.is(decoded.person)
        decoded.members.size() == 1
        decoded.members[0].is(decoded.person)
        decoded.admins.length == 1
        decoded.admins[0].is(decoded.person)
        decoded.guests.size() == 1
        decoded.guests.first().is(decoded.person)
    }

    void "null always as id references round trip"() {
        given:
        def person = new IdentityPerson(id: 1, name: 'Ada')
        def team = new IdentityTeam(
            person: person,
            members: [person, null],
            admins: [person] as IdentityPerson[],
            guests: [person] as Set
        )

        when:
        def json = jsonMapper.writeValueAsString(team)

        then:
        json.contains('"members":[1,null]')

        when:
        def decoded = jsonMapper.readValue('{"person":{"id":1,"name":"Ada"},"manager":null,"members":[1,null]}',
            Argument.of(IdentityTeam))

        then:
        decoded.manager == null
        decoded.members.size() == 2
        decoded.members[0].is(decoded.person)
        decoded.members[1] == null
    }

    void "an unresolved always as id reference fails"() {
        when:
        jsonMapper.readValue('{"person":{"id":1,"name":"Ada"},"manager":99}', Argument.of(IdentityTeam))

        then:
        def e = thrown(Exception)
        e.message.contains('99')
    }
}
