package example;

public final class Point {
    private final int x, y;

    private Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int[] coords() {
        return new int[] { x, y };
    }

    public static Point valueOf(int x, int y) {
        return new Point(x, y);
    }
}
