import org.openrndr.color.ColorRGBa
import org.openrndr.math.Vector2
import org.openrndr.shape.CompositionDrawer
import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour

fun CompositionDrawer.node(pos: Vector2, markRadius: Double) {
    stroke = ColorRGBa.BLACK
    fill = ColorRGBa.BLACK
    strokeWeight = markRadius / 4.0
    circle(pos, markRadius)
}

fun CompositionDrawer.node(x: Double, y: Double, markRadius: Double) {
    node(Vector2(x, y), markRadius)
}

fun edgeContour(pos1: Vector2, pos2: Vector2): ShapeContour {
    if (pos1.x == pos2.x) {
        return LineSegment(pos1, pos2).contour
    }

    val cornerPoint = Vector2(pos2.x, pos1.y)
    return LineSegment(cornerPoint, pos2).contour
}

fun horizontalConnector(pos1: Vector2, pos2: Vector2): ShapeContour {
    if (pos1.x == pos2.x) {
        return LineSegment(pos1, pos2).contour
    }

    val cornerPoint = Vector2(pos2.x, pos1.y)
    return LineSegment(cornerPoint, pos1).contour

}
