package example

import io.micronaut.runtime.Micronaut.*
import io.micronaut.serde.annotation.SerdeImport

fun main(args: Array<String>) {
    build()
        .args(*args)
        .packages("example")
        .start()
}

@SerdeImport(
    value = Product::class,
    mixin = ProductMixin::class) // <1>
class Serdes {}
