package io.micronaut.serde.jackson.annotation

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.context.ApplicationContext
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
        jsonMapper = context.getBean(JsonMapper)
        return context
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
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import java.math.BigDecimal

@Introspected
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
        @Nullable
        @JsonProperty(JSON_PROPERTY_COLOR)
        @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
        open var color: ColorEnum? = null,
        @Nullable
        @JsonProperty(JSON_PROPERTY_PROPERTY_CLASS)
        @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
        open var propertyClass: String? = null,
) {

    companion object {

        const val JSON_PROPERTY_PROPERTY_CLASS = "class"
        const val JSON_PROPERTY_COLOR = "color"
    }
}

@Introspected
@Serdeable
@JsonPropertyOrder(
        Bird.JSON_PROPERTY_NUM_WINGS,
        Bird.JSON_PROPERTY_BEAK_LENGTH,
        Bird.JSON_PROPERTY_FEATHER_DESCRIPTION
)
data class Bird(
        @Nullable
        @JsonProperty(JSON_PROPERTY_NUM_WINGS)
        @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
        var numWings: Int? = null,
        @Nullable
        @JsonProperty(JSON_PROPERTY_BEAK_LENGTH)
        @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
        var beakLength: BigDecimal? = null,
        @Nullable
        @JsonProperty(JSON_PROPERTY_FEATHER_DESCRIPTION)
        @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
        var featherDescription: String? = null,
) : Animal() {

    companion object {

        const val JSON_PROPERTY_NUM_WINGS = "numWings"
        const val JSON_PROPERTY_BEAK_LENGTH = "beakLength"
        const val JSON_PROPERTY_FEATHER_DESCRIPTION = "featherDescription"
    }
}

@Introspected
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

        expect:
        deserializeFromString(jsonMapper, baseClass, """{
            "numWings": 2,
            "beakLength": 12.1,
            "featherDescription": "this is description",
            "class": "ave",
            "color": "red"
        }""").class.simpleName == 'tests.Bird'
//
//        def baseArg = argumentOf(context, "test.Animal")
//        def result = jsonMapper.readValue("""{
//            "numWings": 2,
//            "beakLength": 12.1,
//            "featherDescription": "this is description",
//            "class": "ave",
//            "color": "red"
//        }""", baseArg)
//
//        then:
//        result.class.name == 'tests.Bird'
//        result.propertyClass == 'ave'

        cleanup:
        Thread.currentThread().setContextClassLoader(cl)
        context.close()
    }
}
