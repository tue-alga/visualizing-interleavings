import org.openrndr.math.IntVector2
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.transforms.transform
import org.openrndr.shape.Rectangle
import kotlin.math.max
import kotlin.math.min

operator fun Matrix44.times(v: Vector2) = (this * v.xy01).xy
operator fun Matrix44.times(v: IntVector2) = this * v.vector2

fun Matrix44.Companion.fit(rect: Rectangle, into: Rectangle): Matrix44 =
    transform {
        translate(rect.center - into.corner)
        val maxScale = (rect.dimensions / into.dimensions).max()
        scale(maxScale)
        translate(-into.dimensions / 2.0)
    }.inversed

private fun Vector2.max(): Double = max(x, y)
private fun Vector2.min(): Double = min(x, y)