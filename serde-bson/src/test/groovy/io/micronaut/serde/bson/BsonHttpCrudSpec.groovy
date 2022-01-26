package io.micronaut.serde.bson

import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.bson.types.ObjectId
import spock.lang.Specification

@MicronautTest
class BsonHttpCrudSpec extends Specification{
    @Inject BookClient client

    void "test http CRUD with BSON"() {
        when:
        def results = client.list()

        then:
        results.size() == 0

        when:
        def response = client.saveBook(new Book(title:"the stand", pages:1000))

        then:
        response.status() == HttpStatus.CREATED
        response.body().objectId
    }

    @Client("/book")
    static interface BookClient {
        @Get(uri="/")
        List<Book> list();

        @Get(uri = "/{bookId}")
        @Nullable
        Book getBook(ObjectId bookId)

        @Post("/")
        HttpResponse<Book> saveBook(@Body Book book)

        @Delete("/{bookId}")
        HttpResponse<?> deleteBook(ObjectId bookId)
    }

    @Controller("/book")
    static class BookController {
        List<Book> books = []
        @Get(uri="/")
        HttpResponse<List<Book>> getBooks() {
            return HttpResponse.ok(
                    books
            )
        }

        @Get(uri = "/{bookId}")
        HttpResponse<Book> getBook(ObjectId bookId) {
            return HttpResponse.ok( books.find { it.objectId == bookId } )
        }

        @Post("/")
        HttpResponse<Book> saveBook(@Body Book book) {
            book.objectId = new ObjectId();
            books.add(book)
            return HttpResponse.created(book)
        }

        @Delete("/{bookId}")
        HttpResponse<?> deleteBook(ObjectId bookId) {
            books.removeIf({ it.objectId == bookId })
            return HttpResponse.noContent()
        }
    }
}
