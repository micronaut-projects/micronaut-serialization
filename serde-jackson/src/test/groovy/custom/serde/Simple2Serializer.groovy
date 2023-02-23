package custom.serde

import io.micronaut.core.annotation.NonNull
import io.micronaut.core.type.Argument
import io.micronaut.serde.Encoder
import io.micronaut.serde.Serializer
import io.micronaut.serde.jackson.Simple2
import jakarta.inject.Singleton

@Singleton
class Simple2Serializer implements Serializer<Simple2> {
    @Override
    void serialize(@NonNull Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends Simple2> type, @NonNull Simple2 value) throws IOException {
        encoder.encodeObject(type)
        encoder.encodeKey("nom")
        encoder.encodeString(value.name.toUpperCase(Locale.ENGLISH))
        encoder.finishStructure()
    }
}
