package io.micronaut.serde.exceptions;

import java.io.IOException;

public final class SerdeException extends IOException {
    public SerdeException() {
    }

    public SerdeException(String message) {
        super(message);
    }

    public SerdeException(String message, Throwable cause) {
        super(message, cause);
    }

    public SerdeException(Throwable cause) {
        super(cause);
    }
}
