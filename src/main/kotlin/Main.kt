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
import kotlinx.coroutines.*

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

fun startInput(pos: Vector2): Visualization {
    val tree1 = parseTree("(0.084303(0.089764(0.11142)(0.10713))(0.16119(0.44434(0.68726)(0.4826))(0.16453(0.19375(0.19375(0.26358(0.3994(0.41339(0.41661(0.49857(0.51343(0.52593)(0.74111))(0.57261(0.63464)(0.58276)))(0.43741(0.50612)(0.44335(0.49114)(0.47629))))(0.4488(0.47851(0.50711)(0.54549(0.896)(0.57013)))(0.51578)))(0.48037(0.5263)(0.50637)))(0.29094(0.35706(0.81045)(0.39061(0.46069)(0.44162)))(0.30308)))(0.22247(0.25207(0.311(0.33267(0.34517(0.39073(0.40138)(1.0))(0.37092))(0.35186))(0.32734))(0.27088))(0.23312)))(0.24872(0.26234(0.35743(0.38392(0.39507(0.44174)(0.40794))(0.4613))(0.80995))(0.27844))(0.26742(0.38999(0.3994(0.4488(0.47666(0.50018(0.63489)(0.50786(0.52643)(0.74173)))(0.4826(0.50699)(0.53126(0.54859)(0.54525(0.71821(0.89687)(0.73344))(0.57013)))))(0.51578))(0.48025(0.52643)(0.50637)))(0.40249))(0.27819))))(0.22173))))")
    val tree2 = parseTree("(0.092946(0.099749(0.12359)(0.11917))(0.1855(0.19195(0.47786(0.77421)(0.55184))(0.25701))(0.22022(0.28954(0.30587(0.45562(0.50216(0.51945(0.63228(0.81031)(0.69418(0.75074)(0.81991)))(0.5211(0.57147)(0.53757(0.55102)(0.55953))))(0.50545(0.59714(0.8589(0.92643)(0.94098))(0.63063))(0.57641)))(0.54141(0.59316)(0.56859)))(0.31946))(0.30738(0.33538)(0.41307(0.99629)(0.48596(0.50531(0.55033)(0.54896))(0.50765)))))(0.22118(0.29613(0.3115(0.3535(0.37286(0.40607(0.45727(1.0)(0.47114))(0.42804))(0.39427))(0.36915))(0.33799))(0.3067))(0.29119(0.4025(0.43092(0.49296(0.51025(0.54855)(0.55033))(0.50751))(0.45686))(0.99492))(0.29709(0.43147(0.45562(0.50312(0.55006(0.55143(0.62748(0.82225)(0.68073(0.69707)(0.80976)))(0.59714))(0.597(0.87894(0.94249)(0.92684))(0.63063)))(0.57641))(0.54141(0.5933)(0.56859)))(0.44176))(0.3137)))))))")

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

        //val visualization = vulc_25_ts150am_vs_151pm(drawer.bounds.center)
        val visualization = startInput(drawer.bounds.center)

        println("Delta: " + visualization.interleaving.delta)
        println("T1 Number of leaves: " + visualization.tree1E.leaves.size)
        println("T2 Number of leaves: " + visualization.tree2E.leaves.size)

        val viewSettings = object {
            @ActionParameter("Fit to screen")
            fun fitToScreen() {

                if (visualization.bbox.height.isNaN()){
                    val box = Rectangle(400.0, 100.0, 800.0, 800.0)
                    camera.view = Matrix44.fit(box, drawer.bounds)
                }
                else {
                    camera.view = Matrix44.fit(visualization.bbox, drawer.bounds)
                    println(visualization.bbox)
                }
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

        fun HandleTreeInput(firstFile: String, secondFile: String) = runBlocking<Unit>  {
            val tree1 = async { parseTree(File(firstFile).readText()) }
            val tree2 = async { parseTree(File(secondFile).readText()) }

            println("Computing ParkView...")
            visualization.reCompute(tree1.await(), tree2.await())

            println("Delta: " + visualization.interleaving.delta)

            println("T1 Number of leaves: " + visualization.tree1E.leaves.size)
            println("T2 Number of leaves: " + visualization.tree2E.leaves.size)
        }

        window.drop.listen { dropped ->
            val firstFile = dropped.files.firstOrNull {
                File(it).extension.lowercase() in listOf("txt")
            }
            val secondFile = dropped.files.lastOrNull {
                File(it).extension.lowercase() in listOf("txt")
            }

            if (firstFile != null && secondFile != null) {
                HandleTreeInput(firstFile, secondFile)
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