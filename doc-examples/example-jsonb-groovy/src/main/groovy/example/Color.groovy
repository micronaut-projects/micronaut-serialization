package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
final class Color {
    final String value

    Color(String value) {
        this.value = value
    }
}
