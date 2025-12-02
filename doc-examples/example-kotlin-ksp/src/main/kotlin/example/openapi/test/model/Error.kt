package example.openapi.test.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import io.micronaut.core.annotation.Nullable
import io.micronaut.serde.annotation.Serdeable
import jakarta.annotation.Generated

/**
 * An object for describing errors
 */
@Serdeable
@JsonPropertyOrder(
    Error.JSON_PROPERTY_MESSAGE,
)
@Generated("io.micronaut.openapi.generator.KotlinMicronautServerCodegen")
data class Error(

    /**
     * The error message
     */
    @field:Nullable
    @field:JsonProperty(JSON_PROPERTY_MESSAGE)
    @field:JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    var message: String? = null,
) {

    companion object {

        const val JSON_PROPERTY_MESSAGE = "message"
    }
}
