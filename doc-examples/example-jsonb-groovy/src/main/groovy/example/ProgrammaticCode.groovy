package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
final class ProgrammaticCode {
    final String value

    ProgrammaticCode(String value) {
        this.value = value
    }
}
