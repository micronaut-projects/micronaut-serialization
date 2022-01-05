package example

final class Point {
    private final int x, y

    private Point(int x, int y) {
        this.x = x
        this.y = y
    }

    int[] coords() {
        return new int[] { x, y }
    }

    static Point valueOf(int x, int y) {
        return new Point(x, y)
    }
}
