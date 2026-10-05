from typing import Annotated

from jakarta.inject import Inject
from jakarta.json.bind import Jsonb
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Color import Color
from example.Miles import Miles


@Property(name="spec.name", value="jsonb-extension-beans")
@Property(name="micronaut.serde.jsonb.reflection", value="AUTO")
@MicronautTest
class JsonbExtensionTest:
    jsonb: Annotated[Jsonb, Inject]

    @Test
    def jsonb_extension_beans_are_registered_in_priority_order(self):
        assert self.jsonb.toJson(Color("ff0000")) == '"#ff0000"'
        assert self.jsonb.fromJson('"#00ff00"', Color).value == "00ff00"
        assert self.jsonb.toJson(Miles(12)) == '"12 mi"'
        assert self.jsonb.fromJson('"15 mi"', Miles).value == 15
