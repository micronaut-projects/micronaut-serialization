package example.openapi.test.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import io.micronaut.core.annotation.Nullable
import io.micronaut.serde.annotation.Serdeable
import jakarta.annotation.Generated
import java.math.BigDecimal
import java.util.*

/**
 * Bird
 */
@Serdeable
@JsonPropertyOrder(
    Bird.JSON_PROPERTY_NUM_WINGS,
    Bird.JSON_PROPERTY_BEAK_LENGTH,
    Bird.JSON_PROPERTY_FEATHER_DESCRIPTION,
)
@Generated("io.micronaut.openapi.generator.KotlinMicronautServerCodegen")
class Bird(

    @field:Nullable
    @field:JsonProperty(JSON_PROPERTY_NUM_WINGS)
    @field:JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    var numWings: Int? = null,

    @field:Nullable
    @field:JsonProperty(JSON_PROPERTY_BEAK_LENGTH)
    @field:JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    var beakLength: BigDecimal? = null,

    @field:Nullable
    @field:JsonProperty(JSON_PROPERTY_FEATHER_DESCRIPTION)
    @field:JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    var featherDescription: String? = null,

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
        other as Bird
        return numWings == other.numWings
                && beakLength == other.beakLength
                && featherDescription == other.featherDescription
                && super.equals(other)
    }

    override fun hashCode(): Int =
        Objects.hash(numWings, beakLength, featherDescription, super.hashCode())

    override fun toString(): String =
        "Bird(numWings='$numWings', beakLength='$beakLength', featherDescription='$featherDescription', propertyClass='$propertyClass', color='$color')"

    companion object {

        const val JSON_PROPERTY_NUM_WINGS = "numWings"
        const val JSON_PROPERTY_BEAK_LENGTH = "beakLength"
        const val JSON_PROPERTY_FEATHER_DESCRIPTION = "featherDescription"
    }
}
