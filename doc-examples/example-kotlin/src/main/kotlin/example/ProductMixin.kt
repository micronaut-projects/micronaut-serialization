package example

import com.fasterxml.jackson.annotation.JsonProperty

interface ProductMixin {
    @get:JsonProperty("p_name")
    val name: String

    @get:JsonProperty("p_quantity")
    val quantity: Int
}