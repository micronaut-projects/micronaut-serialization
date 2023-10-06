package io.micronaut.serde.support.serdes

import io.micronaut.context.annotation.Property
import io.micronaut.core.util.StringUtils
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "micronaut.serde.inet-address-as-numeric", value = StringUtils.TRUE)
@Property(name = "spec.name", value = "InetAddressSerdeSpec")
@MicronautTest(startApplication = false)
class InetAddressSerdeSpec extends Specification {

    @Inject
    JsonMapper jsonMapper
    void "if you set micronaut.serde.inet-address-as-numeric getHostAddress is used"() {
        given:
        InetAddress input = InetAddress.getByName("google.com");

        when:
        String ip = jsonMapper.writeValueAsString(input)

        then:
        ip =~  (/([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})/)
    }
}
