package example

import io.micronaut.serde.annotation.Serdeable
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonProperty

@Serdeable // <1>
class Book @BsonCreator constructor( // <3>
    val title: String,
    @field:BsonProperty("qty") val quantity: Int // <2>
)
