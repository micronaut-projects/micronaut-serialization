package io.micronaut.serde.processor.sourcegen

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery

class SerdeSourceGenArgumentTypesSpec extends AbstractTypeElementSpec {

    void 'test property types are normalized to the type their argument describes'() {
        when:
        Map<String, String> normalized = buildClassElement('''package test;

import java.util.*;

class Types<T extends Number, U> {
    boolean booleanValue;
    byte byteValue;
    short shortValue;
    char charValue;
    int intValue;
    long longValue;
    float floatValue;
    double doubleValue;
    int[] intArray;
    String text;
    Iterable<String> iterable;
    Iterable rawIterable;
    List<? extends CharSequence> upperBounded;
    List<? super Integer> lowerBounded;
    List<?> unbounded;
    T boundedVariable;
    U unboundedVariable;
    Map<String, ? extends Map<String, String>> nestedMap;
    List<? extends List<String>> nestedList;

    void run() {
    }
}
''') { ClassElement element ->
            Map<String, String> result = element.getFields().collectEntries { field ->
                ClassElement type = field.type
                if (field.name in ['upperBounded', 'lowerBounded', 'unbounded']) {
                    type = SerdeSourceGenArgumentTypes.typeArguments(type).first()
                }
                [(field.name): describe(SerdeSourceGenArgumentTypes.normalize(type))]
            }
            result.voidReturn = describe(SerdeSourceGenArgumentTypes.normalize(element.getEnclosedElement(
                ElementQuery.ALL_METHODS.named('run')).get().returnType))
            result
        }

        then:
        normalized.booleanValue == 'java.lang.Boolean'
        normalized.byteValue == 'java.lang.Byte'
        normalized.shortValue == 'java.lang.Short'
        normalized.charValue == 'java.lang.Character'
        normalized.intValue == 'java.lang.Integer'
        normalized.longValue == 'java.lang.Long'
        normalized.floatValue == 'java.lang.Float'
        normalized.doubleValue == 'java.lang.Double'
        normalized.intArray == 'int'
        normalized.text == 'java.lang.String'
        normalized.iterable == 'java.util.Collection<java.lang.String>'
        normalized.rawIterable == 'java.util.Collection<java.lang.Object>'
        normalized.upperBounded == 'java.lang.CharSequence'
        normalized.lowerBounded == 'java.lang.Object'
        normalized.unbounded == 'java.lang.Object'
        normalized.boundedVariable == 'java.lang.Number'
        normalized.unboundedVariable == 'java.lang.Object'
        normalized.voidReturn == 'void'

        and: 'a wildcard bound resolved from a type variable keeps its own type arguments'
        normalized.nestedMap == 'java.util.Map<java.lang.String,java.util.Map<java.lang.String,java.lang.String>>'
        normalized.nestedList == 'java.util.List<java.util.List<java.lang.String>>'
    }

    void 'test an iterable without type arguments is normalized to a collection'() {
        expect:
        SerdeSourceGenArgumentTypes.normalize(ClassElement.of(Iterable)).name == 'java.util.Collection'
    }

    private static String describe(ClassElement type) {
        List<ClassElement> typeArguments = SerdeSourceGenArgumentTypes.typeArguments(type)
        if (typeArguments.isEmpty()) {
            return type.name
        }
        return type.name + '<' + typeArguments.collect { describe(SerdeSourceGenArgumentTypes.normalize(it)) }.join(',') + '>'
    }
}
