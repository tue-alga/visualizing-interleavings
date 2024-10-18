import org.openrndr.KEY_SPACEBAR
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.isolated
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.parameters.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.shape.*
import org.openrndr.svg.saveToFile
import java.io.File
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

fun treePositionToPoint(tp: TreePosition<EmbeddedMergeTree>): Vector2? {
    val c = tp.firstDown.edgeContour ?: LineSegment(tp.firstDown.pos, tp.firstDown.pos - Vector2(0.0, 1000.0)).contour
    if (c.segments.size == 1 && c.segments.first().control.size == 1) {
        val b = tp.height
        val seg = c.segments.first()
        val p0 = seg.start
        val p1 = seg.control[0]
        val p2 = seg.end
        val a = p0.y - 2 * p1.y + p2.y
        val t = (p0.y - p1.y - sqrt(b * a + p1.y * p1.y - p0.y * p2.y)) / a
        if (t.isNaN()) return null
        return seg.position(abs(t))
    } else {
        return Vector2(c.segments[0].start.x, tp.height)
    }
}

data class DrawSettings(
    @DoubleParameter("Mark radius", 0.1, 10.0)
    var markRadius: Double = 1.0,

    @BooleanParameter("Draw Nodes")
    var drawNodes: Boolean = false,

    @DoubleParameter("Vertical Edge Width", 0.1, 5.0)
    var verticalEdgeWidth: Double = 2.5,

    @DoubleParameter("Horizontal Edge Width", 0.1, 5.0)
    var horizontalEdgeWidth: Double = verticalEdgeWidth,

    @DoubleParameter("Path Area Scale", 0.0, 3.0)
    var pathAreaPatchScale: Double = 1.5,

    @DoubleParameter("Patch Area Stroke Scale", 0.0, 0.5)
    var patchStrokeScale: Double = 0.15,

    @DoubleParameter("Blob radius", 0.1, 10.0)
    var blobRadius: Double = 4.0,

    )

data class GlobalColorSettings(
    @BooleanParameter("Enable Gradient")
    var enableGradient: Boolean = false,

    @ColorParameter("EdgeColor", order = 0)
    var edgeColor: ColorRGBa = ColorRGBa.WHITE
)

data class ThreeColorSettings(
    //@ColorParameter
    //pathColor parameter here

    //Tree 1
    @ColorParameter("Tree1 color1 hexcode")
    var t1c1: ColorRGBa = ColorRGBa.fromHex("#C5037D"), //purple

    @ColorParameter("Tree1 color2 hexcode")
    var t1c2: ColorRGBa = ColorRGBa.fromHex("#E96222"), //orange

    @ColorParameter("Tree1 color3 hexcode")
    var t1c3: ColorRGBa = ColorRGBa.fromHex("#FCC60E"), //yellow

    //Tree2
    @ColorParameter("Tree2 color1 hexcode")
    var t2c1: ColorRGBa = ColorRGBa.fromHex("#454F96"), //dark-blue

    @ColorParameter("Tree2 color2 hexcode")
    var t2c2: ColorRGBa = ColorRGBa.fromHex("#0695BA"), //light-blue

    @ColorParameter("Tree2 color3 hexcode")
    var t2c3: ColorRGBa = ColorRGBa.fromHex("#8DBB25") //green

)
//val blue = ColorRGBa.fromHex("#8EBBD9")
//val red = ColorRGBa.fromHex("#F08C8D")
//val green = ColorRGBa.fromHex("#99CF95")
//val purple = ColorRGBa.fromHex("#AC8BD1")

enum class ColorInterpolationType {
    RGBLinear, HSVShort
}

data class GradientColorSettings(
    @OptionParameter("Interpolation type")
    var colorInterpolation: ColorInterpolationType = ColorInterpolationType.RGBLinear,

    @ColorParameter("Tree1 Gradient Start")
    var t1c1: ColorRGBa = ColorRGBa.fromHex("#f01d0e"), //red

    @ColorParameter("Tree1 Gradient End")
    var t1c2: ColorRGBa = ColorRGBa.fromHex("#e1ff69"), //yellow

    @ColorParameter("Tree2 Gradient Start")
    var t2c1: ColorRGBa = ColorRGBa.fromHex("#61faff"), //light blue

    @ColorParameter("Tree2 Gradient End")
    var t2c2: ColorRGBa = ColorRGBa.fromHex("#0e0ef0") //dark blue
)

