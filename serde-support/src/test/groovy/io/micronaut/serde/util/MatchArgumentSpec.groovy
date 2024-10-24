package io.micronaut.serde.util

import io.micronaut.context.BeanContext
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.MatchArgumentQualifier
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

@MicronautTest
class MatchArgumentSpec extends Specification {

    @Inject
    BeanContext context

    void "test match serialize specific argument"() {
        given:
            def ge = Argument.of(List, [Argument.STRING] as Argument[])
            def argument = Argument.of(MySerializer, [ge] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.contravariant(MySerializer, ge))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ListStringSerializer
    }

    void "test match serialize object argument"() {
        given:
            def ge = Argument.of(List, [Argument.OBJECT_ARGUMENT] as Argument[])
            def argument = Argument.of(MySerializer, [ge] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.contravariant(MySerializer, ge))

        then:
            beanDefinitions.size() == 2
            beanDefinitions.collect { it.getBeanType() } as Set == [ListGenericSerializer, ListObjectSerializer] as Set
    }

    void "test match serialize non-specific argument"() {
        given:
            def ge = Argument.of(List, [Argument.of(Boolean)] as Argument[])
            def argument = Argument.of(MySerializer, [ge] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.contravariant(MySerializer, ge))

        then:
            beanDefinitions.size() == 2
            beanDefinitions.collect { it.getBeanType() } as Set == [ListGenericSerializer, ListObjectSerializer] as Set
    }

    void "test match serialize Number argument"() {
        given:
            def ge = Argument.of(List, [Argument.of(Number)] as Argument[])
            def argument = Argument.of(MySerializer, [ge] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.contravariant(MySerializer, ge))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ListGenericNumberSerializer
    }

    void "test match serialize Long argument"() {
        given:
            def ge = Argument.of(List, [Argument.of(Long)] as Argument[])
            def argument = Argument.of(MySerializer, [ge] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.contravariant(MySerializer, ge))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ListLongSerializer
    }

    void "test match serialize Double argument"() {
        given:
            def ge = Argument.of(List, [Argument.of(Double)] as Argument[])
            def argument = Argument.of(MySerializer, [ge] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.contravariant(MySerializer, ge))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ListGenericNumberSerializer
    }

    void "test match serialize ArrayList Long argument"() {
        given:
            def ge = Argument.of(ArrayList, [Argument.of(Long)] as Argument[])
            def argument = Argument.of(MySerializer, [ge] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.contravariant(MySerializer, ge))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ListArrayListLongSerializer
    }

    void "test match serialize Collection String argument"() {
        when:
            def beanDefinitions = context.getBeanDefinitions(MySerializer,
                    MatchArgumentQualifier.contravariant(MySerializer, Argument.of(Collection, [Argument.of(String)] as Argument[]))
            )

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == IterableSerializer
    }

    void "test match deserialize List String argument"() {
        def item = Argument.of(Collection, [Argument.of(String)] as Argument[])
        given:
            def argument = Argument.of(MyDeserializer, [item] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.covariant(MyDeserializer, item))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ArrayListStringDeserializer
    }

    void "test match deserialize List Object argument"() {
        def item = Argument.of(Collection, [Argument.of(Object)] as Argument[])
        given:
            def argument = Argument.of(MyDeserializer, [item] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.covariant(MyDeserializer, item))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ArrayListObjectDeserializer
    }

    void "test match deserialize Collection Number argument"() {
        def item = Argument.of(Collection, [Argument.of(Number)] as Argument[])
        given:
            def argument = Argument.of(MyDeserializer, [item] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.covariant(MyDeserializer, item))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ArrayListNumberDeserializer
    }

    void "test match deserialize List Number argument"() {
        def item = Argument.of(List, [Argument.of(Number)] as Argument[])
        given:
            def argument = Argument.of(MyDeserializer, [item] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.covariant(MyDeserializer, item))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ListNumberDeserializer
    }

    void "test match deserialize enum with interface argument"() {
        def item = Argument.of(MyEnum)
        given:
            def argument = Argument.of(MyDeserializer, [item] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.covariant(MyDeserializer, item))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == EnumDeserializer
    }

    interface MySerializer<E> {}

    @Singleton
    static class ListObjectSerializer implements MySerializer<List<Object>> {}

    @Singleton
    static class ListGenericNumberSerializer<T extends Number> implements MySerializer<List<T>> {}

    @Singleton
    static class CollectionGenericNumberSerializer<T extends Number> implements MySerializer<Collection<T>> {}

    @Singleton
    static class ListLongSerializer implements MySerializer<List<Long>> {}

    @Singleton
    static class ListArrayListLongSerializer implements MySerializer<ArrayList<Long>> {}

    @Singleton
    static class ListStringSerializer implements MySerializer<List<String>> {}

    @Singleton
    static class ListCharSequenceSerializer implements MySerializer<List<CharSequence>> {}

    @Singleton
    static class ListGenericSerializer<T> implements MySerializer<List<T>> {}

    @Singleton
    static class IterableSerializer<T> implements MySerializer<Iterable<T>> {}

    interface MyDeserializer<E> {}

    @Singleton
    static class IterableObjectDeserializer<T> implements MyDeserializer<Iterable<T>> {}

    @Singleton
    static class ListObjectDeserializer<T> implements MyDeserializer<List<T>> {}

    @Singleton
    static class ListStringDeserializer implements MyDeserializer<List<String>> {}

    @Singleton
    static class ArrayListObjectDeserializer<T> implements MyDeserializer<ArrayList<T>> {}

    @Singleton
    static class ArrayListStringDeserializer implements MyDeserializer<ArrayList<String>> {}

    @Singleton
    static class ArrayListNumberDeserializer implements MyDeserializer<ArrayList<Number>> {}

    @Singleton
    static class ListNumberDeserializer implements MyDeserializer<List<Number>> {}

    @Singleton
    static class EnumDeserializer<E extends Enum<E>> implements MyDeserializer<E> {}

    @Singleton
    static class MyInterfaceDeserializer implements MyDeserializer<MyInterface> {}

    static interface MyInterface {}

    static enum MyEnum implements MyInterface {}

}


