import org.openrndr.KEY_SPACEBAR
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.isolated
import org.openrndr.extra.color.spaces.ColorOKHSLa
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.parameters.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.shape.*
import org.openrndr.svg.saveToFile
import java.io.File
import java.util.*
import kotlin.math.abs
import kotlin.math.min
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
    @DoubleParameter("Mark radius", 0.1, 10.0, order = 0)
    var markRadius: Double = .5,

    @DoubleParameter("Tree separation", 0.0, 100.0, order = 5)
    var treeSeparation: Double = 30.0,

    @BooleanParameter("Draw Nodes", order = 9)
    var drawNodes: Boolean = false,

    @BooleanParameter("Carve Inwards")
    var carveInwards: Boolean = true,

    @DoubleParameter("Connector radius, ", 0.0, 6.0, order = 10)
    var connectorRadius: Double = 1.0,

    @BooleanParameter("Connector at Top", order = 11)
    var connectorTop: Boolean = true,

    @DoubleParameter("Vertical Edge Width", 0.1, 5.0, order = 20)
    var verticalEdgeWidth: Double = 2.5,

    @DoubleParameter("Vertical mapped Ratio", 0.1, 1.0, order = 21)
    var verticalMappedRatio: Double = 0.7,

    @DoubleParameter("Horizontal Edge Width", 0.1, 5.0, order = 22)
    var horizontalEdgeWidth: Double = verticalEdgeWidth / 9,

    @DoubleParameter("Non Mapped Vertical Edge Width", 0.1, 5.0, order = 23)
    var nonMappedVerticalEdges: Double = verticalEdgeWidth / 9,

    @BooleanParameter("Collapse non mapped", order = 24)
    var collapseNonMapped: Boolean = true,

    @BooleanParameter("Thin non-mapped", order = 25)
    var thinNonMapped: Boolean = true,

    @DoubleParameter("Path Area Scale", 0.0, 3.0)
    var pathAreaPatchScale: Double = 2.0,

    @DoubleParameter("Patch Area Stroke Scale", 0.0, 0.5)
    var patchStrokeScale: Double = 0.15,

    @DoubleParameter("Blob radius", 0.1, 10.0)
    var blobRadius: Double = 4.0,

    @DoubleParameter("Non-mapped blob radius scale", 0.1, 1.0)
    var nonMappedRadius: Double = 0.5,

    @DoubleParameter("Gridline Thickness", 0.01, 0.5, order = 50)
    var gridlineThickness: Double = 0.2,

    @DoubleParameter("Gridline padding", 1.0, 50.0, order = 51)
    var gridlinePadding: Double = 10.0,

//    @DoubleParameter("Whiten", 0.0, 1.0)
    var whiten: Double = 0.0,

//    @ColorParameter("Background color")
    var bgColor: ColorRGBa = ColorRGBa.WHITE,// ColorRGBa.fromHex("#D3D3D3"),

//    @DoubleParameter("Blacken", 0.0, 1.0)
    var blacken: Double = 0.0
    )

data class GlobalColorSettings(
    @BooleanParameter("Enable Gradient")
    var enableGradient: Boolean = false,

    @ColorParameter("EdgeColor1", order = 0)
    var edgeColor: ColorRGBa = ColorRGBa.BLACK,

    @ColorParameter("EdgeColor2", order  = 1)
    var edgeColor2: ColorRGBa = ColorRGBa.BLACK,

    @ColorParameter("Grid color", order = 10)
    var gridColor: ColorRGBa = ColorRGBa.BLACK,

    @DoubleParameter("Grid alpha", 0.01, 1.0, order = 11)
    var gridAlpha: Double = 0.15
)

data class DivergingColorSettings(
    @DoubleParameter("Hue 1", 0.0, 360.0, order=10)
    var hue1: Double = 255.0,

    @DoubleParameter("Hue 2", 0.0, 360.0, order=20)
    var hue2 : Double = 25.0,

    @DoubleParameter("Saturation 1", 0.0, 1.0, order=30)
    var sat1: Double = 0.8,

    @DoubleParameter("Saturation 2", 0.0, 1.0, order=40)
    var sat2: Double = 0.8,

    @DoubleParameter("Saturation 3", 0.0, 1.0, order=50)
    var sat3: Double = 0.8,

    @DoubleParameter("Lightness 1", 0.0, 1.0, order=60)
    var lig1: Double = 0.5,

    @DoubleParameter("Lightness 2", 0.0, 1.0, order=70)
    var lig2: Double = 0.7,

    @DoubleParameter("Lightness 3", 0.0, 1.0, order=80)
    var lig3: Double = 0.9,
)

