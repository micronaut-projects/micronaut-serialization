package example

import io.micronaut.runtime.Micronaut;
import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(
    value = Product.class,
    mixin = ProductMixin.class
) // <1>
class Application {

    static void main(String[] args) {
        Micronaut.run(Application.class, args)
    }
}
