package io.micronaut.serde.toml

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.toml.fixture.Book
import io.micronaut.serde.toml.fixture.Product
import io.micronaut.serde.toml.fixture.ProductCatalog
import io.micronaut.serde.toml.fixture.ProductDetails
import io.micronaut.serde.toml.fixture.ProductVariant
import spock.lang.Specification

class TomlStyleConfigSpec extends Specification {

    void "reads book from toml"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def toml = """title = "Micronaut in Action"
pages = 320

[author]
name = "Ada"
"""

        when:
        def book = mapper.readValue(toml, Argument.of(Book))

        then:
        book.title == "Micronaut in Action"
        book.pages == 320
        book.author.name == "Ada"

        cleanup:
        ctx.close()
    }

    void "inline layout writes nested objects as inline tables"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.write-features.write-layout': 'inline'
        ])
        def mapper = tomlMapper(ctx)
        def book = new Book("Micronaut in Action", 320, new Book.Author("Ada"))

        when:
        def toml = mapper.writeValueAsString(book)

        then:
        toml == "title = 'Micronaut in Action'\npages = 320\nauthor = {name = 'Ada'}\n"

        when:
        def parsed = mapper.readValue(toml, Argument.of(Book))

        then:
        parsed.title == "Micronaut in Action"
        parsed.pages == 320
        parsed.author.name == "Ada"

        cleanup:
        ctx.close()
    }

    void "table layout writes nested objects as table headers"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def book = new Book("Micronaut in Action", 320, new Book.Author("Ada"))

        when:
        def toml = mapper.writeValueAsString(book)

        then:
        toml == "title = 'Micronaut in Action'\npages = 320\n\n[author]\nname = 'Ada'\n"

        cleanup:
        ctx.close()
    }

    void "table layout writes array of tables"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def catalog = productCatalog()

        when:
        def toml = mapper.writeValueAsString(catalog)

        then:
        toml == """[[products]]
name = 'Hammer'
sku = 738594937

[[products]]

[[products]]
name = 'Nail'
sku = 284758393
color = 'gray'
"""

        when:
        def parsed = mapper.readValue(toml, Argument.of(ProductCatalog))

        then:
        parsed.products.size() == 3
        parsed.products[0].name == "Hammer"
        parsed.products[0].sku == 738594937
        parsed.products[1].name == null
        parsed.products[1].sku == null
        parsed.products[2].name == "Nail"
        parsed.products[2].color == "gray"

        cleanup:
        ctx.close()
    }

    void "inline layout writes array of tables as inline"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.write-features.write-layout': 'inline'
        ])
        def mapper = tomlMapper(ctx)
        def catalog = productCatalog()

        when:
        def toml = mapper.writeValueAsString(catalog)

        then:
        toml == "products = [{name = 'Hammer', sku = 738594937}, {}, {name = 'Nail', sku = 284758393, color = 'gray'}]\n"

        when:
        def parsed = mapper.readValue(toml, Argument.of(ProductCatalog))

        then:
        parsed.products.size() == 3
        parsed.products[0].name == "Hammer"
        parsed.products[1].name == null
        parsed.products[2].color == "gray"

        cleanup:
        ctx.close()
    }

    void "table layout writes nested tables and array of tables inside array of tables"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def catalog = new ProductCatalog([
            new Product("Hammer", 100, new ProductDetails(500, "g"),
                [new ProductVariant("red"), new ProductVariant("blue")])
        ])

        when:
        def toml = mapper.writeValueAsString(catalog)

        then:
        toml == """[[products]]
name = 'Hammer'
sku = 100

[products.details]
weight = 500
unit = 'g'

[[products.variants]]
color = 'red'

[[products.variants]]
color = 'blue'
"""

        when:
        def parsed = mapper.readValue(toml, Argument.of(ProductCatalog))
        def product = parsed.products[0]

        then:
        product.name == "Hammer"
        product.sku == 100
        product.details.weight == 500
        product.details.unit == "g"
        product.variants.size() == 2
        product.variants[0].color == "red"
        product.variants[1].color == "blue"

        cleanup:
        ctx.close()
    }

    void "inline layout writes nested tables and array of tables as inline"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.write-features.write-layout': 'inline'
        ])
        def mapper = tomlMapper(ctx)
        def catalog = new ProductCatalog([
            new Product("Hammer", 100, new ProductDetails(500, "g"),
                [new ProductVariant("red"), new ProductVariant("blue")])
        ])

        when:
        def toml = mapper.writeValueAsString(catalog)

        then:
        toml == "products = [{name = 'Hammer', sku = 100, details = {weight = 500, unit = 'g'}, variants = [{color = 'red'}, {color = 'blue'}]}]\n"

        when:
        def parsed = mapper.readValue(toml, Argument.of(ProductCatalog))
        def product = parsed.products[0]

        then:
        product.name == "Hammer"
        product.sku == 100
        product.details.weight == 500
        product.details.unit == "g"
        product.variants.size() == 2
        product.variants[0].color == "red"
        product.variants[1].color == "blue"

        cleanup:
        ctx.close()
    }

    private static ObjectMapper tomlMapper(ApplicationContext ctx) {
        ctx.getBean(ObjectMapper, Qualifiers.byName("toml"))
    }

    private static ProductCatalog productCatalog() {
        new ProductCatalog([
            new Product("Hammer", 738594937, null as String),
            new Product(null, null, null as String),
            new Product("Nail", 284758393, "gray")
        ])
    }
}
