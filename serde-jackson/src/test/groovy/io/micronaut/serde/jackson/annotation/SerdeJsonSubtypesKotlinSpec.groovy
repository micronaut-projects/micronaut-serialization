package io.micronaut.serde.jackson.annotation

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.core.beans.DefaultBeanIntrospector
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import org.intellij.lang.annotations.Language

class SerdeJsonSubtypesKotlinSpec extends AbstractKotlinCompilerSpec {

    Object beanUnderTest
    Argument<?> typeUnderTest
    JsonMapper jsonMapper

    ApplicationContext buildContext(String className, @Language("kotlin") String source, Map<String, Object> properties) {
        def context = buildContext(className, source, true)
        jsonMapper = context.getBean(JsonMapper)
        def t = context.classLoader.loadClass(className)
        typeUnderTest = Argument.of(t)
        beanUnderTest = t.newInstance(properties)
        return context
    }

    @Override
    ApplicationContext buildContext(@Language("kotlin") String source) {
        def context = buildContext("test.Source" + System.currentTimeMillis(), source, true)
        jsonMapper = context.getBean(JsonMapper)
        return context
    }

    @Override
    ApplicationContext buildContext(String className, @Language("kotlin") String cls) {
        def context = super.buildContext(className, cls, true)
        jsonMapper = context.getBean(JsonMapper)
        def t = context.classLoader.loadClass(className)
        typeUnderTest = Argument.of(t)
        return context
    }

    @Override
    ApplicationContext buildContext(String className, @Language("kotlin") String cls, boolean includeAllBeans) {
        def context = super.buildContext(className, cls, true)
        Thread.currentThread().setContextClassLoader(context.classLoader)
        resetLoadedIntrospections(context.classLoader)
        jsonMapper = context.getBean(JsonMapper)
        return context
    }

    private static void resetLoadedIntrospections(ClassLoader classLoader) {
        // Introspection were loaded in a different classloader
        BeanIntrospector shared = BeanIntrospector.SHARED
        shared.@introspectionMap = null
        ReflectionUtils.setField(DefaultBeanIntrospector, "classLoader", shared, classLoader)
    }

    Argument<Object> argumentOf(ApplicationContext context, String name) {
        return Argument.of(context.classLoader.loadClass(name))
    }

    static <T> T deserializeFromString(JsonMapper jsonMapper, Class<T> type, @Language("json") String json, Class<?> view = null) {
        if (view != null) {
            jsonMapper = jsonMapper.cloneWithViewClass(view)
        }
        return jsonMapper.readValue(json, type)
    }