fun example1(pos: Vector2): Visualization {
    val tree1 = parseTree(
        "(0" +
                "(11(30)(40(50)(50)))" +
                "(20.1(25)(30))" +
                "(15(22)(32(40)(37)(45)))" +
                ")"
    )
    val tree2 = parseTree(
        "(0" +
                "(10(40)(30(35)(38(51)(51))))" +
                "(20)" +
                "(11(15)(20))" +
                "(15(31)(32(45)(50)))" +
                ")"
    )

    return Visualization(tree1, tree2, pos) { tree1E, tree2E ->
        monotoneInterleaving(tree1E, tree2E)
    }
}

fun example2(pos: Vector2): Visualization {
    val tree1 = parseTree("(0(10)(20))")
    val tree2 = parseTree("(0(8)(20))")

    return Visualization(tree1, tree2, pos) { tree1E, tree2E ->
        monotoneInterleaving(tree1E, tree2E)
    }
}

fun example3(pos: Vector2): Visualization {
    val tree1 = parseTree("(0(40)(10(25)(35)))")
    val tree2 = parseTree("(0(10(35)(25))(40))")

    return Visualization(tree1, tree2, pos) { tree1E, tree2E ->
        monotoneInterleaving(tree1E, tree2E)
    }
}

fun example4(pos: Vector2): Visualization {
    val tree1 = parseTree("(0" +
            "(25(35)(40(50)(55(70)(65)(60)(70))(50)))" +
            "(5(15(25)(20))(10)(30(40)(35)))" +
            "(10(15)(30(50)(55))(25))" +
            "(15(35)(50(55)(60)(65))(20))" +
            "(5(30)(35(45(65)(55)(60))(40)(40))(15))" +
            "(5(15)(25(35)(30)))" +
            "(35(55)(40)(45(60(65)(65)(70))(55)(65)))" +
            "(20(35)(30)(35)(40(55)(50)(45))(30(35)(35)))" +
            ")"
    )
    val tree2 = parseTree("(0" +
            "(25(35)(40(50)(55(70)(65)(60)(70))(50)))" +
            "(5(15(25)(20))(10)(30(40)(35)))" +
            "(10(15)(30(50)(55))(25))" +
            "(15(35)(50(55)(60)(65))(20))" +
            "(5(30)(35(45(65)(55)(60))(40)(40))(15))" +
            "(5(15)(25(35)(30)))" +
            "(35(55)(40)(45(60(65)(65)(70))(55)(65)))" +
            "(20(35)(30)(35)(40(55)(50)(45))(30(35)(35)))" +
            ")"
    )

    return Visualization(tree1, tree2, pos) { tree1E, tree2E ->
        monotoneInterleaving(tree1E, tree2E)
    }
}

