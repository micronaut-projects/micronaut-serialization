package example;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface ProductMixin {
    @JsonProperty("p_name")
    String getName();

    @JsonProperty("p_quantity")
    int getQuantity();
}
