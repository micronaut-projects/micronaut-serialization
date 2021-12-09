package io.micronaut.json.generator.symbol

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement

class ClassElementUtilSpec extends AbstractTypeElementSpec {
    def 'findParameterization'() {
        given:
        def element = buildClassElement("""
package example;

import java.util.*;

class Sub<U> extends Sup<List<U>> {
}
class Sup<U> extends Supsup<List<U>> {
}
class Supsup<T extends Iterable<?>> {
}
""")
        def baseRawClass = element.superType.get().superType.get().rawClassElement

        expect:
        reconstructTypeSignature(ClassElementUtil.findParameterization(element, baseRawClass).get()) == 'Supsup<List<List>>'
        reconstructTypeSignature(ClassElementUtil.findParameterization(element.withBoundGenericTypes(element.declaredGenericPlaceholders), baseRawClass).get()) == 'Supsup<List<List<U>>>'
        reconstructTypeSignature(ClassElementUtil.findParameterization(element.withBoundGenericTypes([ClassElement.of(String)]), baseRawClass).get()) == 'Supsup<List<List<String>>>'
    }

    def 'findParameterization wildcard'() {
        given:
        def element = buildClassElement("""
package example;

import java.util.*;

class Sub<U> extends Sup<List<? super U>> {
}
class Sup<U> extends Supsup<List<? extends U>> {
}
class Supsup<T extends Iterable<?>> {
}
""")
        def baseRawClass = element.superType.get().superType.get().rawClassElement

        expect:
        reconstructTypeSignature(ClassElementUtil.findParameterization(element, baseRawClass).get()) == 'Supsup<List<? extends List>>'
        reconstructTypeSignature(ClassElementUtil.findParameterization(element.withBoundGenericTypes(element.declaredGenericPlaceholders), baseRawClass).get()) == 'Supsup<List<? extends List<? super U>>>'
        reconstructTypeSignature(ClassElementUtil.findParameterization(element.withBoundGenericTypes([ClassElement.of(String)]), baseRawClass).get()) == 'Supsup<List<? extends List<? super String>>>'
    }
}
