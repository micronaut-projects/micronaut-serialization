package io.micronaut.serde.xml

import io.micronaut.core.type.Argument
import io.micronaut.serde.LimitingStream
import spock.lang.Specification
import tools.jackson.dataformat.xml.XmlFactory
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.dataformat.xml.ser.ToXmlGenerator

import javax.xml.namespace.QName

class XmlGeneratorEncoderSpec extends Specification {

    def 'currentPath'() {
        given:

        def encoder = new XmlGeneratorEncoder(new XmlMapper().createGenerator(new ByteArrayOutputStream()), LimitingStream.DEFAULT_LIMITS)
        // avoiding No element/attribute name specified exception
        encoder.setNextName(new QName("", "person"));

        when:
        def outer = encoder.encodeObject(Argument.STRING)
        outer.encodeKey('foo')
        outer.encodeString('bar')
        then:
        outer.currentPath() == '->foo'

        when:
        outer.encodeKey('')
        outer.encodeString('bar')
        then:
        outer.currentPath() == '->'

        when:
        outer.encodeKey('baz')
        def array = outer.encodeArray(Argument.VOID)
        array.encodeString('foo')
        then:
        array.currentPath() == '->baz->1'
    }

}
