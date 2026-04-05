package io.micronaut.serde.processor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.serde.config.annotation.SerdeConfig

class SimpleSerdeShapeAnalyzerSpec extends AbstractTypeElementSpec {

    void 'test jackson dataformat annotation sourcegen'() {
        given:
        def context = buildBeanIntrospection('test.XmlWrapperBean', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import java.util.List;

@Serdeable
class XmlWrapperBean {
    @JacksonXmlElementWrapper(localName = "items")
    private List<String> items;

    public XmlWrapperBean() {}
    public List<String> getItems() { return items; }
    public void setItems(List<String> items) { this.items = items; }

}
""")

        when:

        def metadata = context.annotationMetadata

        then:
        metadata.booleanValue(SerdeConfig.class, SerdeConfig.SOURCEGEN_SERIALIZER_ELIGIBLE).get() == false
        metadata.booleanValue(SerdeConfig.class, SerdeConfig.SOURCEGEN_DESERIALIZER_ELIGIBLE).get() == false
        !metadata.stringValue(SerdeConfig.class, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).present
        !metadata.stringValue(SerdeConfig.class, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).present

    }
}
