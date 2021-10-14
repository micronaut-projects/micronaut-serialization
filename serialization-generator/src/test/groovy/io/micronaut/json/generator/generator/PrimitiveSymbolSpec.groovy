package io.micronaut.json.generator.generator

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.PrimitiveElement
import io.micronaut.json.DeserializationException
import io.micronaut.json.generator.symbol.PrimitiveSerializerSymbol

class PrimitiveSymbolSpec extends AbstractSymbolSpec {
    @SuppressWarnings('GroovyPointlessBoolean')
    def "boolean"() {
        given:
        def serializer = buildBasicSerializer(Boolean.class, PrimitiveSerializerSymbol.INSTANCE, PrimitiveElement.BOOLEAN)

        expect:
        deserializeFromString(serializer, "true") == true
        deserializeFromString(serializer, "false") == false
        deserializeFromString(serializer, '"true"') == true
        deserializeFromString(serializer, '"false"') == false
        deserializeFromString(serializer, '"bla"') == false
        serializeToString(serializer, true) == "true"
        serializeToString(serializer, false) == "false"
    }

    def "byte"() {
        given:
        def serializer = buildBasicSerializer(Byte.class, PrimitiveSerializerSymbol.INSTANCE, PrimitiveElement.BYTE)

        expect:
        deserializeFromString(serializer, "5") == (byte) 5
        deserializeFromString(serializer, "-4") == (byte) -4
        deserializeFromString(serializer, '"-4"') == (byte) -4
        serializeToString(serializer, (byte) 5) == "5"
        serializeToString(serializer, (byte) -4) == "-4"
    }

    def "short"() {
        given:
        def serializer = buildBasicSerializer(Short.class, PrimitiveSerializerSymbol.INSTANCE, PrimitiveElement.SHORT)

        expect:
        deserializeFromString(serializer, "512") == (short) 512
        deserializeFromString(serializer, "-4674") == (short) -4674
        deserializeFromString(serializer, '"-4674"') == (short) -4674
        serializeToString(serializer, (short) 512) == "512"
        serializeToString(serializer, (short) -4674) == "-4674"
    }

    def "char"() {
        given:
        def serializer = buildBasicSerializer(Character.class, PrimitiveSerializerSymbol.INSTANCE, PrimitiveElement.CHAR)

        expect:
        deserializeFromString(serializer, "512") == (char) 512
        serializeToString(serializer, (char) 512) == "512"
    }

    def "int"() {
        given:
        def serializer = buildBasicSerializer(Integer.class, PrimitiveSerializerSymbol.INSTANCE, PrimitiveElement.INT)

        expect:
        deserializeFromString(serializer, "1874651") == 1874651
        deserializeFromString(serializer, "-1874651") == -1874651
        deserializeFromString(serializer, '"-1874651"') == -1874651
        serializeToString(serializer, 1874651) == "1874651"
        serializeToString(serializer, -1874651) == "-1874651"
    }

    def "long"() {
        given:
        def serializer = buildBasicSerializer(Long.class, PrimitiveSerializerSymbol.INSTANCE, PrimitiveElement.LONG)

        expect:
        deserializeFromString(serializer, "187465149261113196") == 187465149261113196L
        deserializeFromString(serializer, "-187465149261113196") == -187465149261113196
        deserializeFromString(serializer, '"-187465149261113196"') == -187465149261113196
        serializeToString(serializer, 187465149261113196) == "187465149261113196"
        serializeToString(serializer, -187465149261113196) == "-187465149261113196"
    }

    def "float"() {
        given:
        def serializer = buildBasicSerializer(Float.class, PrimitiveSerializerSymbol.INSTANCE, PrimitiveElement.FLOAT)

        expect:
        deserializeFromString(serializer, "4.5") == 4.5F
        deserializeFromString(serializer, '"4.5"') == 4.5F
        serializeToString(serializer, 4.5F) == "4.5"
    }

    def "double"() {
        given:
        def serializer = buildBasicSerializer(Double.class, PrimitiveSerializerSymbol.INSTANCE, PrimitiveElement.DOUBLE)

        expect:
        deserializeFromString(serializer, "4.5") == 4.5D
        deserializeFromString(serializer, '"4.5"') == 4.5D
        serializeToString(serializer, 4.5D) == "4.5"
    }

    def "boolean wrong token error"() {
        given:
        def serializer = buildBasicSerializer(Boolean.class, PrimitiveSerializerSymbol.INSTANCE, PrimitiveElement.BOOLEAN)
        when:
        deserializeFromString(serializer, "{}")
        then:
        thrown DeserializationException
    }

    def "number wrong token error"() {
        given:
        def serializer = buildBasicSerializer(Integer.class, PrimitiveSerializerSymbol.INSTANCE, PrimitiveElement.INT)
        when:
        deserializeFromString(serializer, "{}")
        then:
        thrown DeserializationException
    }

    def "big integer"() {
        given:
        def serializer = buildBasicSerializer(BigInteger.class, PrimitiveSerializerSymbol.INSTANCE, ClassElement.of(BigInteger.class))

        expect:
        deserializeFromString(serializer, "4.5") == BigInteger.valueOf(4)
        deserializeFromString(serializer, '"4"') == BigInteger.valueOf(4)
        serializeToString(serializer, BigInteger.valueOf(4)) == "4"
    }

    def "big decimal"() {
        given:
        def serializer = buildBasicSerializer(BigDecimal.class, PrimitiveSerializerSymbol.INSTANCE, ClassElement.of(BigDecimal.class))

        expect:
        deserializeFromString(serializer, '4.5') == BigDecimal.valueOf(4.5)
        deserializeFromString(serializer, '"4.5"') == BigDecimal.valueOf(4.5)
        serializeToString(serializer, BigDecimal.valueOf(4.5)) == "4.5"
    }
}
