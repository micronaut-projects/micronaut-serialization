package io.micronaut.serde.toml.fixture;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
@JsonPropertyOrder({"products"})
public class ProductCatalog {
    private List<Product> products;

    public ProductCatalog() {
    }

    public ProductCatalog(List<Product> products) {
        this.products = products;
    }

    public List<Product> getProducts() {
        return products;
    }

    public void setProducts(List<Product> products) {
        this.products = products;
    }
}
