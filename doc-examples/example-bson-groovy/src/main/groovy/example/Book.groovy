package example

import io.micronaut.serde.annotation.Serdeable
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonProperty

@Serdeable // <1>
class Book {
    final String title
    @BsonProperty("qty") // <2>
    final int quantity

    @BsonCreator // <3>
    Book(String title, int quantity) {
        this.title = title
        this.quantity = quantity
    }
}
