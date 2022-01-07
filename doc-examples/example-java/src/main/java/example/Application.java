package example;

import io.micronaut.runtime.Micronaut;
import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(
    value = Product.class,
    mixin = ProductMixin.class
) // <1>
public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
