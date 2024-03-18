package example

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Singleton
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@MicronautTest
@Property(name = "spec.name", value = "FruitCommandTest")
class FruitCommandTest {

    @Test
    fun testCommandPost(embeddedServer: EmbeddedServer) {
        embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url).use { client ->
            val ex = assertThrows<HttpClientResponseException> {
                client.toBlocking()
                    .exchange<FruitCommand, String>(
                        HttpRequest.POST("/fruits", FruitCommand("", ""))
                    )
            }
            val embedded = ex.response.getBody(MutableMap::class.java).get().get("_embedded") as Map<String, Any>
            val message = ((embedded["errors"] as List<*>)[0] as Map<String, Any>)["message"]
            Assertions.assertEquals("fruitCommand.name: must not be empty", message)
        }
    }

    @Singleton
    @Controller("/fruits")
    @Requires(property = "spec.name", value = "FruitCommandTest")
    class FruitCommandController {

        @Post
        fun save(@Body fruitCommand: FruitCommand): FruitCommand {
            return fruitCommand
        }
    }
}
