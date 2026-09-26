from jakarta.inject import Singleton
from java.util import Optional
from micronaut.core.convert import ConversionContext, TypeConverter


class Feature:
    def __init__(self, name: str):
        self._name = name

    def name(self) -> str:
        return self._name

    def __str__(self) -> str:  # <1>
        return self._name


@Singleton
class FeatureConverter(TypeConverter[str, Feature]):  # <2>
    def convert(self, object: str, target_type: type[Feature], context: ConversionContext):
        return Optional.of(Feature(object))
