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
    var markRadius: Double = 3.0,

    @BooleanParameter("Draw Nodes")
    var drawNodes: Boolean = false,

    @DoubleParameter("Vertical Edge Width", 0.1, 5.0)
    var verticalEdgeWidth: Double = 2.5,

    @DoubleParameter("Horizontal Edge Width", 0.1, 5.0)
    var horizontalEdgeWidth: Double = verticalEdgeWidth/2,

    @DoubleParameter("Blob radius", 0.1, 10.0)
    var blobRadius: Double = 4.0,

    @ColorParameter("EdgeColor", order = 0)
    var edgeColor: ColorRGBa = ColorRGBa.WHITE
)

data class GlobalColorSettings(
    @BooleanParameter("Enable Gradient")
    var enableGradient: Boolean = false
)

data class TwoColorSettings(
    //@ColorParameter
    //pathColor parameter here

    @ColorParameter("Tree1 color1 hexcode")
    var t1c1: ColorRGBa = ColorRGBa.fromHex("#99CF95"), //green

    @ColorParameter("Tree1 color2 hexcode")
    var t1c2: ColorRGBa = ColorRGBa.fromHex("#AC8BD1"), //purple

    @ColorParameter("Tree2 color1 hexcode")
    var t2c1: ColorRGBa = ColorRGBa.fromHex("#8EBBD9"), //blue

    @ColorParameter("Tree2 color2 hexcode")
    var t2c2: ColorRGBa = ColorRGBa.fromHex("#F08C8D") //red

)
//val blue = ColorRGBa.fromHex("#8EBBD9")
//val red = ColorRGBa.fromHex("#F08C8D")
//val green = ColorRGBa.fromHex("#99CF95")
//val purple = ColorRGBa.fromHex("#AC8BD1")

data class GradientColorSettings(
    @ColorParameter("Tree1 Gradient Start")
    var t1c1: ColorRGBa = ColorRGBa.fromHex("#99CF95"), //green

    @ColorParameter("Tree1 Gradient End")
    var t1c2: ColorRGBa = ColorRGBa.fromHex("#AC8BD1"), //purple

    @ColorParameter("Tree2 Gradient Start")
    var t2c1: ColorRGBa = ColorRGBa.fromHex("#8EBBD9"), //blue

    @ColorParameter("Tree2 Gradient End")
    var t2c2: ColorRGBa = ColorRGBa.fromHex("#F08C8D") //red
)

fun example1(pos: Vector2): Visualization {
    val tree1 = parseTree(
        "(0" +
                "(10(30)(40(50)(50)))" +
                "(20(25)(30))" +
                "(15(22)(32(40)(37)(45)))" +
                ")"
    )
    val tree2 = parseTree(
        "(0" +
                "(10(40)(30(35)(38(51)(51))))" +
                "(20)" +
                "(10(15)(20))" +
                "(15(30)(32(45)(50)))" +
                ")"
    )

    val ds = DrawSettings(1.5)
    val globalcs = GlobalColorSettings()
    val tcs = TwoColorSettings()
    val gcs = GradientColorSettings()
    val tes = TreeEmbedSettings(8.0)

    return Visualization(tree1, tree2, pos, tes, ds, globalcs, tcs, gcs) { tree1E, tree2E ->
        val leaves1 = tree1E.leaves
        val leaves2 = tree2E.leaves

        val delta = 10.0
        val map12 = leafMapping(buildMap {
            listOf(0, 2, 3, 5, 6, 7, 8, 8, 9).forEachIndexed { i, j ->
                set(leaves1[i], leaves2[j])
            }
        }, delta)
        val map21 = leafMapping(buildMap {
            listOf(0, 1, 2, 2, 3, 4, 4, 5, 6, 8).forEachIndexed { i, j ->
                set(leaves2[i], leaves1[j])
            }
        }, delta)

        Interleaving(map12, map21, delta)
    }
}

