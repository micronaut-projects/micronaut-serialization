package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.PendingFeature
import spock.lang.Specification

@MicronautTest
class ProductTest extends Specification {
    @Inject ObjectMapper objectMapper

    @PendingFeature(reason = "micronaut-inject-groovy does not carry @SerdeImport mixin annotations over to the synthetic getters of Groovy properties, so the p_ prefixes from ProductMixin are not applied")
    void "test ser/deser"() {
        when:
        final String result = objectMapper.writeValueAsString(new Product("Apple", 10))

        then:
        result == '{"p_name":"Apple","p_quantity":10}'

        when:
        final Product product = objectMapper.readValue(result, Product)

        then:
        product.name == "Apple"
    }
}
