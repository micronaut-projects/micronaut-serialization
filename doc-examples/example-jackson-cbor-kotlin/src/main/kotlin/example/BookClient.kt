package example

import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.serde.cbor.CborMediaTypes

@Client("/books")
interface BookClient {

    @Post(processes = [CborMediaTypes.APPLICATION_CBOR])
    fun save(@Body book: Book): Book
}