fun main() = application {
    configure {
        width = 1600
        height = 900
        title = "Visualizing interleavings"
    }
    program {
        val camera = Camera()

        var blobsEnabled = true

        val visualization = example4(drawer.bounds.center)

        val viewSettings = object {
            @ActionParameter("Fit to screen")
            fun fitToScreen() {
                camera.view = Matrix44.fit(visualization.bbox, drawer.bounds)
            }

            @ActionParameter("Toggle Blobs")
            fun toggleBlobs() {
                blobsEnabled = !blobsEnabled;
            }

            @ActionParameter("Compute monotone")
            fun computeMonotone() {
                monotoneInterleaving(visualization.tree1, visualization.tree2)
            }
        }

        val exportSettings = object {
            @TextParameter("File name")
            var svgFileName: String = "output"

            @ActionParameter("Export to SVG")
            fun exportToSVG() {
                visualization.composition.saveToFile(File("${svgFileName}.svg"))
                visualization.composition.documentStyle.viewBox
            }
        }

        keyboard.keyDown.listen {
            if (it.key == KEY_SPACEBAR) {
                viewSettings.fitToScreen()
            }
        }

        val gui = GUI()
        gui.add(visualization.tes, "Tree embedding")
        gui.add(visualization.ds, "Drawing")
        gui.add(visualization.globalcs, "Global Color Settings")
        gui.add(visualization.tcs, "Three Color Settings")
        gui.add(visualization.gcs, "Gradient Color Settings")

        gui.add(viewSettings, "View")
        gui.add(exportSettings, "Export")

        gui.onChange { name, value ->
            // name is the name of the variable that changed
            when (name) {
                "drawNodes", "nodeWidth", "markRadius", "verticalEdgeWidth", "horizontalEdgeWidth", "pathAreaPatchScale", "areaPatchStrokeScale",
                "edgeColor", "enableGradient", "colorInterpolation", "t1c1", "t1c2", "t1c3", "t2c1", "t2c2", "t2c3"-> {
                    visualization.compute()
                }
            }
        }

        var mouseTree1Position: TreePosition<EmbeddedMergeTree>? = null
        var mouseTree2Position: TreePosition<EmbeddedMergeTree>? = null

        mouse.moved.listen { mouseEvent ->
            val pos = camera.view.inversed * mouseEvent.position
            val posT1 = visualization.toTree1Local(pos)
            mouseTree1Position = visualization.closestPositionT1(posT1, 3.0)
            val posT2 = visualization.toTree2Local(pos)
            mouseTree2Position = visualization.closestPositionT2(posT2, 3.0)
        }

        fun drawMatching(one: TreePosition<EmbeddedMergeTree>, t1ToT2: Boolean) {
            drawer.apply {
                treePositionToPoint(one)?.let { onePoint ->
                    val other = if (t1ToT2) visualization.interleaving.f[one] else visualization.interleaving.g[one]
                    treePositionToPoint(other)?.let { otherPoint ->
                        val onePos =
                            if (t1ToT2) visualization.fromTree1Local(onePoint) else visualization.fromTree2Local(
                                onePoint
                            )
                        val otherPos =
                            if (t1ToT2) visualization.fromTree2Local(otherPoint) else visualization.fromTree1Local(
                                otherPoint
                            )

                        strokeWeight = visualization.ds.markRadius / 3
                        stroke = ColorRGBa.BLUE
                        fill = null
                        lineSegment(onePos, otherPos)

                        strokeWeight = visualization.ds.markRadius / 3
                        stroke = null
                        fill = ColorRGBa.BLUE
                        circle(onePos, 1.0)
                        circle(otherPos, 1.0)
                    }
                }
            }
        }

        fun drawInverseMatching(tree: EmbeddedMergeTree, t1ToT2: Boolean){
            drawer.apply {
                val treeMapping = if(t1ToT2) visualization.interleaving.g else visualization.interleaving.f

                for (node in tree.nodes()) {
                    val pos1 = if(t1ToT2) visualization.fromTree1Local(node.pos) else visualization.fromTree2Local(node.pos)
                    if (treeMapping.inverseNodeEpsilonMap.contains(node)) {
                        //println("yeet")
                        for (treePos in treeMapping.inverseNodeEpsilonMap[node]!!) {
                            val point = treePositionToPoint(treePos)
                            if (point != null) {
                                val pos2 =  if(t1ToT2) visualization.fromTree2Local(treePositionToPoint(treePos)!!) else visualization.fromTree1Local(treePositionToPoint(treePos)!!)
                                strokeWeight = visualization.ds.markRadius / 3
                                stroke = ColorRGBa.BLUE
                                fill = null
                                lineSegment(pos1, pos2)
                            }
                        }
                        //return;
                    }
//                    if (treeMapping.pathCharges.contains(node)) {
//                        if (treeMapping.pathCharges[node]!! > 1) {
//
//                            val pos = if(t1ToT2) visualization.fromTree1Local(node.pos) else visualization.fromTree2Local(node.pos)
//                            strokeWeight = visualization.ds.markRadius / 3
//                            stroke = null
//                            fill = ColorRGBa.BLUE
//                            circle(pos, 2.0)
//                        }
//
//                    }
                }

                for (path in treeMapping.pathDecomposition) {
                    for (node in treeMapping.pathDecomposition[2]) {
                            val pos =
                                if (t1ToT2) visualization.fromTree1Local(node.pos) else visualization.fromTree2Local(
                                    node.pos
                                )
                            strokeWeight = visualization.ds.markRadius / 3
                            stroke = null
                            fill = ColorRGBa.BLUE
                            circle(pos, 2.0)
                    }
                }
            }
        }

        fun deepestNodeInBlob(blob: Pair<MutableList<EmbeddedMergeTree>, Int>): EmbeddedMergeTree? {
            var deepest:EmbeddedMergeTree? = null

            for (node in blob.first){
                if (deepest == null){
                    deepest = node
                    continue
                }
                if (node.height > deepest.height){
                    deepest = node
                }
            }
            return deepest
        }

        fun highestNodeInBlob(blob: Pair<MutableList<EmbeddedMergeTree>, Int>): EmbeddedMergeTree? {
            var highest:EmbeddedMergeTree? = null

            for (node in blob.first){
                if (highest == null){
                    highest = node
                    continue
                }
                if (node.height < highest.height){
                    highest = node
                }
            }
            return highest
        }

        fun drawBlob(tree: EmbeddedMergeTree, blob: Pair<MutableList<EmbeddedMergeTree>, Int>, gradientInterval: Double, numberOfBlobs: Int) {
            val tree1 = (tree == visualization.tree1E)
            val deepestNodeInBlob = deepestNodeInBlob(blob);
            val highestNodeInBlob = highestNodeInBlob(blob)
            val deepestPos = deepestNodeInBlob!!.pos
            val highestBlobPos = if(highestNodeInBlob!!.parent != null) highestNodeInBlob.parent!!.pos else highestNodeInBlob.pos

            drawer.apply {
                strokeWeight = visualization.ds.markRadius / 3
                stroke = null

                if (visualization.globalcs.enableGradient)
                    visualization.colorGradiantValue(tree1, gradientInterval)
                else
                    fill = ColorRGBa.BLACK

                for (node in blob.first) {
                    //Draw blob along edge
                    stroke = if (visualization.globalcs.enableGradient)
                        visualization.colorGradiantValue(tree1, gradientInterval)
                    else
                        visualization.colorThreeValues(tree1, numberOfBlobs)[blob.second]

                    fill = null
                    strokeWeight = visualization.ds.blobRadius * 2
                    if (node.edgeContour != null) {
                        val pos = node.edgeContour!!.position(1.0)

                        val midX = (pos.x + deepestPos.x)/2
                        val width = abs(pos.x - deepestPos.x)

                        strokeWeight = width + (visualization.ds.blobRadius * 2)

                        val blobContour = LineSegment(Vector2(midX, deepestPos.y), Vector2(midX, highestBlobPos.y)).contour

                        if (tree1)
                            contour(visualization.fromTree1Local(blobContour))
                        else contour(visualization.fromTree2Local(blobContour))
                    }

                    //Draw blob around sub path
                    for (child in node.children) {
                        if (child.blobColor != node.blobColor) {
                            val lowestPathPoint =
                                if (tree1) visualization.interleaving.f.nodeMap[child] else visualization.interleaving.g.nodeMap[child]
                            if (lowestPathPoint != null) {
                                val delta = child.height - lowestPathPoint.height

                                var heightDelta = 0.0;
                                //If lowestPathPoint.firstUp is null, it's > the root node, meaning the entire path should be part of the blob.
                                if (lowestPathPoint.firstUp != null) {
                                    heightDelta = child.height - (lowestPathPoint.firstUp!!.height + delta)
                                }

                                val treePos = TreePosition(child, heightDelta)
                                val edge = child.edgeContour;
                                val point = treePositionToPoint(treePos);

                                //if point is null, mapping path no part of the path to the child should be in the blob.
                                if (point != null) {
                                    val curveOffset = edge!!.on(point, 0.2);
                                    val subContour = edge.sub(0.0, curveOffset!!)
                                    val pos = subContour.position(1.0)

                                    val midX = (pos.x + deepestPos.x)/2
                                    val width = abs(pos.x - deepestPos.x)

                                    strokeWeight = width + (visualization.ds.blobRadius * 2)

                                    val contour = LineSegment(Vector2(midX, pos.y), Vector2(midX, highestBlobPos.y)).contour
                                    if (tree1)
                                        contour(visualization.fromTree1Local(contour))
                                    else contour(visualization.fromTree2Local(contour))
                                }
                            }
                        }
                    }

                    if (node.parent == null){
                        val delta = visualization.interleaving.delta
                        val leftLeave = if (tree1) visualization.tree1E.leaves.first() else visualization.tree2E.leaves.first()
                        val rightLeave = if (tree1) visualization.tree1E.leaves.last() else visualization.tree2E.leaves.last()
                        val width = abs(leftLeave.pos.x - rightLeave.pos.x)
                        strokeWeight = width + (visualization.ds.blobRadius * 2)

                        val  midX = (leftLeave.pos.x + rightLeave.pos.x) / 2
                        val contour = LineSegment(Vector2(midX, node.pos.y - visualization.ds.blobRadius), Vector2(midX, node.pos.y + delta)).contour
                        if (tree1)
                            contour(visualization.fromTree1Local(contour))
                        else contour(visualization.fromTree2Local(contour))
                    }

                }
            }
        }

        fun alternatingSpacedValues(x: Int): List<Double> {
            // Calculate x evenly spaced values between 0 and 1
            val evenlySpaced = List(x) { it.toDouble() / (x - 1) }

            val result = mutableListOf<Double>()

            for (i in 0 until x) {
                if (i % 2 == 0) {
                    // Pick from the start for even indices
                    result.add(evenlySpaced[i / 2])
                } else {
                    // Pick from the end for odd indices
                    result.add(evenlySpaced[x - 1 - i / 2])
                }
            }

            return result
        }

        fun drawBlobs() {
            if (!blobsEnabled) return;

            //Everything with blobs is drawn in reversed order so that higher up blobs will be drawn on top of lower blobs
            var values = alternatingSpacedValues(visualization.tree1BlobsTest.size).reversed()

            var count: Int = 0;
            for (blob in visualization.tree1BlobsTest.reversed()) {
                drawBlob(visualization.tree1E, blob, values[count], visualization.tree1BlobsTest.size)
                count+=1
            }

            values = alternatingSpacedValues(visualization.tree2BlobsTest.size).reversed()
            count = 0
            //Draw blobs of tree2 (reversed to draw large blobs on top of smaller blobs)
            for (blob in visualization.tree2BlobsTest.reversed()) {
                drawBlob(visualization.tree2E, blob, values[count], visualization.tree2BlobsTest.size)
                count+=1
            }
        }

        fun drawBlobPath(tree: EmbeddedMergeTree, blob: Pair<MutableList<EmbeddedMergeTree>, Int>, gradientInterval: Double, numberOfBlobs: Int) {
            val tree1 = (tree == visualization.tree1E)

            drawer.apply {
                fill = null
                strokeWeight = visualization.ds.verticalEdgeWidth * 0.4

                stroke = if (visualization.globalcs.enableGradient)
                    visualization.colorGradiantValue(tree1, gradientInterval)
                else
                    visualization.colorThreeValues(tree1, numberOfBlobs)[blob.second]

                //fill = blob.second

                val currentNode =
                    blob.first.maxByOrNull { it.height } //This is the deepest node in the blob (path is defined by that node.
                val lowestPathPoint =
                    if (tree1) visualization.interleaving.f.nodeMap[currentNode] else visualization.interleaving.g.nodeMap[currentNode]

                if (lowestPathPoint == null) return //return if we don't hit the other tree.

                //Draw the lowest sub edge delta up from the leaf of the path
                val edge = lowestPathPoint.firstDown.edgeContour;
                val curveOffset = edge!!.on(treePositionToPoint(lowestPathPoint)!!, .5);
                val subContour = edge.sub(0.0, curveOffset!!)
                if (tree1)
                    contour(visualization.fromTree2Local(subContour))
                else contour(visualization.fromTree1Local(subContour))

                //draw rest of the path till the root node.
                var pathParent: EmbeddedMergeTree? = lowestPathPoint.firstUp;
                while (pathParent != null) {

                    if (pathParent.edgeContour != null && pathParent.pos.x == lowestPathPoint.firstDown.pos.x) {
                        if (tree1)
                            contour(visualization.fromTree2Local(pathParent.edgeContour!!))
                        else contour(visualization.fromTree1Local(pathParent.edgeContour!!))
                    }
                    pathParent = pathParent.parent;
                }
            }
        }

        fun drawPathSquares(t1: Boolean, path: MutableList<EmbeddedMergeTree>, gradientInterval: Double, color: ColorRGBa) {
            drawer.apply {

                fill = if (visualization.globalcs.enableGradient)
                    visualization.colorGradiantValue(!t1, gradientInterval)
                else
                    color

                strokeWeight = visualization.ds.verticalEdgeWidth * visualization.ds.patchStrokeScale
                stroke = visualization.globalcs.edgeColor

                val parent = path.last().parent
                val posY = parent?.pos?.y ?: (path.last().pos.y - visualization.interleaving.delta - visualization.ds.blobRadius)
                var pos = Vector2(path.last().pos.x, posY)

                pos = if (t1) visualization.fromTree1Local(pos) else visualization.fromTree2Local(pos)

                val rectWidth = visualization.ds.verticalEdgeWidth * visualization.ds.pathAreaPatchScale
                pos -= rectWidth / 2
                rectangle(pos, rectWidth)

            }
        }

        fun drawBlobPaths() {
            if (!blobsEnabled) return;

            val t1values = alternatingSpacedValues(visualization.tree1BlobsTest.size)
            val t2values = alternatingSpacedValues(visualization.tree2BlobsTest.size)

            var count: Int = 0;

            //Draw mapping of blob in the first tree onto the second tree
            for (blob in visualization.tree1BlobsTest) {
                drawPathSquares(false, visualization.tree2PathDecomposition[blob.second], t1values[blob.second], visualization.colorThreeValues(true, visualization.tree1BlobsTest.size)[blob.second])
                drawBlobPath(visualization.tree1E, blob, t1values[blob.second], visualization.tree1BlobsTest.size)
                count += 1
            }
//
//            count = 0
//            //Draw mapping of blob in the second tree onto the second tree
            for (blob in visualization.tree2BlobsTest) {
                drawPathSquares(true, visualization.tree1PathDecomposition[blob.second], t2values[blob.second], visualization.colorThreeValues(false, visualization.tree2BlobsTest.size)[blob.second])
                drawBlobPath(visualization.tree2E, blob, t2values[blob.second], visualization.tree2BlobsTest.size)
                count+=1
            }

            //Draw Rays from root
            drawer.apply {
                stroke = ColorRGBa.BLACK
                fill = null
                strokeWeight = visualization.ds.verticalEdgeWidth*0.4

                //Set path Color
                val pathID1 = visualization.tree1BlobsTest.first().second
                stroke = if (visualization.globalcs.enableGradient) visualization.colorGradiantValue(false, t2values[pathID1]) // t2values[visualization.tree2Blobs.size-1])
                else visualization.colorThreeValues(false, visualization.tree2BlobsTest.size)[pathID1]

                //Draw Contour
                val pos1  = visualization.tree1E.pos
                val contour1 = LineSegment(pos1, Vector2(pos1.x, pos1.y - visualization.interleaving.delta - visualization.ds.blobRadius)).contour
                contour(visualization.fromTree1Local(contour1))

                //Set path Color
                val pathID2 = visualization.tree2BlobsTest.first().second
                stroke = if (visualization.globalcs.enableGradient) visualization.colorGradiantValue(true, t1values[pathID2])// t1values[visualization.tree1Blobs.size-1])
                else visualization.colorThreeValues(true, visualization.tree1BlobsTest.size)[pathID2]

                //Draw Contour
                val pos2  = visualization.tree2E.pos
                val contour2 = LineSegment(pos2, Vector2(pos2.x, pos2.y - visualization.interleaving.delta - visualization.ds.blobRadius)).contour
                contour(visualization.fromTree2Local(contour2))

                //Draw nodes of the trees on top of the path decomposition
                if(visualization.ds.drawNodes)
                    composition(visualization.nodeComposition)
            }
        }

        viewSettings.fitToScreen()

        // Press F11 to toggle the GUI
        extend(gui) {
            persistState = false
        }
        extend(camera) {
            enableRotation = false
        }
        extend {
            drawer.apply {
                clear(ColorRGBa.WHITE)


                drawBlobs();

                // Draw ray upward from roots
                stroke = visualization.globalcs.edgeColor
                val rootT1 = visualization.fromTree1Local(visualization.tree1E.pos)
                strokeWeight = visualization.ds.verticalEdgeWidth
                lineSegment(rootT1, Vector2(rootT1.x, (camera.view.inversed * Vector2(0.0, 0.01)).y))
                val rootT2 = visualization.fromTree2Local(visualization.tree2E.pos)
                lineSegment(rootT2, Vector2(rootT2.x, (camera.view.inversed * Vector2(0.0, 0.01)).y))

                //Draw tree
                composition(visualization.composition)

                drawBlobPaths();

                //drawInverseMatching(visualization.tree2E, false)
                //drawInverseMatching(visualization.tree1E, true)

                mouseTree1Position?.let {
                    drawMatching(it, true)
                }
                mouseTree2Position?.let {
                    drawMatching(it, false)
                }


                isolated {
                    view *= camera.view.inversed

                    fill = ColorRGBa.BLACK
                    stroke = ColorRGBa.BLACK
                    val h = visualization.toTree1Local(camera.view.inversed * mouse.position).y
                    val hFormatted = String.format(Locale.US, "%.2f", h)
                    text("Height: $hFormatted", drawer.width - 100.0, drawer.height - 10.0)
                }
            }
        }
    }
}