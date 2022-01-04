package example

import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.annotation.Serdeable.Deserializable
import io.micronaut.serde.annotation.Serdeable.Serializable

@Serdeable
data class Place(
        @Deserializable(using = ReversePointSerde::class) // <1>
        @Serializable(using = ReversePointSerde::class) // <2>
        val point: Point,

        @Serdeable.Serializable(using = example.ReversePointSerde::class)
        val pointCustomSer: Point,

        @Deserializable(using = ReversePointSerde::class)
        val pointCustomDes: Point
)