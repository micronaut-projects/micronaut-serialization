package example

import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.serde.cbor.CborMediaTypes

@Controller("/books")
class BookController {

    @Post(processes = CborMediaTypes.APPLICATION_CBOR)
    Book save(@Body Book book) {
        return book
    }
}
