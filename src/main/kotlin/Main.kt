import kotlinx.coroutines.flow.merge
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Drawer
import org.openrndr.draw.isolated
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.math.Vector2
import org.openrndr.shape.contour

class MergeTree(val height: Double, val children: List<MergeTree> = emptyList())

fun Drawer.node(pos: Vector2) {
    stroke = ColorRGBa.BLACK
    fill = ColorRGBa.WHITE
    circle(pos, 3.0)
}

fun Drawer.edge(pos1: Vector2, pos2: Vector2) {
    val control = Vector2(pos2.x, pos1.y)
    val c = contour {
        moveTo(pos1)
        curveTo(control, pos2)
    }
    contour(c)
}

fun Drawer.mergeTree(tree: MergeTree, pos: Vector2 = Vector2.ZERO) {
    translate(pos)

    stroke = ColorRGBa.BLACK
    fill = null
    for ((i, child) in tree.children.withIndex()) {
        val j = i - tree.children.size / 2 + 0.5
        val heightDiff = tree.height - child.height
        val childPos = Vector2(j * heightDiff, child.height)
        edge(Vector2(0.0, tree.height), childPos)
        isolated {
            translate(childPos.x, 0.0)
            mergeTree(child)
        }
    }
    node(Vector2(0.0, tree.height))
}

fun findSubtrees(s: String): List<String> = buildList {
    var substring = ""
    var depth = 0
    for (i in s.indices) {
        val c = s[i]
        if (c == '(') {
            depth += 1
        } else if (c == ')') {
            depth -= 1
        }
        if (depth >= 1){
            substring += c
        }
        if (depth == 0) {
            add(substring)
            substring = ""
        }
    }
}

fun parseTree(s: String): MergeTree {
    assert(s.first() == '(' && s.last() == ')')
    val i = s.withIndex().firstOrNull() { it.index > 0 && it.value == '(' }?.index ?: s.length
    val h = s.substring(1, i)

    val subtrees = if (i == s.length) emptyList() else findSubtrees(s.substring(i, s.length)).map { parseTree(it) }
    return MergeTree(h.toDouble(), subtrees)
}

fun main() = application {
    configure {
        width = 800
        height = 800
    }
    program {
        // Construct a merge tree manually
//        val c1 = MergeTree(40.0)
//        val c2 = MergeTree(60.0)
//        val tree = MergeTree(100.0, listOf(c1, c2))

        // Construct a merge tree from a String
        val tree = parseTree("(100(40(20)(10))(60))")
        extend(Camera()) {
            enableRotation = true;
        }
        extend {
            drawer.apply {
                clear(ColorRGBa.WHITE)
                translate(0.0, height * 1.0)
                scale(1.0, -1.0)
                isolated {
                    mergeTree(tree, drawer.bounds.center)
                }
            }
        }
    }
}