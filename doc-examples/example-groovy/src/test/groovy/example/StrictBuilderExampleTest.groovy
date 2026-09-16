package example

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class StrictBuilderExampleTest extends Specification {
    @Inject ObjectMapper objectMapper

    void "test default value is applied for a missing property"() {
        when:
        ReleaseRequest request = objectMapper.readValue(
            '{"service":"checkout"}',
            Argument.of(ReleaseRequest)
        )

        then:
        request.service == "checkout"
        request.owner == "platform"
        request.notes == null
    }

    void "test missing required property is rejected"() {
        when:
        objectMapper.readValue(
            '{"owner":"growth"}',
            Argument.of(ReleaseRequest)
        )

        then:
        SerdeException e = thrown()
        e.message.contains("Required property")
    }
}
