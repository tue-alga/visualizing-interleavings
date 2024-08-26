import org.openrndr.extra.parameters.BooleanParameter
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.math.Vector2
import org.openrndr.shape.CompositionDrawer
import org.openrndr.shape.Rectangle
import kotlin.math.abs

data class TreeDrawSettings(
    @DoubleParameter("Node width", 0.1, 50.0)
    var nodeWidth: Double = 20.0,
    @DoubleParameter("Mark radius", 0.1, 50.0)
    var markRadius: Double = 3.0,
    @BooleanParameter("Show rectangles")
    var showRectangles: Boolean = false
)

private fun MergeTree.toAtzeTree(w: Double, h: Double = 0.0): Atze.Tree {
    val convertedChildren = children.map {
        val heightDiff = abs(height - it.height)
        it.toAtzeTree(w, heightDiff)
    }
    return Atze.Tree(w, h, height, *convertedChildren.toTypedArray())
}

// Draw the rectangular nodes that have been laid out.
private fun CompositionDrawer.drawSimple(tree: Atze.Tree) {
    for (child in tree.c) {
        drawSimple(child)
    }
    contour(Rectangle(Vector2(tree.x - tree.w / 2, tree.y - tree.h), tree.w, tree.h).contour)
}

// Draw the merge tree.
private fun CompositionDrawer.drawNice(tree: Atze.Tree, markRadius: Double) {
    for (child in tree.c) {
        edge(tree.x, tree.y, child.x, child.y, markRadius)
        drawNice(child, markRadius)
    }
    node(tree.x, tree.y, markRadius)
}

fun CompositionDrawer.tidyTree(mergeTree: MergeTree, tds: TreeDrawSettings) {
    val atzeTree = mergeTree.toAtzeTree(tds.nodeWidth)
    Atze.layout(atzeTree)
    if (tds.showRectangles) {
        drawSimple(atzeTree)
    }
    drawNice(atzeTree, tds.markRadius)
}