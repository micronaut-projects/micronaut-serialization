from jakarta.inject import Singleton
from jakarta.json.bind import JsonbConfig
from micronaut.context.annotation import Factory, Requires

from example.ProgrammaticCodeDeserializer import ProgrammaticCodeDeserializer
from example.ProgrammaticCodeSerializer import ProgrammaticCodeSerializer


@Factory
@Requires(property="spec.name", value="jsonb-programmatic-config")
class ProgrammaticJsonbConfigFactory:
    @Singleton
    def jsonb_config(self) -> JsonbConfig:
        return (
            JsonbConfig()
            .withSerializers(ProgrammaticCodeSerializer())
            .withDeserializers(ProgrammaticCodeDeserializer())
        )
