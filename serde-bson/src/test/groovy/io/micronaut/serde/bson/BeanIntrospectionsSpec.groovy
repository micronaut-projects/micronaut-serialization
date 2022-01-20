package io.micronaut.serde.bson

import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

@MicronautTest
class BeanIntrospectionsSpec extends Specification {

    void "test introspections state"() {
        when:
        // test that this doesn't CNFE
        def introspections = BeanIntrospector.SHARED.findIntrospections({ ref -> ref.isPresent()})

        then:
        !introspections.isEmpty()

    }
}
