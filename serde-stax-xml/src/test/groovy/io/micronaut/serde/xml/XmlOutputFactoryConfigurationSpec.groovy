package io.micronaut.serde.xml

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.serde.annotation.SerdeableGenerated
import spock.lang.Specification
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty

class XmlOutputFactoryConfigurationSpec extends Specification {

    def "default output factory configuration preserves textual XML contracts"() {
        when:
        def xml = withMapper([:]) { XmlObjectMapper mapper ->
            mapper.writeValueAsString(new AttributeOnlyBean())
        }

        then:
        xml == '<AttributeOnlyBean other="3"></AttributeOnlyBean>'
    }

    def "automatic empty elements require a supporting output factory"() {
        when:
        withMapper(['micronaut.serde.format.xml.automatic-empty-elements': true]) { XmlObjectMapper mapper ->
            mapper.writeValueAsString(new AttributeOnlyBean())
        }

        then:
        def e = thrown(BeanInstantiationException)
        e.message.contains("XML output factory does not support automatic empty elements")
    }

    def "default output factory configuration repairs namespaces"() {
        when:
        def xml = withMapper([:]) { XmlObjectMapper mapper ->
            mapper.writeValueAsString(new NamespacedChildBean())
        }

        then:
        xml.startsWith('<NamespacedChildBean><')
        xml.contains(':ChildXML')
        xml.contains('="uri:child"')
        xml.endsWith('</NamespacedChildBean>')
    }

    def "namespace repairing can be disabled"() {
        when:
        withMapper(['micronaut.serde.format.xml.repairing-namespaces': false]) { XmlObjectMapper mapper ->
            mapper.writeValueAsString(new NamespacedChildBean())
        }

        then:
        thrown(IOException)
    }

    private static <T> T withMapper(Map<String, Object> properties, Closure<T> closure) {
        ApplicationContext context = ApplicationContext.run(properties)
        try {
            return closure.call(context.getBean(XmlObjectMapper))
        } finally {
            context.close()
        }
    }

    @SerdeableGenerated(skip = true)
    static class AttributeOnlyBean {
        @JacksonXmlProperty(isAttribute = true, localName = "other")
        String attr = "3"
    }

    @SerdeableGenerated(skip = true)
    static class NamespacedChildBean {
        @JacksonXmlProperty(namespace = "uri:child", localName = "ChildXML")
        String child = "v"
    }
}
