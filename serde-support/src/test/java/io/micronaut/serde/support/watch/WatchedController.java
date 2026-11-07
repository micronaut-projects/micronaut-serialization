package io.micronaut.serde.support.watch;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;

/**
 * Serves a serializable value as JSON, so that an application has its mappers in use.
 */
@Requires(property = "serde.watch.controller")
@Controller("/watched")
public class WatchedController {

    @Get
    public WatchedValue get() {
        return new WatchedValue("a");
    }

    @Post
    public WatchedValue post(@Body WatchedValue value) {
        return value;
    }
}
