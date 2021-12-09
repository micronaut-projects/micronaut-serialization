package io.micronaut.json.generator.generator

import io.micronaut.json.generator.symbol.StringSerializerSymbol
import io.micronaut.serde.exceptions.SerdeException

class StringSerializerSymbolSpec extends AbstractSymbolSpec {
    def "string"() {
        given:
        def serializer = buildBasicSerializer(String.class, StringSerializerSymbol.INSTANCE)

        expect:
        deserializeFromString(serializer, '"foo"') == 'foo'
        serializeToString(serializer, 'foo') == '"foo"'
    }

    def "wrong token throws error"() {
        given:
        def serializer = buildBasicSerializer(String.class, StringSerializerSymbol.INSTANCE)
        when:
        deserializeFromString(serializer, '{}')
        then:
        thrown SerdeException
    }

    def "coercion"() {
        given:
        def serializer = buildBasicSerializer(String.class, StringSerializerSymbol.INSTANCE)

        expect:
        deserializeFromString(serializer, '42') == '42'
    }

    def "char sequence"() {
        given:
        def serializer = buildBasicSerializer(CharSequence.class, StringSerializerSymbol.INSTANCE)

        expect:
        serializeToString(serializer, new StringBuilder('foo')) == '"foo"'
    }
}
