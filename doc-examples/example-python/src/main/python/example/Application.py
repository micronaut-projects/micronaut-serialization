from micronaut.serde.annotation import SerdeImport

from example.Product import Product
from example.ProductMixin import ProductMixin


@SerdeImport(value=Product, mixin=ProductMixin)  # <1>
class Application:
    pass
