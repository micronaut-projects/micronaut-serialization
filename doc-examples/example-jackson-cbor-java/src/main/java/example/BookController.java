package example;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.serde.cbor.CborMediaTypes;

@Controller("/books")
public class BookController {

    @Post(processes = CborMediaTypes.APPLICATION_CBOR)
    public Book save(@Body Book book) {
        return book;
    }
}
