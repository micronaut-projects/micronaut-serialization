from typing import Annotated

from jakarta.inject import Named, Singleton
from micronaut.serde import ObjectMapper


@Singleton
class PropertiesBookService:
    # TODO(python): Named(PropertiesMapper.NAME) fails to transform, the qualifier is spelled out
    def __init__(self, properties_mapper: Annotated[ObjectMapper, Named("properties")]):
        self._properties_mapper = properties_mapper

    def properties_mapper(self) -> ObjectMapper:
        return self._properties_mapper
