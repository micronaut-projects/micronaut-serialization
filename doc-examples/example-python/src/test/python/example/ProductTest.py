from typing import Annotated

from jakarta.inject import Inject
from micronaut.serde import ObjectMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Product import Product


@MicronautTest
class ProductTest:
    object_mapper: Annotated[ObjectMapper, Inject]

    @Test
    def test_ser_deser(self):
        result = self.object_mapper.writeValueAsString(Product("Apple", 10))
        assert result == '{"p_name":"Apple","p_quantity":10}'
        product = self.object_mapper.readValue(result, Product)
        assert product.name == "Apple"
