package io.micronaut.serde.xml

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.annotation.Serdeable
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

    def "automatic empty elements can be enabled for Woodstox output"() {
        when:
        def xml = withMapper(['micronaut.serde.xml.automatic-empty-elements': true]) { XmlObjectMapper mapper ->
            mapper.writeValueAsString(new AttributeOnlyBean())
        }

        then:
        xml == '<AttributeOnlyBean other="3"/>'
    }

    def "default output factory configuration repairs namespaces"() {
        when:
        def xml = withMapper([:]) { XmlObjectMapper mapper ->
            mapper.writeValueAsString(new NamespacedChildBean())
        }

        then:
        xml == '<NamespacedChildBean><wstxns1:ChildXML xmlns:wstxns1="uri:child">v</wstxns1:ChildXML></NamespacedChildBean>'
    }

    def "namespace repairing can be disabled"() {
        when:
        withMapper(['micronaut.serde.xml.repairing-namespaces': false]) { XmlObjectMapper mapper ->
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

    @Serdeable
    static class AttributeOnlyBean {
        @JacksonXmlProperty(isAttribute = true, localName = "other")
        String attr = "3"
    }

    @Serdeable
    static class NamespacedChildBean {
        @JacksonXmlProperty(namespace = "uri:child", localName = "ChildXML")
        String child = "v"
    }
}
