class Point:
    def __init__(self, x: int, y: int):
        self._x = x
        self._y = y

    def coords(self) -> list[int]:
        return [self._x, self._y]

    @staticmethod
    def value_of(x: int, y: int) -> "Point":
        return Point(x, y)
