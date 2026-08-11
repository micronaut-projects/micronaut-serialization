package io.micronaut.serde.xml

import io.micronaut.core.type.Argument
import io.micronaut.serde.Decoder
import io.micronaut.serde.KeyDescriptor
import io.micronaut.serde.Keys
import io.micronaut.serde.KeysAwareDecoder
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.serde.exceptions.SerdeException
import org.intellij.lang.annotations.Language
import spock.lang.Specification

import javax.xml.stream.XMLInputFactory

class XmlReaderDecoderSpec extends Specification {

    private static Decoder createDecoder(@Language('xml') String xml, boolean emptyElementAsNull = false) {
        def reader = XMLInputFactory.newFactory()
            .createXMLStreamReader(new StringReader(xml))
        new XmlReaderDecoder.DocumentDecoder(LimitingStream.DEFAULT_LIMITS, reader, emptyElementAsNull)
    }

    def "decode root scalar values"() {
        expect:
        createDecoder('<value>a</value>').decodeString() == 'a'
        createDecoder('<value>42</value>').decodeInt() == 42
        createDecoder('<value>42</value>').decodeDouble() == 42
        createDecoder('<value>42</value>').decodeBigInteger() == BigInteger.valueOf(42)
        createDecoder('<value>42.5</value>').decodeBigDecimal() == BigDecimal.valueOf(42.5)
        createDecoder('<value>true</value>').decodeBoolean()
    }

    def "decode object keys attributes and scalars"() {
        given:
        def object = createDecoder('<user id="7"><name>Foo</name><age>42</age></user>')
            .decodeObject(Argument.of(Map))

        expect:
        object.decodeKey() == 'id'
        object.decodeString() == '7'

        object.decodeKey() == 'name'
        object.decodeString() == 'Foo'

        object.decodeKey() == 'age'
        object.decodeInt() == 42

        object.decodeKey() == null
        object.finishStructure()
    }

    def "decode wrapped array values"() {
        given:
        def array = createDecoder('<items><item>a</item><item>42</item></items>')
            .decodeArray()

        expect:
        array.hasNextArrayValue()
        array.decodeString() == 'a'

        array.hasNextArrayValue()
        array.decodeInt() == 42

        !array.hasNextArrayValue()
        array.finishStructure()
    }

    def "decode XML property layout from reusable keys"() {
        given:
        def keys = Keys.createWithMetadata(
            KeyDescriptor.create('id', SerdeConfig.XML_ATTRIBUTE_PROPERTY, 'true'),
            KeyDescriptor.create('item', SerdeConfig.META_ANNOTATION_PROPERTY, 'false'),
            KeyDescriptor.create(
                'values',
                SerdeConfig.META_ANNOTATION_PROPERTY, 'true',
                SerdeConfig.WRAPPER_PROPERTY, 'entries'
            )
        )
        def object = (KeysAwareDecoder) createDecoder(
            '<root id="7"><item>a</item><item>b</item><entries><values>x</values></entries></root>'
        ).decodeObject(Argument.of(Map))

        expect:
        object.decodeKey(keys) == 0
        object.decodeInt() == 7

        object.decodeKey(keys) == 1
        def inline = object.decodeArray(Argument.of(List))
        inline.hasNextArrayValue()
        inline.decodeString() == 'a'
        inline.hasNextArrayValue()
        inline.decodeString() == 'b'
        !inline.hasNextArrayValue()
        inline.finishStructure()

        object.decodeKey(keys) == 2
        def wrapped = object.decodeArray(Argument.of(List))
        wrapped.hasNextArrayValue()
        wrapped.decodeString() == 'x'
        !wrapped.hasNextArrayValue()
        wrapped.finishStructure()

        object.decodeKey(keys) == KeysAwareDecoder.MATCH_END_OBJECT
        object.finishStructure()
    }

    def "skip nested xml value keeps cursor aligned"() {
        given:
        def object = createDecoder('<root><first>a</first><skip><nested>x</nested></skip><last>b</last></root>')
            .decodeObject(Argument.of(Map))

        expect:
        object.decodeKey() == 'first'
        object.decodeString() == 'a'

        object.decodeKey() == 'skip'
        object.skipValue()

        object.decodeKey() == 'last'
        object.decodeString() == 'b'

        object.decodeKey() == null
        object.finishStructure()
    }

    def "decode arbitrary nested element as map"() {
        given:
        def object = createDecoder('<root><nested><key>a</key><key>b</key></nested></root>')
            .decodeObject(Argument.of(Map))

        expect:
        object.decodeKey() == 'nested'
        object.decodeArbitrary() == [key: ['a', 'b']]
    }

    def "nested element cannot be decoded as scalar array item"() {
        given:
        def array = createDecoder('<items><item><nested>a</nested></item></items>')
            .decodeArray()

        when:
        array.hasNextArrayValue()
        array.decodeString()

        then:
        thrown(SerdeException)
    }

    def "empty element can decode as null when feature is enabled"() {
        given:
        def object = createDecoder('<root><empty></empty></root>', true)
            .decodeObject(Argument.of(Map))

        expect:
        object.decodeKey() == 'empty'
        object.decodeNull()
    }
}
