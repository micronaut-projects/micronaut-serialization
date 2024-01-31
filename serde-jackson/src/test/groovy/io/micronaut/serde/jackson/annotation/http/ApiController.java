package io.micronaut.serde.jackson.annotation.http;

import io.micronaut.http.annotation.Controller;
import io.micronaut.serde.ApiResponse;
import io.micronaut.serde.Dummy;

import java.util.List;

@Controller
public class ApiController implements Api {

    @Override
    public ApiResponse<List<Dummy>> wrappedList() {
        return new ApiResponse<>(
            List.of(
                new Dummy("test-1"),
                new Dummy("test-2")
            )
        );
    }

    @Override
    public ApiResponse<ApiResponse<Dummy>> wrappedNested() {
        return new ApiResponse<>(
            new ApiResponse<>(
                new Dummy("test-1")
            )
        );
    }

    @Override
    public ApiResponse<Dummy> wrappedSimple() {
        return new ApiResponse<>(
            new Dummy("test-1")
        );
    }

    @Override
    public List<Dummy> simpleList() {
        return List.of(
            new Dummy("test-1"),
            new Dummy("test-2")
        );
    }
}
