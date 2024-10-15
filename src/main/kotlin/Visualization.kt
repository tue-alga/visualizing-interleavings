import org.openrndr.color.ColorHSVa
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.shadestyles.linearGradient
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.shape.*
import kotlin.math.max
import kotlin.math.min
import org.openrndr.math.mix

class Visualization(val tree1: MergeTree,
                    val tree2: MergeTree,
                    val pos: Vector2,
                    val tes: TreeEmbedSettings = TreeEmbedSettings(),
                    val ds: DrawSettings = DrawSettings(),
                    val globalcs: GlobalColorSettings = GlobalColorSettings(),
                    val tcs: TwoColorSettings = TwoColorSettings(),
                    val gcs: GradientColorSettings = GradientColorSettings(),
                    val createInterleaving: (EmbeddedMergeTree, EmbeddedMergeTree) -> Interleaving<EmbeddedMergeTree>
) {
    lateinit var tree1E: EmbeddedMergeTree
    lateinit var tree2E: EmbeddedMergeTree
    lateinit var interleaving: Interleaving<EmbeddedMergeTree>
    lateinit var composition: Composition
    lateinit var nodeComposition: Composition
    //Blobs sorted from the deepest path to the highest path.
    var tree1Blobs: MutableList<Pair<MutableList<EmbeddedMergeTree>, ColorRGBa>> = mutableListOf();
    var tree2Blobs: MutableList<Pair<MutableList<EmbeddedMergeTree>, ColorRGBa>> = mutableListOf();
    var tree1BlobsTest: MutableList<Pair<MutableList<EmbeddedMergeTree>, Int>> = mutableListOf();
    var tree2BlobsTest: MutableList<Pair<MutableList<EmbeddedMergeTree>, Int>> = mutableListOf();

    //TODO: Find path decomposition and use that to create blobs
    //Path decompositions: List of paths. Path is defined by a leaf and the highest node <leaf, highestnode>
    var tree1PathDecomposition: MutableList<MutableList<EmbeddedMergeTree>> = mutableListOf();
    var tree2PathDecomposition: MutableList<MutableList<EmbeddedMergeTree>> = mutableListOf();

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

        tree1E.setID(0)
        tree2E.setID(0)
        if (interleaving.g.inverseNodeEpsilonMap.keys.first().parent == null){
            interleaving.g.inverseNodeEpsilonMap.keys.first().setID(0)
        }
        if (interleaving.f.inverseNodeEpsilonMap.keys.first().parent == null){
            interleaving.f.inverseNodeEpsilonMap.keys.first().setID(0)
        }

        pathDecomposition(true)
        pathDecomposition(false)

        repositionNodes(true, tree1E)
        repositionNodes(false, tree2E)

        treePairComposition()

        val blue = ColorRGBa.fromHex("#8EBBD9")
        val red = ColorRGBa.fromHex("#F08C8D")
        val green = ColorRGBa.fromHex("#99CF95")
        val purple = ColorRGBa.fromHex("#AC8BD1")

//        val t1Gradient = ColorGrad(gcs.t1c1, gcs.t1c2)
//        val t2Gradient = linearGradient(gcs.t2c1, gcs.t2c2)

        blobComposition(true, tcs.t1c1, tcs.t1c2)
        blobComposition(false, tcs.t2c1, tcs.t2c2)

        blobCompositionTest(true, tcs.t1c1, tcs.t1c2)
        blobCompositionTest(false, tcs.t1c1, tcs.t1c2)

    }

    private fun pathDecomposition(t1: Boolean){
        val tree = if (t1) interleaving.g else interleaving.f

        if (t1) tree1PathDecomposition.clear() else tree2PathDecomposition.clear()

        for (path in tree.pathDecomposition) {
            if (t1){
                tree1PathDecomposition.add(path)
            }
            else {
                tree2PathDecomposition.add(path)
            }
        }
    }

    private fun repositionNodes(t1: Boolean, t: EmbeddedMergeTree){
        if (t.children.isEmpty()) return //Don't reposition leaves

        for (c in t.children) {
            repositionNodes(t1, c)
        }

        val pc = getChildInSamePath(t1, t)

        if (pc != null && t.id != pc.id) {
            t.pos = Vector2(pc.pos.x, t.pos.y)
        }

        for (c in t.children) {
            val contour: ShapeContour? = c.parent?.let {
                edgeContour(t.pos, c.pos)
            }
            c.edgeContour = contour

            val horizontal: ShapeContour? = c.parent?.let {
                horizontalConnector(t.pos, c.pos)
            }
            c.horizontalContour = horizontal

        }
    }

    private fun getChildInSamePath(t1: Boolean, t:  EmbeddedMergeTree): EmbeddedMergeTree? {
        if (t.children.isEmpty()) return null

        val pathID = if (t1) getPathID(t, tree1PathDecomposition) else getPathID(t, tree2PathDecomposition)

        for (c in t.children) {
            val childPathID = if (t1) getPathID(c, tree1PathDecomposition) else getPathID(c, tree2PathDecomposition)
            if (pathID == childPathID) {
                return c
            }
        }
        return null
    }

    fun getPathID(t:  EmbeddedMergeTree, paths: MutableList<MutableList<EmbeddedMergeTree>>): Int {
        for (i in paths.indices) {
            for (node in paths[i]) {
                if (node.id == t.id) return i
            }
        }
        return -1
    }

    fun colorGradiantValue(t1: Boolean, t: Double): ColorRGBa {
        val clampedT = t.coerceIn(0.0, 1.0)

        val startColor = if(t1) gcs.t1c1 else gcs.t2c1;
        val endColor = if(t1) gcs.t1c2 else gcs.t2c2;

        when (gcs.colorInterpolation)
        {
            ColorInterpolationType.RGBLinear -> {
                return startColor.mix(endColor, t)
            }
            ColorInterpolationType.HSVShort -> {
                val hsvStartColor = ColorHSVa.fromRGBa(startColor)
                val hsvEndColor = ColorHSVa.fromRGBa(endColor)
                return hsvStartColor.mix(hsvEndColor, t).toRGBa();
            }
        }


    }

    /** Draw tree1E and tree2E side by side */
    private fun treePairComposition() {
        val tree1C = drawComposition { tree1E.draw(this, ds, globalcs) }
        val tree2C = drawComposition { tree2E.draw(this, ds, globalcs) }
        val tree1NC = drawComposition { tree1E.drawNodes(this, ds.markRadius) }
        val tree2NC = drawComposition { tree2E.drawNodes(this, ds.markRadius) }
        val bounds1 = tree1C.findShapes().map { it.bounds }.bounds
        val bounds2 = tree2C.findShapes().map { it.bounds }.bounds
        val halfGap = ds.markRadius * 20

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

        nodeComposition = drawComposition {
                translate(pos)
                isolated {
                    translate(-bounds1.width / 2 - halfGap, 0.0)
                    tree1EMatrix = model
                    composition(tree1NC)
                }
                isolated {
                    translate(bounds2.width / 2 + halfGap, 0.0)
                    tree2EMatrix = model
                    composition(tree2NC)
                }
        }
    }

    private fun blobCompositionTest(t1: Boolean, color1: ColorRGBa, color2: ColorRGBa) {
        val tree = if(t1) interleaving.f else interleaving.g;

        if (t1) {
            tree1BlobsTest.clear()
            for (i in tree2PathDecomposition.indices) {
                tree1BlobsTest.add(Pair(mutableListOf(), i))
            }
            for (node in tree1E.nodes()){
                val other = tree.nodeMap[node];
                val otherPathID = getPathID(other!!.firstDown, tree2PathDecomposition)

                tree1BlobsTest[otherPathID].first.add(node)
            }

            tree1BlobsTest.sortBy { it.first.first().height }
        }
        else {
            tree2BlobsTest.clear()
            for (i in tree1PathDecomposition.indices) {
                tree2BlobsTest.add(Pair(mutableListOf(), i))
            }
            for (node in tree2E.nodes()){
                val other = tree.nodeMap[node]
                val otherPathID = getPathID(other!!.firstDown, tree1PathDecomposition)

                tree2BlobsTest[otherPathID].first.add(node)
            }

            tree2BlobsTest.sortBy { it.first.first().height }

        }
    }

    /** Construct blob decomposition into tree1Blobs if t1=true and into tree2Blobs if t1=false */
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