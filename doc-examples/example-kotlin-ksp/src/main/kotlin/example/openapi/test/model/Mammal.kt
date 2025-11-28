package example.openapi.test.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import org.jspecify.annotations.Nullable
import io.micronaut.serde.annotation.Serdeable
import jakarta.annotation.Generated
import java.util.*

/**
 * Mammal
 */
@Serdeable
@JsonPropertyOrder(
    Mammal.JSON_PROPERTY_WEIGHT,
    Mammal.JSON_PROPERTY_DESCRIPTION,
)
@Generated("io.micronaut.openapi.generator.KotlinMicronautServerCodegen")
class Mammal(

    @field:JsonProperty(JSON_PROPERTY_WEIGHT)
    var weight: Float,

    @field:JsonProperty(JSON_PROPERTY_DESCRIPTION)
    var description: String,

    @Nullable
    @JsonProperty(JSON_PROPERTY_PROPERTY_CLASS)
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    propertyClass: String? = null,

    @Nullable
    @JsonProperty(JSON_PROPERTY_COLOR)
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    color: ColorEnum? = null,
) : Animal(propertyClass, color) {

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        other as Mammal
        return weight == other.weight
                && description == other.description
                && super.equals(other)
    }

    override fun hashCode(): Int =
        Objects.hash(weight, description, super.hashCode())

    override fun toString(): String =
        "Mammal(weight='$weight', description='$description', propertyClass='$propertyClass', color='$color')"

    companion object {

        const val JSON_PROPERTY_WEIGHT = "weight"
        const val JSON_PROPERTY_DESCRIPTION = "description"
    }
}
