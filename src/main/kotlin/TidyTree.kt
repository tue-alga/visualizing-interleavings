import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.math.Vector2
import org.openrndr.shape.*
import kotlin.math.abs

data class TreeEmbedSettings(
    @DoubleParameter("Node width", 0.1, 50.0)
    var nodeWidth: Double = 16.0,
)

private fun MergeTree.toAtzeTree(w: Double, h: Double = 0.0): Atze.Tree {
    val convertedChildren = children.map {
        val heightDiff = abs(height - it.height)
        it.toAtzeTree(w, heightDiff)
    }
    return Atze.Tree(w, h, height - h, *convertedChildren.toTypedArray())
}

private fun tidyEmbedding(tree: Atze.Tree, parent: Pair<EmbeddedMergeTree, Vector2>? = null): EmbeddedMergeTree {
    val pos = Vector2(tree.x, tree.y + tree.h)
    val contour: ShapeContour? = parent?.let {
        edgeContour(it.second, pos)
    }
    val horizontal: ShapeContour? = parent?.let {
        horizontalConnector(it.second, pos)
    }
    val t = EmbeddedMergeTree(pos, parent=parent?.first, edgeContour=contour, horizontalContour=horizontal)
    for (child in tree.c) {
        val embeddedChild = tidyEmbedding(child, t to pos)
        t.children.add(embeddedChild)
    }
    return t
}

fun tidyTree(mergeTree: MergeTree, tds: TreeEmbedSettings): EmbeddedMergeTree {
    val atzeTree = mergeTree.toAtzeTree(tds.nodeWidth)
    Atze.layout(atzeTree)
    return tidyEmbedding(atzeTree)
}