# TODO(python): the Python compiler cannot expose a dataclass attribute typed dict[Feature, Point] to Java
# (PythonCoercion.coerceMap only accepts Map<String, V>), so this snippet is not compiled yet.
# See DISABLED_TESTS.md.
#
# from dataclasses import dataclass
#
# from micronaut.serde.annotation import Serdeable
#
# from example.Feature import Feature
# from example.Point import Point
#
#
# @Serdeable
# @dataclass
# class Location:
#     features: dict[Feature, Point]
