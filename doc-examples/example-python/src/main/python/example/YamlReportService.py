from typing import Annotated

from jakarta.inject import Named, Singleton
from micronaut.serde import ObjectMapper
from micronaut.serde.yaml import YamlObjectMapper


@Singleton
class YamlReportService:
    def __init__(self, yaml_mapper: Annotated[ObjectMapper, Named(YamlObjectMapper.YAML)]):
        self._yaml_mapper = yaml_mapper

    def yaml_mapper(self) -> ObjectMapper:
        return self._yaml_mapper
