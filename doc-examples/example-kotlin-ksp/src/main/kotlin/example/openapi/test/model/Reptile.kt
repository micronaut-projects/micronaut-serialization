package example.openapi.test.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import io.micronaut.core.annotation.Nullable
import io.micronaut.serde.annotation.Serdeable
import jakarta.annotation.Generated
import java.util.*

/**
 * Reptile
 */
@Serdeable
@JsonPropertyOrder(
    Reptile.JSON_PROPERTY_NUM_LEGS,
    Reptile.JSON_PROPERTY_FANGS,
    Reptile.JSON_PROPERTY_FANG_DESCRIPTION,
)
@Generated("io.micronaut.openapi.generator.KotlinMicronautServerCodegen")
class Reptile(

    @field:JsonProperty(JSON_PROPERTY_NUM_LEGS)
    var numLegs: Int,

    @field:JsonProperty(JSON_PROPERTY_FANGS)
    var fangs: Boolean,

    @field:Nullable
    @field:JsonProperty(JSON_PROPERTY_FANG_DESCRIPTION)
    @field:JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    var fangDescription: String? = null,

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
        other as Reptile
        return numLegs == other.numLegs
                && fangs == other.fangs
                && fangDescription == other.fangDescription
                && super.equals(other)
    }

    override fun hashCode(): Int =
        Objects.hash(numLegs, fangs, fangDescription, super.hashCode())

    override fun toString(): String =
        "Reptile(numLegs='$numLegs', fangs='$fangs', fangDescription='$fangDescription', propertyClass='$propertyClass', color='$color')"

    companion object {

        const val JSON_PROPERTY_NUM_LEGS = "numLegs"
        const val JSON_PROPERTY_FANGS = "fangs"
        const val JSON_PROPERTY_FANG_DESCRIPTION = "fangDescription"
    }
}
