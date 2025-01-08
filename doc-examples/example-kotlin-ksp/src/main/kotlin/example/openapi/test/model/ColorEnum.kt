package example.openapi.test.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import io.micronaut.serde.annotation.Serdeable
import jakarta.annotation.Generated

/**
 * Gets or Sets ColorEnum
 *
 * @param value The value represented by this enum
 */
@Serdeable
@Generated("io.micronaut.openapi.generator.KotlinMicronautServerCodegen")
enum class ColorEnum(
    @get:JsonValue val value: String,
) {

    @JsonProperty("red")
    RED("red"),

    @JsonProperty("blue")
    BLUE("blue"),

    @JsonProperty("green")
    GREEN("green"),

    @JsonProperty("light-blue")
    LIGHT_BLUE("light-blue"),

    @JsonProperty("dark-green")
    DARK_GREEN("dark-green"),
    ;

    override fun toString(): String = value

    companion object {

        @JvmField
        val VALUE_MAPPING = entries.associateBy { it.value.lowercase() }

        /**
         * Create this enum from a value.
         *
         * @param value The value
         *
         * @return The enum
         */
        @JsonCreator
        @JvmStatic
        fun fromValue(value: String): ColorEnum {
            val key = value.lowercase()
            require(VALUE_MAPPING.containsKey(key)) { "Unexpected value '$key'" }
            return VALUE_MAPPING[key]!!
        }
    }
}
