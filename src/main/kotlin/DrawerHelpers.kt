import org.openrndr.color.ColorRGBa
import org.openrndr.math.Vector2
import org.openrndr.shape.CompositionDrawer

fun CompositionDrawer.node(pos: Vector2, markRadius: Double) {
    stroke = ColorRGBa.BLACK
    fill = ColorRGBa.WHITE
    strokeWeight = markRadius / 3.0
    circle(pos, markRadius)
}

fun CompositionDrawer.node(x: Double, y: Double, markRadius: Double) {
    node(Vector2(x, y), markRadius)
}

fun CompositionDrawer.edge(pos1: Vector2, pos2: Vector2, markRadius: Double) {
    val control = Vector2(pos2.x, pos1.y)
    val c = org.openrndr.shape.contour {
        moveTo(pos1)
        curveTo(control, pos2)
    }
    strokeWeight = markRadius / 3.0
    contour(c)
}

fun CompositionDrawer.edge(x1: Double, y1: Double, x2: Double, y2: Double, markRadius: Double) {
    edge(Vector2(x1, y1), Vector2(x2, y2), markRadius)
}