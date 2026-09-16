package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class BookHttpTest extends Specification {
    @Inject BookClient client

    void "cbor http round trip"() {
        when:
        Book saved = client.save(new Book("The Stand", 50))

        then:
        saved != null
        saved.title == "The Stand"
        saved.quantity == 50
    }
}
