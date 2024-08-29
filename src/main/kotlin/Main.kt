import org.openrndr.KEY_SPACEBAR
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.isolated
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.parameters.ActionParameter
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.extra.parameters.TextParameter
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

    @DoubleParameter("Blob radius", 0.1, 10.0)
    var blobRadius: Double = 5.0
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
                "(10(40)(30(35)(38(43)(43))))" +
                "(20)" +
                "(10(15)(20))" +
                "(15(30)(32(45)(50)))" +
                ")"
    )

    val ds = DrawSettings(1.5)
    val tes = TreeEmbedSettings(17.5)

    return Visualization(tree1, tree2, pos, tes, ds) { tree1E, tree2E ->
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
        width = 800
        height = 800
        title = "Visualizing interleavings"
    }
    program {
        val camera = Camera()

        var blobsEnabled = false;

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
        gui.add(viewSettings, "View")
        gui.add(exportSettings, "Export")

        gui.onChange { name, value ->
            // name is the name of the variable that changed
            when (name) {
                "nodeWidth", "markRadius" -> {
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

        fun drawBlobs() {
            if (!blobsEnabled) return;
            drawer.apply {
                strokeWeight = visualization.ds.markRadius / 3
                stroke = null

                //Draw blobs of tree1 (reversed to draw large blobs on top of smaller blobs)
                for (blob in visualization.tree1Blobs.reversed()) {
                    for (node in blob.first){
                        //Draw blob around node
                        fill = blob.second
                        stroke = null
                        val pos = visualization.fromTree1Local(node.pos)
                        circle(pos, visualization.ds.blobRadius)

                        //Draw blob along edge
                        stroke = blob.second
                        fill = null
                        strokeWeight = visualization.ds.blobRadius *2
                        if (node.edgeContour != null) {
                            contour(visualization.fromTree1Local(node.edgeContour))
                        }

                    }
                }

                //Draw blobs of tree2 (reversed to draw large blobs on top of smaller blobs)
                for (blob in visualization.tree2Blobs.reversed()) {
                    //Draw blob around node
                    fill = blob.second;
                    for (node in blob.first){
                        fill = blob.second
                        stroke = null
                        val pos = visualization.fromTree2Local(node.pos)
                        circle(pos, visualization.ds.blobRadius)

                        //Draw blob along edge
                        stroke = blob.second
                        fill = null
                        strokeWeight = visualization.ds.blobRadius *2
                        if (node.edgeContour != null) {
                            contour(visualization.fromTree2Local(node.edgeContour))
                        }
                    }
                }
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

                // Draw ray upward from roots
                val rootT1 = visualization.fromTree1Local(visualization.tree1E.pos)
                strokeWeight = visualization.ds.markRadius / 3
                lineSegment(rootT1, Vector2(rootT1.x, (camera.view.inversed * Vector2(0.0, 0.0)).y))
                val rootT2 = visualization.fromTree2Local(visualization.tree2E.pos)
                lineSegment(rootT2, Vector2(rootT2.x, (camera.view.inversed * Vector2(0.0, 0.0)).y))
                
                drawBlobs();

                composition(visualization.composition)
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