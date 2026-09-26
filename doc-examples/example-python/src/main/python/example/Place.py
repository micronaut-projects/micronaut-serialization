from dataclasses import dataclass
from typing import Annotated

from micronaut.serde.annotation import Serdeable

from example.Point import Point
from example.ReversePointSerde import ReversePointSerde


@Serdeable
@dataclass
class Place:
    point: Annotated[
        Point,
        Serdeable.Serializable(using=ReversePointSerde),  # <1>
        Serdeable.Deserializable(using=ReversePointSerde),  # <2>
    ]
    pointCustomSer: Annotated[Point, Serdeable.Serializable(using=ReversePointSerde)]
    pointCustomDes: Annotated[Point, Serdeable.Deserializable(using=ReversePointSerde)]
