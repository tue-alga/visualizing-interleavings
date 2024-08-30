import org.openrndr.color.ColorRGBa
import org.openrndr.math.Vector2
import org.openrndr.shape.CompositionDrawer
import org.openrndr.shape.ShapeContour
import java.util.*

interface MergeTreeLike<T> {
    val height: Double
    val children: List<T>
    val parent: T?
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
    override val parent: MergeTree? = null): MergeTreeLike<MergeTree> {

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

class EmbeddedMergeTree(val pos: Vector2,
                        val edgeContour: ShapeContour?,
                        override val children: MutableList<EmbeddedMergeTree> = mutableListOf(),
                        override val parent: EmbeddedMergeTree? = null) : MergeTreeLike<EmbeddedMergeTree> {
    override val height: Double = pos.y

    val leaves: List<EmbeddedMergeTree> by lazy {
        this.leaves()
    }

    //Color of the root node from the blob decomposition. BLACK = not assigned.
    var blobColor = ColorRGBa.BLACK;

    fun draw(drawer: CompositionDrawer, markRadius: Double) {
        drawer.apply {
            for (child in children) {
                stroke = ColorRGBa.BLACK
                fill = null
                strokeWeight = markRadius / 3.0
                contour(child.edgeContour!!)
                child.draw(this, markRadius)
            }
            node(pos, markRadius)
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
