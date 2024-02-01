package io.micronaut.serde.jackson.annotation.http;

import io.micronaut.http.annotation.Get;
import io.micronaut.serde.ApiResponse;
import io.micronaut.serde.Dummy;

import java.util.List;

public interface Api {

    @Get("/api/dummy/wrapped/list")
    ApiResponse<List<Dummy>> wrappedList();

    @Get("/api/dummy/wrapped/nested")
    ApiResponse<ApiResponse<Dummy>> wrappedNested();

    @Get("/api/dummy/wrapped/simple")
    ApiResponse<Dummy> wrappedSimple();

    @Get("/api/dummy/raw")
    List<Dummy> simpleList();

}
