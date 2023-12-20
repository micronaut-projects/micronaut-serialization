package io.micronaut.serde.jackson.jsonvalue;


import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public enum TokenError {
    INVALID_REQUEST("invalid_request"),
    INVALID_CLIENT("invalid_client"),
    INVALID_GRANT("invalid_grant"),
    UNAUTHORIZED_CLIENT("unauthorized_client"),
    UNSUPPORTED_GRANT_TYPE("unsupported_grant_type"),
    INVALID_SCOPE("invalid_scope");

    private final String errorCode;

    /**
     * @param errorCode The error code
     */
    TokenError(String errorCode) {
        this.errorCode = errorCode;
    }

    /**
     *
     * @return An errorCode code.
     */
    @Override
    @JsonValue
    public String toString() {
        return errorCode;
    }

}
