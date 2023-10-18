package example

class Point private constructor(private val x: Int, private val y: Int) {
    fun coords(): IntArray {
        return intArrayOf(x, y)
    }

    companion object {
        fun valueOf(x: Int, y: Int): Point {
            return Point(x, y)
        }
    }
}