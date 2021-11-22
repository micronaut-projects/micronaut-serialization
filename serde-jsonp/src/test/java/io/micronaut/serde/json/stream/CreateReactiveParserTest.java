package io.micronaut.serde.json.stream;

import java.util.Arrays;
import java.util.List;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

@MicronautTest
public class CreateReactiveParserTest {

    @Test
    void testStreamArray(StreamClient client) {
        final List<Book> results = client.booksFlux(Flux.fromIterable(Arrays.asList(
                new Book("One"),
                new Book("Two"),
                new Book("Three")
        ))).collectList().block();

        Assertions.assertEquals(3, results.size());
    }

    @Test
    void testStream(StreamClient client) {
        final List<Book> results = client.booksStream(Flux.fromIterable(Arrays.asList(
                new Book(StringGroovyMethods.multiply("One", 1000)),
                new Book("Two"),
                new Book("Three")
        ))).collectList().block();

        Assertions.assertEquals(3, results.size());
    }

    @Test
    void testStreamBig(StreamClient client) {
        final List<Book> results = client.booksStream(Flux.fromIterable(Arrays.asList(
                new Book("One"),
                new Book("Two"),
                new Book("Three")
        ))).collectList().block();

        Assertions.assertEquals(3, results.size());
    }

    @Controller("/stream")
    static class StreamMe {
        @Post Flux<Book> booksFlux(@Body Flux<Book> body) {
            return body;
        }

        @Post(uri = "/json", processes = MediaType.APPLICATION_JSON_STREAM) Flux<Book> booksStream(@Body Flux<Book> body) {
            return body;
        }
    }

    @Client("/stream")
    interface StreamClient {
        @Post("/") Flux<Book> booksFlux(@Body Flux<Book> body);
        @Post(uri = "/json", processes = MediaType.APPLICATION_JSON_STREAM) Flux<Book> booksStream(@Body Flux<Book> body);
    }

    @Serdeable
    static class Book {
        private final String title;

        Book(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }
}