fun example2(pos: Vector2): Visualization {
    val tree1 = parseTree("(0(10)(20))")
    val tree2 = parseTree("(0(8)(20))")

    return Visualization(tree1, tree2, pos) { tree1E, tree2E ->
        val leaves1 = tree1E.leaves
        val leaves2 = tree2E.leaves

        val delta = 2.0
        val map12 = leafMapping(buildMap {
            listOf(0, 1).forEachIndexed { i, j ->
                set(leaves1[i], leaves2[j])
            }
        }, delta)
        val map21 = leafMapping(buildMap {
            listOf(0, 1).forEachIndexed { i, j ->
                set(leaves2[i], leaves1[j])
            }
        }, delta)
        Interleaving(map12, map21, delta)
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

        var blobsEnabled = true;

        val visualization = example1(drawer.bounds.center)

        val viewSettings = object {
            @ActionParameter("Fit to screen")
            fun fitToScreen() {
                camera.view = Matrix44.fit(visualization.bbox, drawer.bounds)
            }

            @ActionParameter("Toggle Blobs")
            fun toggleBlobs() {
                blobsEnabled = !blobsEnabled;
            }
        }



        val exportSettings = object {
            @TextParameter("File name")
            var svgFileName: String = "output"

            //TODO: Refactor blob and path decomposition to be part of the composition so that it can be exported to svg.
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
        gui.add(visualization.tcs, "Two Color Settings")
        gui.add(visualization.gcs, "Gradient Color Settings")

        gui.add(viewSettings, "View")
        gui.add(exportSettings, "Export")

        gui.onChange { name, value ->
            // name is the name of the variable that changed
            when (name) {
                "drawNodes", "nodeWidth", "markRadius", "verticalEdgeWidth", "horizontalEdgeWidth",
                "edgeColor", "t1c1", "t1c2", "t2c1", "t2c2"-> {
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

        fun deepestnodeInBlob(blob: Pair<MutableList<EmbeddedMergeTree>, ColorRGBa>): EmbeddedMergeTree? {
            var deepest: EmbeddedMergeTree? = null;

            for (node in blob.first){
                if (deepest == null){
                    deepest = node
                    continue
                }
                if (node.height > deepest.height){
                    deepest = node;
                }
            }
            return deepest
        }

        fun drawBlob(tree: EmbeddedMergeTree, blob: Pair<MutableList<EmbeddedMergeTree>, ColorRGBa>) {
            val tree1 = (tree == visualization.tree1E)
            val deepestNodeInBlob = deepestnodeInBlob(blob);
            val deepestPos = deepestNodeInBlob!!.pos

            drawer.apply {
                strokeWeight = visualization.ds.markRadius / 3
                stroke = null
                fill = blob.second
                for (node in blob.first) {

                    //Visualizing rounded ends of the blobs
//                    fill = blob.second
//                    stroke = null
//                    val pos =
//                        if (tree1) visualization.fromTree1Local(node.pos) else visualization.fromTree2Local(node.pos)
//                    circle(pos, visualization.ds.blobRadius)

                    //Draw blob along edge
                    stroke = blob.second
                    fill = null
                    strokeWeight = visualization.ds.blobRadius * 2
                    if (node.edgeContour != null) {
                        val pos = node.edgeContour.position(1.0)

                        val midX = (pos.x + deepestPos.x)/2
                        val width = abs(pos.x - deepestPos.x)

                        strokeWeight = width + (visualization.ds.blobRadius * 2)

                        val blobContour = LineSegment(Vector2(midX, deepestPos.y), Vector2(midX, -5.0)).contour

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

                                    val contour = LineSegment(Vector2(midX, pos.y), Vector2(midX, -5.0)).contour
                                    if (tree1)
                                        contour(visualization.fromTree1Local(contour))
                                    else contour(visualization.fromTree2Local(contour))
                                }
                            }
                        }
                    }
                }
            }
        }

        fun drawBlobs() {
            if (!blobsEnabled) return;

            //Draw blobs of tree2 (reversed to draw large blobs on top of smaller blobs)
            for (blob in visualization.tree1Blobs.reversed()) {
                drawBlob(visualization.tree1E, blob)
            }

            //Draw blobs of tree2 (reversed to draw large blobs on top of smaller blobs)
            for (blob in visualization.tree2Blobs.reversed()) {
                drawBlob(visualization.tree2E, blob)
            }
        }

        fun drawBlobPath(tree: EmbeddedMergeTree, blob: Pair<MutableList<EmbeddedMergeTree>, ColorRGBa>) {
            val tree1 = (tree == visualization.tree1E)

            drawer.apply {
                fill = null
                strokeWeight = visualization.ds.verticalEdgeWidth * 0.4
                stroke = blob.second;

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

                    if (pathParent.edgeContour != null) {
                        if (tree1)
                            contour(visualization.fromTree2Local(pathParent.edgeContour!!))
                        else contour(visualization.fromTree1Local(pathParent.edgeContour!!))
                    }

                    pathParent = pathParent.parent;
                }
            }
        }

        fun drawBlobPaths() {
            if (!blobsEnabled) return;

            //Draw mapping of blob in the first tree onto the second tree
            for (blob in visualization.tree1Blobs.reversed()) {
                drawBlobPath(visualization.tree1E, blob)
            }

            //Draw mapping of blob in the second tree onto the second tree
            for (blob in visualization.tree2Blobs.reversed()) {
                drawBlobPath(visualization.tree2E, blob)
            }

            //Draw Rays from root
            drawer.apply {
                stroke = ColorRGBa.BLACK
                fill = null
                strokeWeight = visualization.ds.verticalEdgeWidth*0.4

                val rootT1 = visualization.fromTree1Local(visualization.tree1E.pos)
                stroke = visualization.tree2E.blobColor //path should be color of the other tree its root
                lineSegment(rootT1, Vector2(rootT1.x, (camera.view.inversed * Vector2(0.0, 0.01)).y))
                val rootT2 = visualization.fromTree2Local(visualization.tree2E.pos)
                stroke = visualization.tree1E.blobColor //path should be color of the other tree its root
                lineSegment(rootT2, Vector2(rootT2.x, (camera.view.inversed * Vector2(0.0, 0.01)).y))

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
                stroke = visualization.ds.edgeColor
                val rootT1 = visualization.fromTree1Local(visualization.tree1E.pos)
                strokeWeight = visualization.ds.verticalEdgeWidth
                lineSegment(rootT1, Vector2(rootT1.x, (camera.view.inversed * Vector2(0.0, 0.01)).y))
                val rootT2 = visualization.fromTree2Local(visualization.tree2E.pos)
                lineSegment(rootT2, Vector2(rootT2.x, (camera.view.inversed * Vector2(0.0, 0.01)).y))

                //Draw tree
                composition(visualization.composition)

                drawBlobPaths();

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