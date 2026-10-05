from typing import Annotated

from jakarta.inject import Named, Singleton
from micronaut.serde import ObjectMapper
from micronaut.serde.properties import PropertiesMapper


@Singleton
class PropertiesBookService:
    def __init__(self, properties_mapper: Annotated[ObjectMapper, Named(PropertiesMapper.NAME)]):
        self._properties_mapper = properties_mapper

    def properties_mapper(self) -> ObjectMapper:
        return self._properties_mapper
