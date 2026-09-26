from typing import Annotated

from jakarta.inject import Inject
from micronaut.core.type import Argument
from micronaut.serde import ObjectMapper
from micronaut.serde.exceptions import SerdeException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.ReleaseRequest import ReleaseRequest


@MicronautTest
class StrictBuilderExampleTest:
    object_mapper: Annotated[ObjectMapper, Inject]

    @Test
    def test_default_value_is_applied_for_a_missing_property(self):
        request = self.object_mapper.readValue(
            '{"service":"checkout"}',
            Argument.of(ReleaseRequest),
        )

        assert request.service == "checkout"
        assert request.owner == "platform"
        assert request.notes is None

    @Test
    def test_missing_required_property_is_rejected(self):
        try:
            self.object_mapper.readValue('{"owner":"growth"}', Argument.of(ReleaseRequest))
            assert False, "expected a SerdeException"
        except SerdeException as e:
            assert "Required property" in e.getMessage()
