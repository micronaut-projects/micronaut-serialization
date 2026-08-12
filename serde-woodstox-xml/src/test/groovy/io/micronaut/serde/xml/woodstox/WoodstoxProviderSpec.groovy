package io.micronaut.serde.xml.woodstox

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.annotation.SerdeableGenerated
import io.micronaut.serde.xml.XmlObjectMapper
import spock.lang.Specification

import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLOutputFactory

class WoodstoxProviderSpec extends Specification {

    def "Woodstox supplies the XML factories"() {
        expect:
        XMLInputFactory.newFactory().class.name == 'com.ctc.wstx.stax.WstxInputFactory'
        XMLOutputFactory.newFactory().class.name == 'com.ctc.wstx.stax.WstxOutputFactory'
    }

    def "Woodstox automatic empty elements can be enabled"() {
        given:
        def context = ApplicationContext.run([
            'micronaut.serde.format.xml.automatic-empty-elements': true
        ])

        when:
        def xml = context.getBean(XmlObjectMapper).writeValueAsString(new EmptyBean())

        then:
        xml == '<EmptyBean/>'

        cleanup:
        context.close()
    }

    @SerdeableGenerated(skip = true)
    static class EmptyBean {
    }
}
