package io.micronaut.serde.jackson.binding

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.context.ApplicationContext
import io.micronaut.core.bind.BeanPropertyBinder
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.serde.annotation.Serdeable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class JsonBeanPropertyBinderSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    @Unroll
    void "test bind map properties to object"() {
        given:
        BeanPropertyBinder binder = context.getBean(BeanPropertyBinder)
        def result = binder.bind(type.newInstance(), map)

        expect:
        result == expected

        where:
        type   | map                                                                                            | expected
        Author | ['name': 'Stephen King', 'publisher.name': 'Blah']                                             | new Author(name: "Stephen King", publisher: new Publisher(name: "Blah"))
        Book   | ['authors[0].name': 'Stephen King', 'authors[0].publisher.name': 'Blah']                       | new Book(authors: [new Author(name: "Stephen King", publisher: new Publisher(name: 'Blah'))])
        Book   | ['authorsByInitials[SK].name': 'Stephen King', 'authorsByInitials[SK].publisher.name': 'Blah'] | new Book(authorsByInitials: [SK: new Author(name: "Stephen King", publisher: new Publisher(name: 'Blah'))])
        Book   | ['title': 'The Stand', url: 'http://foo.com']                                                  | new Book(title: "The Stand", url: new URL("http://foo.com"))
        Book   | ['authors[0].name': 'Stephen King']                                                            | new Book(authors: [new Author(name: "Stephen King")])
        Book   | ['authors[0].name': 'Stephen King', 'authors[0].age': 60]                                      | new Book(authors: [new Author(name: "Stephen King", age: 60)])
        Book   | ['authors[0].name': 'Stephen King', 'authors[0].age': 60,
                  'authors[1].name': 'JRR Tolkien', 'authors[1].age': 110]                                      | new Book(authors: [new Author(name: "Stephen King", age: 60), new Author(name: "JRR Tolkien", age: 110)])
        Book   | ['authorsByInitials[SK].name' : 'Stephen King', 'authorsByInitials[SK].age': 60,
                  'authorsByInitials[JRR].name': 'JRR Tolkien', 'authorsByInitials[JRR].age': 110]              | new Book(authorsByInitials: [SK: new Author(name: "Stephen King", age: 60), JRR: new Author(name: "JRR Tolkien", age: 110)])

    }

    void "test convert map to immutable object"() {
        when:
        def mapToObjectConverter = context.getBean(ConversionService)
        def optional = mapToObjectConverter.convert(['first_name': 'Todd'], ImmutablePerson)

        then:
        optional.isPresent()
        optional.get() instanceof ImmutablePerson
        optional.get().firstName == 'Todd'
    }

    @EqualsAndHashCode
    @ToString
    @Serdeable
    static class Book {
        String title
        URL url

        List<Author> authors = []
        Map<String, Author> authorsByInitials = [:]
    }

    @EqualsAndHashCode
    @ToString
    @Serdeable
    static class Author {
        String name
        Integer age

        Publisher publisher
    }

    @EqualsAndHashCode
    @ToString
    @Serdeable
    static class Publisher {
        String name
    }


    @Serdeable
    static class ImmutablePerson {

        @JsonProperty("first_name")
        private String firstName

        @JsonCreator
        ImmutablePerson(@JsonProperty("first_name") String firstName) {
            this.firstName = firstName
        }

        String getFirstName() {
            return firstName
        }
    }

    void 'mapping exceptions have proper metadata'() {
        given:
        BeanPropertyBinder binder = context.getBean(BeanPropertyBinder)

        when:
        binder.bind(FailingType, ['foo': '123blob'])

        then:
        def e = thrown ConversionErrorException

        expect:
        e.argument.type == int
        e.argument.name == 'foo'
        e.conversionError.originalValue == Optional.of('123blob')
    }

    @Serdeable
    static class FailingType {
        int foo
    }
}