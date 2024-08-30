import org.openrndr.color.ColorRGBa
import org.openrndr.extra.color.presets.ORANGE
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.shape.*
import kotlin.math.max
import kotlin.math.min

class Visualization(val tree1: MergeTree,
                    val tree2: MergeTree,
                    val pos: Vector2,
                    val tes: TreeEmbedSettings = TreeEmbedSettings(),
                    val ds: DrawSettings = DrawSettings(),
                    val createInterleaving: (EmbeddedMergeTree, EmbeddedMergeTree) -> Interleaving<EmbeddedMergeTree>
) {
    lateinit var tree1E: EmbeddedMergeTree
    lateinit var tree2E: EmbeddedMergeTree
    lateinit var interleaving: Interleaving<EmbeddedMergeTree>
    lateinit var composition: Composition
    //Blobs sorted from deepest path to highest path.
    var tree1Blobs: MutableList<Pair<MutableList<EmbeddedMergeTree>, ColorRGBa>> = mutableListOf();
    var tree2Blobs: MutableList<Pair<MutableList<EmbeddedMergeTree>, ColorRGBa>> = mutableListOf();
    private lateinit var tree1EMatrix: Matrix44
    private lateinit var tree2EMatrix: Matrix44
    val compBounds: Rectangle get() = composition.findShapes().map { it.effectiveShape.bounds }.bounds
    val bbox: Rectangle get() = compBounds.offsetEdges(min(compBounds.width, compBounds.height) * 0.1)

    init {
        compute()
    }

    fun compute() {
        tree1E = tidyTree(tree1, tes)
        tree2E = tidyTree(tree2, tes)

        interleaving = createInterleaving(tree1E, tree2E)

        treePairComposition()

        val blue = ColorRGBa.fromHex("#8EBBD9")
        val red = ColorRGBa.fromHex("#F08C8D")
        val green = ColorRGBa.fromHex("#99CF95")
        val purple = ColorRGBa.fromHex("#AC8BD1")

        blobComposition(true, green, purple)
        blobComposition(false, blue, red)
    }

    /** Draw tree1E and tree2E side by side */
    private fun treePairComposition() {
        val tree1C = drawComposition { tree1E.draw(this, ds.markRadius) }
        val tree2C = drawComposition { tree2E.draw(this, ds.markRadius) }
        val bounds1 = tree1C.findShapes().map { it.bounds }.bounds
        val bounds2 = tree2C.findShapes().map { it.bounds }.bounds
        val halfGap = ds.markRadius * 4

        composition = drawComposition {
            translate(pos)
            isolated {
                translate(-bounds1.width / 2 - halfGap, 0.0)
                tree1EMatrix = model
                composition(tree1C)
            }
            isolated {
                translate(bounds2.width / 2 + halfGap, 0.0)
                tree2EMatrix = model
                composition(tree2C)
            }
        }
    }

    private fun blobComposition(t1: Boolean, color1: ColorRGBa, color2: ColorRGBa){
        val tree = if(t1) interleaving.f else interleaving.g;

        if (t1) tree1Blobs.clear() else tree2Blobs.clear()

        var groupColor = color1;

        for (group in tree.leafGroups) {
            var colorDetermined = false;
            val path = mutableListOf<EmbeddedMergeTree>()

            for (leaf in group){
                var node: EmbeddedMergeTree? = leaf;
                //groupColor = currentColor;
                path.add(leaf)

                while (node != null) {
                    val nodeColor = node.blobColor;
                    if (nodeColor != ColorRGBa.BLACK) {
                        //if we encounter a node that already has an assigned color
                        if (!colorDetermined) {
                            groupColor = if (nodeColor == color1) color2 else color1;
                            colorDetermined = true;
                        }
                        break;
                    }
                    path.add(node)
                    node = node.parent;
                }
            }

            val distinctPath = path.distinct();
            path.clear()
            path.addAll(distinctPath)

            if (t1)
                tree1Blobs.add(Pair(path, groupColor))
            else tree2Blobs.add(Pair(path, groupColor))

            for (n in path) {
                n.blobColor = groupColor
            }
            groupColor = if (groupColor == color1) color2 else color1;
        }
    }

    /** Transforms a point in 'world space' to the 'model space' of tree1 */
    fun toTree1Local(posWorldSpace: Vector2): Vector2 {
        return tree1EMatrix.inversed * posWorldSpace
    }

    /** Transforms a point in 'world space' to the 'model space' of tree1 */
    fun fromTree1Local(posLocalT1: Vector2): Vector2 {
        return tree1EMatrix * posLocalT1
    }

    /** Transforms a contour in 'world space' to the 'model space' of tree1 */
    fun fromTree1Local(contour: ShapeContour): ShapeContour {
        return contour.transform(tree1EMatrix);
    }

    /** Transforms a point in 'world space' to the 'model space' of tree2 */
    fun toTree2Local(posWorldSpace: Vector2): Vector2 {
        return tree2EMatrix.inversed * posWorldSpace
    }

    /** Transforms a point in 'world space' to the 'model space' of tree2 */
    fun fromTree2Local(posLocalT2: Vector2): Vector2 {
        return tree2EMatrix * posLocalT2
    }

    /** Transforms a contour in 'world space' to the 'model space' of tree2 */
    fun fromTree2Local(contour: ShapeContour): ShapeContour {
        return contour.transform(tree2EMatrix);
    }

    private fun closestPositionTi(tree: EmbeddedMergeTree, pos: Vector2, radius: Double): TreePosition<EmbeddedMergeTree>? {
        val closestNode = tree.nodes().map {
            val c = (it.edgeContour ?: LineSegment(it.pos, it.pos - Vector2(0.0, 1000.0)).contour)
            val nearest = c.nearest(pos).position
            it to (pos - nearest).squaredLength
        }.filter { it.second < radius * radius }.minByOrNull { it.second }?.first

        if (closestNode == null) return null

        val diff = closestNode.height - pos.y
        return TreePosition(closestNode, max(diff, 0.0))
    }

    fun closestPositionT1(posT1: Vector2, radius: Double): TreePosition<EmbeddedMergeTree>? {
        return closestPositionTi(tree1E, posT1, radius)
    }

    fun closestPositionT2(posT2: Vector2, radius: Double): TreePosition<EmbeddedMergeTree>? {
        return closestPositionTi(tree2E, posT2, radius)
    }
}