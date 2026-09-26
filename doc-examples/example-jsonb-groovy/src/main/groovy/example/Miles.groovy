package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
final class Miles {
    final int value

    Miles(int value) {
        this.value = value
    }
}
