package example.openapi.test.api

import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Put
import example.openapi.test.model.Animal

@Controller
interface RequestBodyApi {

    /**
     * A method to send a model with discriminator in body
     *
     * @param animal (required)
     * @return Animal
     */
    @Put("/sendModelWithDiscriminator")
    fun sendModelWithDiscriminator(
        @Body animal: Animal,
    ): Animal
}