data class ThreeColorSettings(
    //Tree 1
    @ColorParameter("Tree1 color1 hexcode")
    var t1c1: ColorRGBa = ColorRGBa(0.7333333772420884,0.2952000176752698, 0.27866668335199357),

    @ColorParameter("Tree1 color2 hexcode")
    var t1c2: ColorRGBa =  ColorRGBa.fromHex("#66a61e"), //light-blue

    @ColorParameter("Tree1 color3 hexcode")
    var t1c3: ColorRGBa =  ColorRGBa.fromHex("#7570b3"), //yellow

    //Tree2
    @ColorParameter("Tree2 color1 hexcode")
    var t2c1: ColorRGBa =  ColorRGBa.fromHex("#e7298a"), //dark-blue

    @ColorParameter("Tree2 color2 hexcode")
    var t2c2: ColorRGBa =  ColorRGBa.fromHex("#e6ab02"), //orange

    @ColorParameter("Tree2 color3 hexcode")
    var t2c3: ColorRGBa =  ColorRGBa.fromHex("#d95f02") //green
) {

    constructor(dcs: DivergingColorSettings) : this(
    ColorOKHSLa(dcs.hue1, dcs.sat1, dcs.lig1).toRGBa(), ColorOKHSLa(dcs.hue1, dcs.sat2, dcs.lig2).toRGBa(), ColorOKHSLa(dcs.hue1, dcs.sat3, dcs.lig3).toRGBa(),
        ColorOKHSLa(dcs.hue2, dcs.sat1, dcs.lig1).toRGBa(), ColorOKHSLa(dcs.hue2, dcs.sat2, dcs.lig2).toRGBa(), ColorOKHSLa(dcs.hue2, dcs.sat3, dcs.lig3).toRGBa())
}

fun vulc_25_ts150am_vs_151pm(pos: Vector2): Visualization {
    val tree1 = parseTree("(0.2397(3.1925)(0.543(0.98799(1.3234(1.7029(3.4772(6.4383)(5.9871))(6.0748))(1.629(4.7824)(4.2939)))(1.2587(1.3776(2.0052(7.6294)(6.0667))(9.4685))(7.3541)))(3.2373)))")
    val tree2 = parseTree("(0.3907(0.46803(3.2518)(0.81681(2.0717(7.3303)(5.495))(2.3462(5.4626)(8.0314))))(4.3764))")

    return Visualization(tree1, tree2, pos) { tree1E, tree2E ->
        monotoneInterleaving(tree1E, tree2E)
    }
}

fun main() = application {
    configure {
        width =  800//3500
        height = 700
        title = "Visualizing interleavings"
        windowResizable = true
    }
    program {
        val camera = Camera()

        var blobsEnabled = true

        val visualization = vulc_25_ts150am_vs_151pm(drawer.bounds.center)

        println("Delta: " + visualization.interleaving.delta)
        println("T1 Number of leaves: " + visualization.tree1E.leaves.size)
        println("T2 Number of leaves: " + visualization.tree2E.leaves.size)

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

        val dcs = DivergingColorSettings()

        val gui = GUI()
        gui.add(visualization.tes, "Tree embedding")
        gui.add(visualization.ds, "Drawing")
        gui.add(visualization.globalcs, "Global Color Settings")
        gui.add(visualization.tcs, "Three Color Settings")
        gui.add(dcs, "Diverging Color Settings")

        gui.add(viewSettings, "View")
        gui.add(exportSettings, "Export")

        gui.loadParameters(File("gui-parameters/paper.json"))

        gui.onChange { name, value ->
            when (name) {
                "hue1", "hue2", "hue3", "sat1", "sat2", "sat3", "lig1", "lig2", "lig3" -> {
                    visualization.tcs = ThreeColorSettings(dcs)
                    visualization.compute()
                }
                "svgFileName" ->{
                    //donothing
                }
                else -> {
                    visualization.compute()
                }
            }
        }

        window.drop.listen { dropped ->
            val firstFile = dropped.files.firstOrNull {
                File(it).extension.lowercase() in listOf("txt")
            }
            val secondFile = dropped.files.lastOrNull {
                File(it).extension.lowercase() in listOf("txt")
            }

            if (firstFile != null && secondFile != null) {
                val tree1 = parseTree(File(firstFile).readText())
                val tree2 = parseTree(File(secondFile).readText())

                println("Computing ParkView...")
                visualization.reCompute(tree1, tree2)

                println("Delta: " + visualization.interleaving.delta)

                println("T1 Number of leaves: " + visualization.tree1E.leaves.size)
                println("T2 Number of leaves: " + visualization.tree2E.leaves.size)
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
                if (treePositionToPoint(one) == null) return
                treePositionToPoint(one)?.let { onePoint ->

                    //if (visualization.interleaving.f == null)
                    val other = if (t1ToT2) visualization.interleaving.f[one] else visualization.interleaving.g[one]
                    if (other == null) return
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
                    if (treeMapping.pathCharges.contains(node)) {
                        if (treeMapping.pathCharges[node]!! > 2) {
                            //println(treeMapping.inverseNodeEpsilonMap[node])
                            val pos = if(t1ToT2) visualization.fromTree1Local(node.pos) else visualization.fromTree2Local(node.pos)
                            strokeWeight = visualization.ds.markRadius / 3
                            stroke = null
                            fill = ColorRGBa.BLUE
                            circle(pos, 2.0)
                        }

                    }
                }

//                for (path in treeMapping.pathDecomposition) {
//                    for (node in treeMapping.pathDecomposition[2]) {
//                            val pos =
//                                if (t1ToT2) visualization.fromTree1Local(node.pos) else visualization.fromTree2Local(
//                                    node.pos
//                                )
//                            strokeWeight = visualization.ds.markRadius / 3
//                            stroke = null
//                            fill = ColorRGBa.BLUE
//                            circle(pos, 2.0)
//                    }
//                }
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
                clear(visualization.ds.bgColor)
                visualization.globalcs.edgeColor2 = visualization.globalcs.edgeColor

                //Draw tree
                composition(visualization.composition)

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