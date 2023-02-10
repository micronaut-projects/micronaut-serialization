
package io.micronaut.serde


import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.support.IPet
import io.micronaut.serde.support.Owner
import io.micronaut.serde.support.Pet
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class DeserSpec extends Specification {

    @Inject
    ObjectMapper serdeMapper

    void 'test me'() {
        given:
        def owner = new Owner()
        owner.name = "Owner1"
        owner.id = 1
        owner.age = 30
        def pet = new Pet()
        pet.id = 1
        pet.name = "Pet1"
        pet.type = IPet.PetType.CAT
        pet.owner = owner
        def serialized = serdeMapper.writeValueAsString(pet)
        when:
        def obj = serdeMapper.readValue(serialized, Pet)
        then:
        obj.name == "Pet1"
        // This fails because owner field is not deserialized (treated as read only in introspection bean)
        obj.owner
        obj.owner.name == "Owner1"
    }
}
