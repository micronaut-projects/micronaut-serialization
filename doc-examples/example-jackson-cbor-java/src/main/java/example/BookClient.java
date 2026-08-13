package example;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.cbor.CborMediaTypes;

@Client("/books")
public interface BookClient {

    @Post(processes = CborMediaTypes.APPLICATION_CBOR)
    Book save(@Body Book book);
}
