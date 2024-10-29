import org.openrndr.color.ColorRGBa
import org.openrndr.math.Vector2
import org.openrndr.panel.elements.Element
import org.openrndr.shape.CompositionDrawer
import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour
import java.util.*

interface MergeTreeLike<T> {
    val height: Double
    val children: List<T>
    val parent: T?
    var id: Int
}

fun <T: MergeTreeLike<T>> T.leaves(): List<T> {
    return if (children.isEmpty())
        listOf(this)
    else children.flatMap { it.leaves() }
}

fun <T: MergeTreeLike<T>> T.nodes(): Iterable<T> = object : Iterable<T> {
    override fun iterator(): Iterator<T> {
        return object: Iterator<T> {
            var current: T? = this@nodes
            val q = mutableListOf<T>()

            override fun hasNext(): Boolean {
                return current != null
            }

            override fun next(): T {
                val temp = current!!
                q.addAll(current!!.children)
                current = q.removeFirstOrNull()
                return temp
            }
        }
    }
}

class MergeTree(
    override val height: Double,
    override val children: MutableList<MergeTree> = mutableListOf(),
    override val parent: MergeTree? = null,
    override var id: Int = -1): MergeTreeLike<MergeTree> {

    val leaves: List<MergeTree> by lazy {
        this.leaves()
    }

    val uuid = UUID.randomUUID()

    override fun toString(): String {
        return "(${height}${children.joinToString { it.toString() }})"
    }

    override fun hashCode(): Int {
        return uuid.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return if (other is MergeTree) {
            uuid == other.uuid
        } else {
            false
        }
    }

    fun copy(): MergeTree {
        return MergeTree(height, children.map { it.copy() }.toMutableList(), this)
    }
}

fun MergeTree.reverse(): MergeTree {
    if (children.isEmpty()) return this.copy()
    return MergeTree(height, children.map { it.reverse() }.reversed().toMutableList(), this)
}

open class EmbeddedMergeTree(var pos: Vector2,
                             var edgeContour: ShapeContour?,
                             var horizontalContour: ShapeContour?,
                             override val children: MutableList<EmbeddedMergeTree> = mutableListOf(),
                             override val parent: EmbeddedMergeTree? = null,
                             override var id: Int = -1) : MergeTreeLike<EmbeddedMergeTree> {
    override val height: Double = pos.y

    val leaves: List<EmbeddedMergeTree> by lazy {
        this.leaves()
    }

    fun setID(currentID: Int): Int{
        id = currentID
        var nextID = currentID + 1

        for (child in children) {
            nextID = child.setID(nextID)
        }
        return nextID
    }

    fun getDeepestLeave(): EmbeddedMergeTree {
        var deepest = leaves.first()

        for (leave in leaves) {
            if (leave.pos.y > deepest.pos.y){
                deepest = leave
            }
        }

        return deepest
    }

    //Color of the root node from the blob decomposition. BLACK = not assigned.
    var blobColor = ColorRGBa.BLACK;

    fun draw(drawer: CompositionDrawer, ds: DrawSettings, globalcs: GlobalColorSettings) {
        drawer.apply {
            for (child in children) {
                stroke = globalcs.edgeColor
                fill = null
                strokeWeight = ds.verticalEdgeWidth
                contour(child.edgeContour!!)
                strokeWeight = ds.horizontalEdgeWidth

                //val blobContour = LineSegment(pos, Vector2(pos.x, -5.0)).contour
                var hContour = child.horizontalContour
                if (hContour != null) {
                    var pos1 = hContour.position(0.0)
                    val pos2 = hContour.position(1.0)
                    if (pos1.x != pos2.x) {
                        if (pos1.x < pos2.x) {
                            pos1 = Vector2(pos1.x - ds.verticalEdgeWidth/2, pos1.y)
                        }
                        else {
                            pos1 = Vector2(pos1.x + ds.verticalEdgeWidth/2, pos1.y)
                        }
                        hContour = LineSegment(pos1, pos2).contour
                    }
                }
                contour(hContour!!)

                child.draw(this, ds, globalcs)
            }
            if (ds.drawNodes)
                node(pos, ds.markRadius)
        }
    }

    fun drawNodes(drawer: CompositionDrawer, markRadius: Double) {
        drawer.apply {
            for (child in children) {
                stroke = ColorRGBa.BLACK
                fill = null
                strokeWeight = markRadius / 3.0
                child.drawNodes(this, markRadius)
            }
            node(pos, markRadius)
        }
    }

    override fun toString(): String {
        return "({${pos}}${children.joinToString { it.toString() }})"
    }
}
