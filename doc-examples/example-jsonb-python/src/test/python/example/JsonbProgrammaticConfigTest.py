from typing import Annotated

from jakarta.inject import Inject
from jakarta.json.bind import Jsonb
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.ProgrammaticCode import ProgrammaticCode


@Property(name="spec.name", value="jsonb-programmatic-config")
@Property(name="micronaut.serde.jsonb.reflection", value="AUTO")
@MicronautTest
class JsonbProgrammaticConfigTest:
    jsonb: Annotated[Jsonb, Inject]

    @Test
    def jsonb_extensions_can_be_registered_programmatically(self):
        assert self.jsonb.toJson(ProgrammaticCode("A1")) == '"code:A1"'
        assert self.jsonb.fromJson('"code:B2"', ProgrammaticCode).value == "B2"
