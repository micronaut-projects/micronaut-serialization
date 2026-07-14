package example;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import jakarta.json.bind.JsonbConfig;

@Factory
@Requires(property = "spec.name", value = "jsonb-programmatic-config")
public final class ProgrammaticJsonbConfigFactory {
    @Singleton
    JsonbConfig jsonbConfig() {
        return new JsonbConfig()
            .withSerializers(new ProgrammaticCodeSerializer())
            .withDeserializers(new ProgrammaticCodeDeserializer());
    }
}
