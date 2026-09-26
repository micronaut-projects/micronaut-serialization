from dataclasses import dataclass

from micronaut.serde.annotation import Serdeable

from example.Feature import Feature
from example.Point import Point


@Serdeable
@dataclass
class Location:
    features: dict[Feature, Point]
