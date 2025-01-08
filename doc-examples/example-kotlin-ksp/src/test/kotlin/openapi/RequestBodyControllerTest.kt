package openapi

import example.openapi.test.model.*
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.util.stream.Stream

@MicronautTest
class RequestBodyControllerTest(
        var server: EmbeddedServer,
        @Client("/")
        var reactiveClient: HttpClient,
) {

    lateinit var client: BlockingHttpClient

    @BeforeEach
    fun setup() {
        client = reactiveClient.toBlocking()
    }

    @MethodSource("discriminators")
    @ParameterizedTest
    fun testSendModelWithDiscriminatorChild1(discriminatorName: String, model: Animal) {
        val request = HttpRequest.PUT("/sendModelWithDiscriminator", model)
        val response = client.retrieve(request, Argument.of(Animal::class.java), Argument.of(String::class.java))

        assertEquals(discriminatorName, response.propertyClass)

        response.propertyClass = null
        assertEquals(model, response)

        val stringResponse = client.retrieve(request, Argument.of(String::class.java))
        assertTrue(stringResponse.contains(""""class":"$discriminatorName""""))
    }

    companion object {

        @JvmStatic
        fun discriminators(): Stream<Arguments> {
            val bird = Bird(2, BigDecimal.valueOf(12, 1), "Large blue and white feathers")
            bird.color = ColorEnum.BLUE
            val mammal = Mammal(20.5f, "A typical Canadian beaver")
            mammal.color = ColorEnum.BLUE
            val reptile = Reptile(0, true, "A pair of venomous fangs")
            reptile.color = ColorEnum.BLUE
            return Stream.of(
                    arguments("ave", bird),
                    arguments("mammalia", mammal),
                    arguments("reptilia", reptile)
            )
        }
    }
}
