package custom.serde

import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.type.Argument
import io.micronaut.serde.Encoder
import io.micronaut.serde.Serializer
import io.micronaut.serde.config.SerdeConfiguration
import io.micronaut.serde.jackson.Simple2
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class Simple2Serializer implements Serializer<Simple2> {
    @Inject
    SerdeConfiguration configuration

    @Override
    void serialize(@NonNull Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends Simple2> type, @NonNull Simple2 value) throws IOException {

        if (!configuration.getDateFormat().isPresent()) {
            throw new IllegalStateException("Date format not set, configuration not passed to ObjectMapper.create")
        }
        encoder.encodeObject(type)
        encoder.encodeKey("nom")
        encoder.encodeString(value.name.toUpperCase(Locale.ENGLISH))
        encoder.finishStructure()
    }
}
