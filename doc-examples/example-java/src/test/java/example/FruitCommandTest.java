package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
@Property(name = "spec.name", value = "FruitCommandTest")
class FruitCommandTest {

    @Test
    void testCommandPost(EmbeddedServer embeddedServer) {
        try(var client = embeddedServer.getApplicationContext().createBean(HttpClient.class, embeddedServer.getURL())) {
            var ex = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/fruits", new FruitCommand("", "")), String.class)
            );
            Map<String, Object> embedded = (Map<String, Object>) ex.getResponse().getBody(Map.class).get().get("_embedded");
            Object message = ((Map<String, Object>) ((List) embedded.get("errors")).get(0)).get("message");

            assertEquals("fruitCommand.name: must not be empty", message);
        }
    }

    @Controller("/fruits")
    @Requires(property = "spec.name", value = "FruitCommandTest")
    static class FruitCommandController {

        @Post
        FruitCommand save(@Valid @Body FruitCommand fruitCommand) {
            return fruitCommand;
        }
    }
}
