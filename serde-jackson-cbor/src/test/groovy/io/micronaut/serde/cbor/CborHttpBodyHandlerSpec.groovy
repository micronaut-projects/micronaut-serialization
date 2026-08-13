package io.micronaut.serde.cbor

import io.micronaut.buffer.netty.NettyByteBufferFactory
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.type.Argument
import io.micronaut.core.type.Headers
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.codec.CodecException
import io.micronaut.serde.cbor.body.CborMessageHandler
import io.micronaut.serde.cbor.data.Book
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import io.netty.buffer.Unpooled

@MicronautTest
class CborHttpBodyHandlerSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    CborObjectMapper cborMapper

    @Inject
    CborMessageHandler cborMessageHandler

    def "post and receive CBOR body"() {
        given:
        def book = new Book("The Stand", 454)
        byte[] requestBody = cborMapper.writeValueAsBytes(book)

        when:
        def exchange = client.toBlocking().exchange(
            HttpRequest.POST("/cbor/echo", requestBody)
                .contentType(CborMediaTypes.APPLICATION_CBOR)
                .accept(CborMediaTypes.APPLICATION_CBOR),
            byte[]
        )

        then:
        exchange.status() == HttpStatus.OK
        exchange.contentType.isPresent()
        exchange.contentType.get().toString().startsWith(CborMediaTypes.APPLICATION_CBOR)

        when:
        def responseBook = cborMapper.readValue(exchange.body(), Book)

        then:
        responseBook == book
    }

    def "controller binds @Body from application/cbor"() {
        given:
        def book = new Book("IT", 1138)

        when:
        def result = client.toBlocking().retrieve(
            HttpRequest.POST("/cbor/title", cborMapper.writeValueAsBytes(book))
                .contentType(CborMediaTypes.APPLICATION_CBOR)
                .accept(MediaType.TEXT_PLAIN),
            String
        )

        then:
        result == "IT"
    }

    def "releases a reference-counted buffer when decoding fails"() {
        given:
        ByteBuffer buffer = NettyByteBufferFactory.DEFAULT.wrap(Unpooled.wrappedBuffer([0xFF] as byte[]))
        Headers headers = Mock()

        when:
        cborMessageHandler.read(Argument.of(Book), CborMediaTypes.APPLICATION_CBOR_TYPE, headers, buffer)

        then:
        thrown(CodecException)
        buffer.asNativeBuffer().refCnt() == 0
    }

    @Controller("/cbor")
    static class CborController {

        @Post(uri = "/echo", processes = CborMediaTypes.APPLICATION_CBOR)
        Book echo(@Body Book book) {
            return book
        }

        @Post(uri = "/title", consumes = CborMediaTypes.APPLICATION_CBOR, produces = MediaType.TEXT_PLAIN)
        String title(@Body Book book) {
            return book.title()
        }
    }
}
