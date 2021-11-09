package io.micronaut.serde.jackson.maps;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.http.HttpStatus;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class EnumKeys {

    private final Map<HttpStatus, Integer> statusCodes;

    public EnumKeys(Map<HttpStatus, Integer> statusCodes) {
        this.statusCodes = statusCodes;
    }

    public Map<HttpStatus, Integer> getStatusCodes() {
        return statusCodes;
    }
}
