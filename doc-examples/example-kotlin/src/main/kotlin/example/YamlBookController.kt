package example

import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.serde.yaml.YamlMediaTypes

@Controller
class YamlBookController {

    @Post(uri = "/books", processes = [YamlMediaTypes.APPLICATION_YAML])
    fun save(@Body book: Book): Book = book
}
