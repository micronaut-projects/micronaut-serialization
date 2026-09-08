package io.micronaut.serde.yaml

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
import io.micronaut.serde.yaml.body.YamlMessageHandler
import io.micronaut.serde.yaml.data.Book
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.netty.buffer.Unpooled
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class YamlHttpBodyHandlerSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    YamlObjectMapper yamlMapper

    @Inject
    YamlMessageHandler yamlMessageHandler

    def "post and receive a YAML body as #contentType"() {
        given:
        def book = new Book("The Stand", 454)

        when:
        def exchange = client.toBlocking().exchange(
                HttpRequest.POST("/yaml/echo", yamlMapper.writeValueAsString(book))
                        .contentType(contentType)
                        .accept(contentType),
                String
        )

        then:
        exchange.status() == HttpStatus.OK
        exchange.contentType.get().toString().startsWith(contentType)
        exchange.body() == "title: The Stand\npages: 454\n"
        yamlMapper.readValue(exchange.body(), Book) == book

        where:
        contentType << [YamlMediaTypes.APPLICATION_YAML, YamlMediaTypes.APPLICATION_X_YAML]
    }

    def "controller binds @Body from application/yaml"() {
        when:
        def result = client.toBlocking().retrieve(
                HttpRequest.POST("/yaml/title", "title: IT\npages: 1138\n")
                        .contentType(YamlMediaTypes.APPLICATION_YAML)
                        .accept(MediaType.TEXT_PLAIN),
                String
        )

        then:
        result == "IT"
    }

    def "releases a reference-counted buffer when decoding fails"() {
        given:
        ByteBuffer buffer = NettyByteBufferFactory.DEFAULT.wrap(Unpooled.wrappedBuffer("title: [oops\n".bytes))
        Headers headers = Mock()

        when:
        yamlMessageHandler.read(Argument.of(Book), YamlMediaTypes.APPLICATION_YAML_TYPE, headers, buffer)

        then:
        thrown(CodecException)
        buffer.asNativeBuffer().refCnt() == 0
    }

    def "the handler only accepts yaml media types"() {
        expect:
        yamlMessageHandler.isReadable(Argument.of(Book), YamlMediaTypes.APPLICATION_YAML_TYPE)
        yamlMessageHandler.isReadable(Argument.of(Book), YamlMediaTypes.APPLICATION_X_YAML_TYPE)
        !yamlMessageHandler.isReadable(Argument.of(Book), MediaType.APPLICATION_JSON_TYPE)
        !yamlMessageHandler.isWriteable(Argument.of(Book), null)
    }

    @Controller("/yaml")
    static class YamlController {

        @Post(uri = "/echo", processes = [YamlMediaTypes.APPLICATION_YAML, YamlMediaTypes.APPLICATION_X_YAML])
        Book echo(@Body Book book) {
            return book
        }

        @Post(uri = "/title", consumes = YamlMediaTypes.APPLICATION_YAML, produces = MediaType.TEXT_PLAIN)
        String title(@Body Book book) {
            return book.title()
        }
    }
}
