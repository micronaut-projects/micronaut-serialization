package io.micronaut.serde.xml

import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.Encoder
import io.micronaut.serde.Serializer
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.xml.bean.XmlKeysBean
import io.micronaut.serde.xml.bean.XmlKeysRecord
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Specification

@MicronautTest
class XmlObjectMapperRegressionSpec extends Specification {

    @Inject
    @Named(XmlObjectMapper.XML_MAPPER_NAME)
    XmlObjectMapper xmlMapper

    def "root scalar values have a valid document element"() {
        when:
        def xml = xmlMapper.writeValueAsString("hello")

        then:
        xml == "<String>hello</String>"
        xmlMapper.readValue(xml, String) == "hello"
    }

    def "typed null values do not reach the selected serializer"() {
        expect:
        new String(xmlMapper.writeValueAsBytes(Argument.of(String), null)).endsWith("<null/>")
        xmlMapper.writeValueToTree(Argument.of(String), null) == JsonNode.nullNode()
    }

    def "null collection entries do not close the collection wrapper"() {
        expect:
        xmlMapper.writeValueAsString(["a", null, "b"]) ==
            "<ArrayList><item>a</item><item/><item>b</item></ArrayList>"
    }

    def "external XML entities are rejected"() {
        given:
        def xml = '''<!DOCTYPE ExternalEntityBean [
            <!ENTITY external SYSTEM "file:///etc/passwd">
        ]>
        <ExternalEntityBean><value>&external;</value></ExternalEntityBean>'''

        when:
        xmlMapper.readValue(xml, ExternalEntityBean)

        then:
        thrown(SerdeException)
    }

    def "custom root serializers can write scalar XML values"() {
        expect:
        xmlMapper.writeValueAsString(new CustomValue(value: "foo")) ==
            "<CustomValue>custom:foo</CustomValue>"
    }

    def "source-generated bean uses XML key metadata"() {
        given:
        def value = new XmlKeysBean('Bob', 7, ['a', 'b'])

        when:
        def xml = xmlMapper.writeValueAsString(value)
        def decoded = xmlMapper.readValue(xml, XmlKeysBean)

        then:
        xml == '<XmlKeysBean id="7"><name>Bob</name><item>a</item><item>b</item></XmlKeysBean>'
        decoded.id == 7
        decoded.name == 'Bob'
        decoded.items == ['a', 'b']
    }

    def "source-generated record uses XML key metadata without changing constructor order"() {
        given:
        def value = new XmlKeysRecord('Bob', 7, ['a', 'b'])

        when:
        def xml = xmlMapper.writeValueAsString(value)
        def decoded = xmlMapper.readValue(xml, XmlKeysRecord)

        then:
        xml == '<XmlKeysRecord id="7"><name>Bob</name><item>a</item><item>b</item></XmlKeysRecord>'
        decoded == value
    }

    def "source-generated XML key metadata references SerdeConfig constants"() {
        expect:
        generatedXmlSerdeSources().each { source ->
            assert source.contains('SerdeConfig.XML_ATTRIBUTE_PROPERTY')
            assert source.contains('SerdeConfig.META_ANNOTATION_PROPERTY')
            assert !source.contains('"xmlAttributeProperty"')
            assert !source.contains('"Property"')
        }
    }

    private static List<String> generatedXmlSerdeSources() {
        [
            'SerdeXmlKeysBeanSerializer',
            'SerdeXmlKeysBeanDeserializer',
            'SerdeXmlKeysRecordSerializer',
            'SerdeXmlKeysRecordDeserializer'
        ].collect { generatedXmlSerdeSource(it) }
    }

    private static String generatedXmlSerdeSource(String simpleName) {
        String sourcePath = "io/micronaut/serde/xml/bean/${simpleName}.java"
        def sourceFile = new File("build/generated/sources/annotationProcessor/java/test/${sourcePath}")
        if (!sourceFile.exists()) {
            sourceFile = new File("serde-stax-xml/build/generated/sources/annotationProcessor/java/test/${sourcePath}")
        }
        assert sourceFile.exists() : "Generated test source not found: ${sourcePath}"
        sourceFile.text
    }

    @Serdeable
    static class ExternalEntityBean {
        String value
    }

    @Serdeable.Serializable(using = PrefixSerializer)
    static class CustomValue {
        String value
    }

    @Singleton
    static class PrefixSerializer implements Serializer<CustomValue> {
        @Override
        void serialize(Encoder encoder,
                       Serializer.EncoderContext context,
                       Argument<? extends CustomValue> type,
                       CustomValue value) throws IOException {
            encoder.encodeString("custom:" + value.value)
        }
    }
}
