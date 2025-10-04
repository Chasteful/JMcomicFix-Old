package net.ccbluex.liquidbounce.render.engine.type

import org.joml.Vector2f

@JvmRecord
data class Rect(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {

    val cx: Float get() = (x1 + x2) * 0.5F
    val cy: Float get() = (y1 + y2) * 0.5F
    val w: Float get() = x2 - x1
    val h: Float get() = y2 - y1

    val center: Vector2f
        get() = Vector2f(cx, cy)

    init {
        require(x1 <= x2)
        require(y1 <= y2)
    }

    fun contains(px: Float, py: Float): Boolean =
        px in x1..x2 && py in y1..y2

    fun intersects(other: Rect): Boolean =
        !(other.x1 > x2 || other.x2 < x1 || other.y1 > y2 || other.y2 < y1)

    companion object {
        @JvmStatic
        fun of(cx: Float, cy: Float, w: Float, h: Float): Rect {
            return Rect(cx - w * 0.5F, cy - h * 0.5F, cx + w * 0.5F, cy + h * 0.5F)
        }
    }

}
