package io.micronaut.json;

import java.io.IOException;

final class DeserializationException extends IOException {
    DeserializationException() {
    }

    DeserializationException(String message) {
        super(message);
    }

    DeserializationException(String message, Throwable cause) {
        super(message, cause);
    }

    DeserializationException(Throwable cause) {
        super(cause);
    }
}
