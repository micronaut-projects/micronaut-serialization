package io.micronaut.serde.tck.jackson.databind

import io.micronaut.context.ApplicationContext
import io.micronaut.json.JsonMapper
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import tools.jackson.databind.exc.InvalidFormatException

/**
 * The serde TCK's InetAddressTest for Jackson Databind, which since 3.2 deserializes only IP address literals
 * and no longer resolves host names.
 */
class DatabindInetAddressSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    JsonMapper jsonMapper = context.getBean(JsonMapper)

    void "InetAddress is serialized as its host name, or its address without one"() {
        expect:
            jsonMapper.writeValueAsString(InetAddress.getByName("127.0.0.1")) == '"127.0.0.1"'
            jsonMapper.writeValueAsString(InetAddress.getByAddress("example.com", [192, 0, 2, 1] as byte[])) == '"example.com"'
    }

    void "InetAddress is deserialized from an IP address literal"() {
        expect:
            jsonMapper.readValue('"127.0.0.1"', InetAddress).hostAddress == "127.0.0.1"
    }

    void "InetAddress is not deserialized from a host name"() {
        when:
            jsonMapper.readValue('"google.com"', InetAddress)

        then:
            def e = thrown(InvalidFormatException)
            e.message.contains("Not a valid IP address string literal")
    }
}
