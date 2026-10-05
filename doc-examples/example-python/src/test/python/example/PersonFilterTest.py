from typing import Annotated

from jakarta.inject import Inject
from micronaut.serde import ObjectMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Person import Person


@MicronautTest
class PersonFilterTest:
    object_mapper: Annotated[ObjectMapper, Inject]

    @Test
    def test_write_person_without_preferred_name(self):
        result = self.object_mapper.writeValueAsString(Person("Adam", None))
        assert result == '{"name":"Adam"}'

    @Test
    def test_write_person_with_preferred_name(self):
        result = self.object_mapper.writeValueAsString(Person("Adam", "Ad"))
        assert result == '{"preferredName":"Ad"}'