    def 'test @JsonSubTypes'() {
        given:
        def context = buildContext('test.Animal', """
package test

import com.fasterxml.jackson.annotation.*
import io.micronaut.core.annotation.Nullable
import io.micronaut.serde.annotation.Serdeable
import java.math.BigDecimal

@Serdeable
@JsonPropertyOrder(
        Animal.JSON_PROPERTY_PROPERTY_CLASS,
        Animal.JSON_PROPERTY_COLOR
)
@JsonIgnoreProperties(
        value = ["class"],
        allowSetters = true
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "class", visible = true)
@JsonSubTypes(
        JsonSubTypes.Type(value = Bird::class, name = "ave")
)
open class Animal(

    @field:Nullable
    @field:JsonProperty(JSON_PROPERTY_PROPERTY_CLASS)
    @field:JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    open var propertyClass: String? = null,

    @field:Nullable
    @field:JsonProperty(JSON_PROPERTY_COLOR)
    @field:JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    open var color: ColorEnum? = null,
) {

    companion object {

        const val JSON_PROPERTY_PROPERTY_CLASS = "class"
        const val JSON_PROPERTY_COLOR = "color"
    }
}

@Serdeable
@JsonPropertyOrder(
    Bird.JSON_PROPERTY_NUM_WINGS,
    Bird.JSON_PROPERTY_BEAK_LENGTH,
    Bird.JSON_PROPERTY_FEATHER_DESCRIPTION,
)
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

    companion object {

        const val JSON_PROPERTY_NUM_WINGS = "numWings1"
        const val JSON_PROPERTY_BEAK_LENGTH = "beakLength"
        const val JSON_PROPERTY_FEATHER_DESCRIPTION = "featherDescription"
    }
}

@Serdeable
enum class ColorEnum(
        @get:JsonValue val value: String
) {

    @JsonProperty("red")
    RED("red");

    override fun toString(): String {
        return value
    }

    companion object {

        @JvmField
        val VALUE_MAPPING = entries.associateBy { it.value }

        @JsonCreator
        @JvmStatic
        fun fromValue(value: String): ColorEnum {
            require(VALUE_MAPPING.containsKey(value)) { "Unexpected value '\$value'" }
            return VALUE_MAPPING[value]!!
        }
    }
}

""", true)

        def baseClass = context.classLoader.loadClass('test.Animal')
        def cl = Thread.currentThread().getContextClassLoader()
        Thread.currentThread().setContextClassLoader(context.classLoader)

        when:
        var result = deserializeFromString(jsonMapper, baseClass, """{
            "class": "ave",
            "numWings1": 2,
            "beakLength": 12.1,
            "featherDescription": "this is description",
            "color": "red"
        }""")

        then:
        result.class.name == 'test.Bird'
        result.properties["propertyClass"] == 'ave'

        when:
        result.properties["propertyClass"] = 'Bird'
        var serialized = jsonMapper.writeValueAsString(result)

        then:
        serialized == '{"class":"ave","numWings1":2,"beakLength":12.1,"featherDescription":"this is description","color":"red"}'

        cleanup:
        Thread.currentThread().setContextClassLoader(cl)
        context.close()
    }

    def 'test @JsonSubTypes with constructor argument annotations'() {
        given:
        def context = buildContext('test.BookInfo', """
package test

import com.fasterxml.jackson.annotation.*
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Serdeable
@JsonIgnoreProperties(
    value = ["myType"], // ignore manually set type, it will be automatically generated by Jackson during serialization
    allowSetters = true, // allows the type to be set during deserialization
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "myType", visible = true)
@JsonSubTypes(
    JsonSubTypes.Type(value = BasicBookInfo::class, name = "BASIC"),
    JsonSubTypes.Type(value = DetailedBookInfo::class, name = "DETAILED"),
)
open class BookInfo(

    var name: String,
    @field:JsonProperty("myType")
    var type: BookInfoType? = null,
)

@Serdeable
open class BasicBookInfo(

    @field:NotNull
    @field:Size(min = 3)
    var author: String,

    name: String,
    type: BookInfoType? = null,
) : BookInfo(name, type)

@Serdeable
class DetailedBookInfo(

    @field:Pattern(regexp = "[0-9]{13}")
    var isbn: String,

    author: String,
    name: String,
    type: BookInfoType? = null,
) : BasicBookInfo(author, name, type)

@Serdeable
enum class BookInfoType(
    @get:JsonValue val value: String,
) {

    @JsonProperty("BASIC")
    BASIC("BASIC"),
    @JsonProperty("DETAILED")
    DETAILED("DETAILED"),
    ;

    override fun toString(): String = value

    companion object {

        @JvmField
        val VALUE_MAPPING = entries.associateBy { it.value }

        /**
         * Create this enum from a value.
         *
         * @param value The value
         *
         * @return The enum
         */
        @JsonCreator
        @JvmStatic
        fun fromValue(value: String): BookInfoType {
            require(VALUE_MAPPING.containsKey(value)) { "Unexpected value '\$value'" }
            return VALUE_MAPPING[value]!!
        }
    }
}

""", true)

        def baseClass = context.classLoader.loadClass('test.BookInfo')
        def cl = Thread.currentThread().getContextClassLoader()
        Thread.currentThread().setContextClassLoader(context.classLoader)

        expect:
        var result = deserializeFromString(jsonMapper, baseClass, """{
            "myType": "DETAILED",
            "author": "Some author",
            "name": "Book name",
            "isbn": "1234567890"
        }""")

        result.class.name == 'test.DetailedBookInfo'

        cleanup:
        Thread.currentThread().setContextClassLoader(cl)
        context.close()
    }
}
