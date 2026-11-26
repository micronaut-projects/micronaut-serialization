package io.micronaut.serde.cbor

import io.micronaut.core.type.Argument
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.serde.cbor.data.Book
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class CborSerdeCacheSpec extends Specification {

    @Inject
    CborObjectMapper cborMapper

    def "the serdes of types without annotations are created once"() {
        given:
        def mapper = cborMapper.cloneWithViewClass(Object)
        def type = Argument.of(Book)
        def annotated = Argument.of(Book, 'book', new MutableAnnotationMetadata().tap {
            addAnnotation('io.micronaut.serde.annotation.Serdeable', [:])
        })

        when:
        def first = mapper.writeValueAsBytes(type, new Book("The Stand", 454))
        def second = mapper.writeValueAsBytes(new Book("It", 1138))
        def read = mapper.readValue(first, type)
        def readAgain = mapper.readValue(second, type)
        mapper.readValue(first, annotated)
        mapper.writeValueAsBytes(annotated, new Book("Carrie", 199))

        then:
        read == new Book("The Stand", 454)
        readAgain == new Book("It", 1138)
        mapper.@serdeCache.@serializers.size() == 1
        mapper.@serdeCache.@deserializers.size() == 1
    }
}
