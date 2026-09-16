package example;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.serde.yaml.YamlMediaTypes;

@Controller
public class YamlBookController {

    @Post(uri = "/books", processes = YamlMediaTypes.APPLICATION_YAML)
    public Book save(@Body Book book) {
        return book;
    }
}
