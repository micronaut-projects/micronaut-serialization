package io.micronaut.json.generator.generator

import io.micronaut.json.generator.symbol.InlineEnumSerializerSymbol
import spock.lang.Ignore

class InlineEnumSerializerSymbolSpec extends AbstractSymbolSpec {
    @Ignore // broken because ClassElement.of doesn't work on enums
    def "simple"() {
        given:
        def serializer = buildBasicSerializer(E.class, InlineEnumSerializerSymbol.INSTANCE)

        expect:
        deserializeFromString(serializer, '"A"') == E.A
        serializeToString(serializer, E.A) == '"A"'
    }

    enum E {
        A, B
    }
}
