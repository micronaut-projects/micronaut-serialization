package example.openapi.test.api

import io.micronaut.http.annotation.Controller
import example.openapi.test.model.Animal

@Controller
class RequestBodyApiImpl : RequestBodyApi {

    override fun sendModelWithDiscriminator(animal: Animal): Animal =
        animal
}
