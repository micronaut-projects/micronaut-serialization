package io.micronaut.serde.toml.fixture;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Serdeable
@JsonPropertyOrder({"name", "sku", "color", "details", "variants"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Product {
    @Nullable
    private String name;
    @Nullable
    private Integer sku;
    @Nullable
    private String color;
    @Nullable
    private ProductDetails details;
    @Nullable
    private List<ProductVariant> variants;

    public Product() {
    }

    public Product(@Nullable String name, @Nullable Integer sku, @Nullable String color) {
        this.name = name;
        this.sku = sku;
        this.color = color;
    }

    public Product(@Nullable String name, @Nullable Integer sku,
                   @Nullable ProductDetails details, @Nullable List<ProductVariant> variants) {
        this.name = name;
        this.sku = sku;
        this.details = details;
        this.variants = variants;
    }

    public @Nullable String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    public @Nullable Integer getSku() {
        return sku;
    }

    public void setSku(@Nullable Integer sku) {
        this.sku = sku;
    }

    public @Nullable String getColor() {
        return color;
    }

    public void setColor(@Nullable String color) {
        this.color = color;
    }

    public @Nullable ProductDetails getDetails() {
        return details;
    }

    public void setDetails(@Nullable ProductDetails details) {
        this.details = details;
    }

    public @Nullable List<ProductVariant> getVariants() {
        return variants;
    }

    public void setVariants(@Nullable List<ProductVariant> variants) {
        this.variants = variants;
    }
}
