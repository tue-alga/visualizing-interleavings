import org.openrndr.KEY_SPACEBAR
import org.openrndr.application
import org.openrndr.color.ColorHSVa
import org.openrndr.color.ColorRGBa
import org.openrndr.color.rgb
import org.openrndr.draw.isolated
import org.openrndr.extra.color.spaces.ColorOKHSLa
import org.openrndr.extra.color.spaces.ColorOKHSVa
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.parameters.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.shape.*
import org.openrndr.svg.saveToFile
import java.io.File
import java.util.*
import kotlin.math.abs
import kotlin.math.max
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
    var blobRadius: Double = 8.0,

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
    //@ColorParameter
    //pathColor parameter here

    //Tree 1
    @ColorParameter("Tree1 color1 hexcode")
//    var t1c1: ColorRGBa = ColorRGBa(49 / 255.0,  135 / 255.0, 188 / 255.0),
//    var t1c1: ColorRGBa = ColorRGBa.fromHex("#C5037D"), //purple
//    var t1c1: ColorRGBa = ColorRGBa.fromHex("#66c2a5"), //purple
//    var t1c1: ColorRGBa = ColorRGBa.fromHex("#1b9e77"), //purple
    var t1c1: ColorRGBa = ColorRGBa(0.7333333772420884,0.2952000176752698, 0.27866668335199357),
//    var t1c1: ColorRGBa = ColorRGBa.fromHex("#E41A1C"),

    @ColorParameter("Tree1 color2 hexcode")
//    var t1c2: ColorRGBa = ColorRGBa(85 / 255.0,  164 / 255.0, 189 / 255.0), //
//    var t1c2: ColorRGBa =  ColorRGBa.fromHex("#E96222"), //orange
//    var t1c2: ColorRGBa =  ColorRGBa.fromHex("#a6d854"), //light-blue
    var t1c2: ColorRGBa =  ColorRGBa.fromHex("#66a61e"), //light-blue
//    var t1c2: ColorRGBa =  ColorRGBa.fromHex("#377EB8"),

    @ColorParameter("Tree1 color3 hexcode")
//    var t1c3: ColorRGBa = ColorRGBa(133 / 255.0, 120 / 255.0, 220 / 255.0),//
//    var t1c3: ColorRGBa =  ColorRGBa.fromHex("#FCC60E"), //yellow
//    var t1c3: ColorRGBa =  ColorRGBa.fromHex("#8da0cb"), //yellow
    var t1c3: ColorRGBa =  ColorRGBa.fromHex("#7570b3"), //yellow
 //   var t1c3: ColorRGBa =  ColorRGBa.fromHex("#4DAF4A"),

    //Tree2
    @ColorParameter("Tree2 color1 hexcode")
//    var t2c1: ColorRGBa = ColorRGBa(212 / 255.0,  61 / 255.0, 79 / 255.0),//
//    var t2c1: ColorRGBa =  ColorRGBa.fromHex("#454F96"), //dark-blue
//    var t2c1: ColorRGBa =  ColorRGBa.fromHex("#e78ac3"), //dark-blue
    var t2c1: ColorRGBa =  ColorRGBa.fromHex("#e7298a"), //dark-blue
  //  var t2c1: ColorRGBa =  ColorRGBa.fromHex("#E41A1C"),

    @ColorParameter("Tree2 color2 hexcode")
//    var t2c2: ColorRGBa = ColorRGBa(244 / 255.0, 108 / 255.0, 67 / 255.0),//
//    var t2c2: ColorRGBa =  ColorRGBa.fromHex("#e78ac3"), //light-blue
//    var t2c2: ColorRGBa =  ColorRGBa.fromHex("#fc8d62"), //orange
    var t2c2: ColorRGBa =  ColorRGBa.fromHex("#e6ab02"), //orange
//    var t2c2: ColorRGBa =  ColorRGBa.fromHex("#377EB8"),

    @ColorParameter("Tree2 color3 hexcode")
//    var t2c3: ColorRGBa = ColorRGBa(250 / 255.0, 155 / 255.0, 26 / 255.0)///
//    var t2c3: ColorRGBa =  ColorRGBa.fromHex("#8DBB25") //green
    var t2c3: ColorRGBa =  ColorRGBa.fromHex("#d95f02") //green
 //   var t2c3: ColorRGBa =  ColorRGBa.fromHex("#4DAF4A")
) {
//    constructor(dcs: DivergingColorSettings) : this(ColorHSVa(dcs.hue1, dcs.sat1, dcs.val1).toRGBa(), ColorHSVa(dcs.hue1, dcs.sat2, dcs.val2).toRGBa(), ColorHSVa(dcs.hue1, dcs.sat3, dcs.val3).toRGBa(),
//                                                    ColorHSVa(dcs.hue2, dcs.sat1, dcs.val1).toRGBa(), ColorHSVa(dcs.hue2, dcs.sat2, dcs.val2).toRGBa(), ColorHSVa(dcs.hue2, dcs.sat3, dcs.val3).toRGBa())
//    constructor(dcs: DivergingColorSettings) : this(ColorOKHSVa(dcs.hue1, dcs.sat1, dcs.val1).toRGBa(), ColorOKHSVa(dcs.hue1, dcs.sat2, dcs.val2).toRGBa(), ColorOKHSVa(dcs.hue1, dcs.sat3, dcs.val3).toRGBa(),
//        ColorOKHSVa(dcs.hue2, dcs.sat1, dcs.val1).toRGBa(), ColorOKHSVa(dcs.hue2, dcs.sat2, dcs.val2).toRGBa(), ColorOKHSVa(dcs.hue2, dcs.sat3, dcs.val3).toRGBa())
    constructor(dcs: DivergingColorSettings) : this(
    ColorOKHSLa(dcs.hue1, dcs.sat1, dcs.lig1).toRGBa(), ColorOKHSLa(dcs.hue1, dcs.sat2, dcs.lig2).toRGBa(), ColorOKHSLa(dcs.hue1, dcs.sat3, dcs.lig3).toRGBa(),
        ColorOKHSLa(dcs.hue2, dcs.sat1, dcs.lig1).toRGBa(), ColorOKHSLa(dcs.hue2, dcs.sat2, dcs.lig2).toRGBa(), ColorOKHSLa(dcs.hue2, dcs.sat3, dcs.lig3).toRGBa())
}

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
                "(10(40)(30(35)(38(50)(51))))" + //"(10(40)(30(35)(38(51)(51))))" +  //
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
            "(5(35(45(65)(55)(60))(40)(40))(15))" +
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
            "(5(35(45(65)(55)(60))(40)(40))(15))" +
            "(5(15)(25(35)(30)))" +
            "(35(55)(40)(45(60(65)(65)(70))(55)(65)))" +
            "(20(35)(30)(35)(40(55)(50)(45))(30(35)(35)))" +
            ")"
    )

    return Visualization(tree1, tree2, pos) { tree1E, tree2E ->
        monotoneInterleaving(tree1E, tree2E)
    }
}

fun example7(pos: Vector2): Visualization {
    val tree1 = parseTree("(0" +
            "(5(15)(25(35)(30)))" +
            "(35(55)(40)(45(60(65)(65)(70))(55)(65)))" +
            "(20(35)(30)(35)(40(55)(50)(45))(30(35)(35)))" +
            ")"
    )
    val tree2 = parseTree("(0" +
            "(5(15)(25(35)(30)))" +
            "(35(55)(40)(45(60(65)(65)(70))(55)(65)))" +
            "(20(35)(30)(35)(40(55)(50)(45))(30(35)(35)))" +
            ")"
    )

    return Visualization(tree1, tree2, pos) { tree1E, tree2E ->
        monotoneInterleaving(tree1E, tree2E)
    }
}

fun example5(pos: Vector2): Visualization {
    val tree1 = parseTree("(0" +
            "(25(35)(40(50)(55(70)(65)(60)(70))(50)))" +
            "(5(15(25)(20))(10)(30(40)(35)))" +
            "(10(15)(30(50)(55)))" +
            "(15(35)(50(55)(60)(65))(20)(25))" +
            "(5(35(45(65)(55)(60))(40)(40)))" +
            "(5(15)(25(35)(30)))" +
            "(35(55)(40)(45(60(65)(65)(70))(55)))" +
            "(20(40(55)(50)(45))(30(35)(35)))" +
            ")"
    )
    val tree2 = parseTree("(0" +
            "(25(35)(40(50)(55(70)(65)(60)(70))(50)))" +
            "(5(15(25)(20))(10)(30(40)(35)))" +
            "(10(15)(30(50)(55)))" +
            "(15(35)(50(55)(60)(65))(20)(25))" +
            "(5(35(45(65)(55)(60))(40)(40)))" +
            "(5(15)(25(35)(30)))" +
            "(35(55)(40)(45(60(65)(65)(70))(55)))" +
            "(20(40(55)(50)(45))(30(35)(35)))" +
            ")"
    )

    return Visualization(tree1, tree2, pos) { tree1E, tree2E ->
        monotoneInterleaving(tree1E, tree2E)
    }
}

fun example6(pos: Vector2): Visualization {
    val tree1 = parseTree("(0.001(100)(161.19(687.26)(164.53(193.7(248.72(399.4(526.43)(448.79999999999995(476.65999999999997(896.8699999999999)(500.17999999999995(741.73)(634.89)))(515.78)))(357.43(809.9499999999999)(383.91999999999996(461.3)(441.74))))(193.75(1000.0)(263.58(399.4(526.3)(413.39(448.79999999999995(896.0)(515.78))(416.60999999999996(498.57(741.11)(634.64))(437.41(506.12)(491.14000000000004)))))(357.06(810.45)(390.61(460.69)(441.62))))))(221.73000000000002))))")
    val test = parseTree("(0.001(100)(161.19(687.26)(164.53(193.7(248.72(399.4(526.43)(448.79999999999995))))))))) ") //(476.65999999999997(896.8699999999999))))))))) ")//(500.17999999999995(741.73)(634.89)))(515.78)))") //(357.43(809.9499999999999)(383.91999999999996(461.3)(441.74))))(193.75(1000.0)(263.58(399.4(526.3)(413.39(448.79999999999995(896.0)(515.78))(416.60999999999996(498.57(741.11)(634.64))(437.41(506.12)(491.14000000000004)))))(357.06(810.45)(390.61(460.69)(441.62))))))(221.73000000000002))))")

    val tree2 = parseTree("(1e-06(0.16119(0.68726)(0.16453(0.1937(0.24872(0.3994(0.52643)(0.4488(0.47666(0.89687)(0.50018(0.74173)(0.63489)))(0.51578)))(0.35743(0.80995)(0.38392(0.4613)(0.44174))))(0.19375(1.0)(0.26358(0.3994(0.5263)(0.41339(0.4488(0.896)(0.51578))(0.41661(0.49857(0.74111)(0.63464))(0.43741(0.50612)(0.49114)))))(0.35706(0.81045)(0.39061(0.46069)(0.44162))))))(0.22173))))")

    return Visualization(tree1, tree1, pos) { tree1E, tree2E ->
        monotoneInterleaving(tree1E, tree2E)
    }
}

fun realExample1(pos: Vector2): Visualization { //Time step 25 vs 26
    val tree1 = parseTree("(0.001(161.19(687.26)(193.75(248.72(399.4(476.65999999999997(896.8699999999999)(500.17999999999995(741.73)(634.89)))(526.43))(357.43(809.9499999999999)(461.3)))(195.09(1000.0)(263.58(399.4(413.39(896.0)(498.57(741.11)(634.64)))(526.3))(357.06(810.45)(460.69)))))))")
    val tree2 = parseTree("(0.001(185.5(774.2099999999999)(220.22(289.54(455.62(502.16(940.98)(632.28(819.9100000000001)(810.31)))(593.16))(413.07(996.29)(550.33)))(221.17999999999998(1000.0)(291.19(455.62(550.06(942.49)(627.48(822.25)(809.76)))(593.3000000000001))(402.5(994.9200000000001)(550.33)))))))")

    return Visualization(tree1, tree2, pos) { tree1E, tree2E ->
        monotoneInterleaving(tree1E, tree2E)
    }
}
fun realExample2(pos: Vector2): Visualization {
    val tree1 = parseTree("(0.001(161.19(687.26)(164.53(193.7(248.72(399.4(526.43)(448.79999999999995(476.65999999999997(896.8699999999999)(500.17999999999995(741.73)(634.89)))(515.78)))(357.43(809.9499999999999)(383.91999999999996(461.3)(441.74))))(193.75(1000.0)(263.58(399.4(526.3)(413.39(448.79999999999995(896.0)(515.78))(416.60999999999996(498.57(741.11)(634.64))(437.41(506.12)(491.14000000000004)))))(357.06(810.45)(390.61(460.69)(441.62))))))(221.73000000000002))))")
    val tree2 = parseTree("(0.001(185.5(774.2099999999999)(220.22(289.54(455.62(502.16(940.98)(632.28(819.9100000000001)(810.31)))(593.16))(413.07(996.29)(550.33)))(221.17999999999998(1000.0)(291.19(455.62(550.06(942.49)(627.48(822.25)(809.76)))(593.3000000000001))(402.5(994.9200000000001)(550.33)))))))")
    //val tree3 = parseTree("(0.001(161.19(687.26)(193.75(248.72(399.4(476.65999999999997(896.8699999999999)(500.17999999999995(741.73)(634.89)))(526.43))(357.43(809.9499999999999)(461.3)))(195.09(1000.0)(263.58(399.4(413.39(896.0)(498.57(741.11)(634.64)))(526.3))(357.06(810.45)(460.69)))))))")

    //val tree2 = parseTree("(0.001(161.19(687.26)(164.53(193.7(248.72(399.4(526.43)(448.79999999999995(476.65999999999997(896.8699999999999)(500.17999999999995(741.73)(634.89)))(515.78)))(357.43(809.9499999999999)(383.91999999999996(461.3)(441.74))))(193.75(1000.0)(263.58(399.4(526.3)(413.39(448.79999999999995(896.0)(515.78))(416.60999999999996(498.57(741.11)(634.64))(437.41(506.12)(491.14000000000004)))))(357.06(810.45)(390.61(460.69)(441.62))))))(221.73000000000002))))")

    return Visualization(tree1, tree2, pos) { tree1E, tree2E ->
        monotoneInterleaving(tree1E, tree2E)
    }
}
fun realExample3(pos: Vector2): Visualization { //Time step 26 vs 75
    val tree1 = parseTree("(0.001(185.5(774.2099999999999)(220.22(289.54(455.62(502.16(940.98)(632.28(819.9100000000001)(810.31)))(593.16))(413.07(996.29)(550.33)))(221.17999999999998(1000.0)(291.19(455.62(550.06(942.49)(627.48(822.25)(809.76)))(593.3000000000001))(402.5(994.9200000000001)(550.33)))))))")
    val tree2 = parseTree("(0.001(168.17(193.52(563.5799999999999)(193.73000000000002(733.6899999999999(1000.0)(996.4399999999999))(563.29)))(343.62(530.61)(343.98(644.89)(529.83)))))")

    return Visualization(tree1, tree2, pos) { tree1E, tree2E ->
        monotoneInterleaving(tree1E, tree2E)
    }
}

fun largeExample2(pos: Vector2): Visualization { //timestep 0025
    val tree1 = parseTree("(1e-06(0.084303(0.16119(0.44434(0.68726)(0.4826))(0.16453(0.19375(0.24872(0.26234(0.35743(0.80995)(0.38392(0.4613)(0.39507(0.44174)(0.40794))))(0.27844))(0.26742(0.38999(0.3994(0.4488(0.51578)(0.47666(0.50018(0.63489)(0.50786(0.74173)(0.52643)))(0.4826(0.53126(0.54525(0.71821(0.89687)(0.73344))(0.57013))(0.54859))(0.50699))))(0.48025(0.52643)(0.50637)))(0.40249))(0.27819)))(0.19385(0.26358(0.3994(0.48037(0.5263)(0.50637))(0.41339(0.4488(0.51578)(0.47851(0.54549(0.896)(0.57013))(0.50711)))(0.41661(0.49857(0.57261(0.63464)(0.58276))(0.51343(0.74111)(0.52593)))(0.43741(0.50612)(0.44335(0.49114)(0.47629))))))(0.29094(0.35706(0.81045)(0.39061(0.46069)(0.44162)))(0.30308)))(0.22247(0.25207(0.311(0.33267(0.34517(0.39073(1.0)(0.40138))(0.37092))(0.35186))(0.32734))(0.27088))(0.23312))))(0.22173)))(0.089764(0.11142)(0.10713))))")
    val tree1Adjusted = parseTree("(1e-06(0.084303(0.16119(0.44434(0.68726)(0.4826))(0.17453(0.19375(0.24872(0.26234(0.35743(0.80995)(0.38392(0.4613)(0.39507(0.44174)(0.40794))))(0.27844))(0.26742(0.38999(0.44(0.63489)(0.50786(0.74173)(0.52643))(0.50(0.53126(0.54525(0.71821(0.89687)(0.73344))(0.57013))(0.54859))(0.50699)))(0.48025(0.52643)(0.50637))))(0.27819)))(0.19385(0.26358(0.3994(0.4288(0.51578)(0.47851(0.54549(0.896))(0.50711))(0.49857(0.57261(0.63464)(0.58276))(0.51343(0.74111)(0.52593)))(0.44335(0.50612)(0.49114)(0.47629))))(0.29094(0.35706(0.81045)(0.39061(0.46069)(0.44162)))(0.30308)))(0.22247(0.34517(0.39073(1.0)(0.40138))(0.37092)))(0.27088))(0.23312))(0.22173)))(0.089764(0.11142)(0.10713))))")
    val tree4Adjusted = parseTree("(1e-06(0.084303(0.16119(0.44434(0.68726)(0.4826))(0.17453(0.19375(0.24872(0.26234(0.35743(0.80995)(0.38392(0.4613)(0.39507(0.44174)(0.40794))))(0.27844))(0.26742(0.38999(0.44(0.63489)(0.50786(0.74173)(0.52643))(0.50(0.53126(0.54525(0.71821(0.89687)(0.73344))(0.57013))(0.54859))(0.50699)))(0.48025(0.52643)(0.50637))))(0.27819)))(0.19385(0.26358(0.3994(0.4288(0.51578)(0.47851(0.54549(0.896))(0.50711))(0.49857(0.57261(0.63464)(0.58276))(0.51343(0.74111)(0.52593)))(0.44335(0.50612)(0.49114)(0.47629))))(0.29094(0.35706(0.81045)(0.39061(0.46069)(0.44162)))(0.30308)))(0.22247(0.34517(0.39073(1.0)(0.40138))(0.37092)))(0.27088))(0.23312))(0.22173)))(0.089764(0.11142)(0.11713))))")
    val tree2 = parseTree("(1e-06(0.092946(0.1855(0.22022(0.28954(0.30738(0.41307(0.99629)(0.48596(0.50531(0.55033)(0.54896))(0.50765)))(0.33538))(0.30587(0.45562(0.54141(0.59316)(0.56859))(0.50216(0.50545(0.59714(0.8589(0.94098)(0.92643))(0.63063))(0.57641))(0.51945(0.63228(0.81031)(0.69418(0.81991)(0.75074)))(0.5211(0.57147)(0.53757(0.55953)(0.55102))))))(0.31946)))(0.22118(0.29119(0.4025(0.99492)(0.43092(0.49296(0.51025(0.55033)(0.54855))(0.50751))(0.45686)))(0.29709(0.43147(0.45562(0.50312(0.55006(0.597(0.87894(0.94249)(0.92684))(0.63063))(0.55143(0.62748(0.82225)(0.68073(0.80976)(0.69707)))(0.59714)))(0.57641))(0.54141(0.5933)(0.56859)))(0.44176))(0.3137)))(0.29613(0.3115(0.3535(0.37286(0.40607(0.45727(1.0)(0.47114))(0.42804))(0.39427))(0.36915))(0.33799))(0.3067))))(0.19195(0.47786(0.77421)(0.55184))(0.25701)))(0.099749(0.12359)(0.11917))))\n")
    val tree2Adjusted = parseTree("(1e-06(0.092946(0.1855(0.22022(0.24954(0.30738(0.41307(0.99629)(0.48596(0.50531(0.55033)(0.54896))(0.50765)))(0.33538))(0.30587(0.45562(0.54141(0.59316)(0.56859))(0.50216(0.50545(0.59714(0.8589(0.94098)(0.92643))(0.63063))(0.57641))(0.51945(0.63228(0.81031)(0.69418(0.81991)(0.75074)))(0.5211(0.57147)(0.53757(0.55953)(0.55102))))))(0.31946)))(0.22118(0.29119(0.4025(0.99492)(0.43092(0.49296(0.51025(0.55033)(0.54855))(0.50751))(0.45686)))(0.29709(0.43147(0.55143(0.597(0.87894(0.94249)(0.92684))(0.63063))(0.62748(0.82225)(0.68073(0.80976)(0.69707))(0.64714)))(0.54141(0.5933)(0.56859)))(0.44176))(0.3137)))(0.29613(0.3115(0.3535(0.37286(0.40607(0.45727(1.0)(0.47114))(0.42804))(0.39427))(0.36915))(0.33799))(0.3067))))(0.19195(0.47786(0.77421)(0.55184))(0.25701)))(0.099749(0.12359)(0.11917))))\n")


    return Visualization(tree1Adjusted, tree2Adjusted, pos) { tree1E, tree2E ->
        monotoneInterleaving(tree1E, tree2E)
    }
}

fun largeExample1(pos: Vector2): Visualization {
    val tree1 = parseTree("(1e-06(0.084303(0.16119(0.44434(0.68726)(0.4826))(0.16453(0.19375(0.24872(0.26234(0.35743(0.80995)(0.38392(0.4613)(0.39507(0.44174)(0.40794))))(0.27844))(0.26742(0.38999(0.3994(0.4488(0.51578)(0.47666(0.50018(0.63489)(0.50786(0.74173)(0.52643)))(0.4826(0.53126(0.54525(0.71821(0.89687)(0.73344))(0.57013))(0.54859))(0.50699))))(0.48025(0.52643)(0.50637)))(0.40249))(0.27819)))(0.19475(0.26358(0.3994(0.48037(0.5263)(0.50637))(0.41339(0.4488(0.51578)(0.47851(0.54549(0.896)(0.57013))(0.50711)))(0.41661(0.49857(0.57261(0.63464)(0.58276))(0.51343(0.74111)(0.52593)))(0.43741(0.50612)(0.44335(0.49114)(0.47629))))))(0.29094(0.35706(0.81045)(0.39061(0.46069)(0.44162)))(0.30308)))(0.22247(0.25207(0.311(0.33267(0.34517(0.39073(1.0)(0.40138))(0.37092))(0.35186))(0.32734))(0.27088))(0.23312))))(0.22173)))(0.089764(0.11142)(0.10713))))")
    val tree2 = parseTree("(1e-06(0.092946(0.1855(0.22022(0.28954(0.30738(0.41307(0.99629)(0.48596(0.50531(0.55033)(0.54896))(0.50765)))(0.33538))(0.30587(0.45562(0.54141(0.59316)(0.56859))(0.50216(0.50545(0.59714(0.8589(0.94098)(0.92643))(0.63063))(0.57641))(0.51945(0.63228(0.81031)(0.69418(0.81991)(0.75074)))(0.5211(0.57147)(0.53757(0.55953)(0.55102))))))(0.31946)))(0.22118(0.29119(0.4025(0.99492)(0.43092(0.49296(0.51025(0.55033)(0.54855))(0.50751))(0.45686)))(0.29709(0.43147(0.45562(0.50312(0.55006(0.597(0.87894(0.94249)(0.92684))(0.63063))(0.55143(0.62748(0.82225)(0.68073(0.80976)(0.69707)))(0.59714)))(0.57641))(0.54141(0.5933)(0.56859)))(0.44176))(0.3137)))(0.29613(0.3115(0.3535(0.37286(0.40607(0.45727(1.0)(0.47114))(0.42804))(0.39427))(0.36915))(0.33799))(0.3067))))(0.19195(0.47786(0.77421)(0.55184))(0.25701)))(0.099749(0.12359)(0.11917))))")

    return Visualization(tree1, tree2, pos) { tree1E, tree2E ->
        monotoneInterleaving(tree1E, tree2E)
    }
}

fun volcanicExampleSmall(pos: Vector2): Visualization {
    val tree1 = parseTree("(0.56233(0.90966(1.0633(1.149(6.4553)(4.9931))(6.0679))(1.2913(6.2512)(1.6497(8.8859)(8.8254))))(4.5984))")
    val tree2 = parseTree("(0.33197(0.85816(5.8418)(1.1753(1.2979(5.4803)(1.4873(5.3434)(4.9546)))(5.1361)))(5.6036))")

    return Visualization(tree1, tree2, pos) { tree1E, tree2E ->
        monotoneInterleaving(tree1E, tree2E)
    }
}

fun ionizationExample(pos: Vector2): Visualization { //TS 25 vs 26 -> space-filling curve
    val tree1 = parseTree("(0.16119(0.68726)(0.19375(0.19509(0.26358(0.3994(0.41339(0.49857(0.74111)(0.63464))(0.896))(0.5263))(0.35706(0.81045)(0.46069)))(1.0))(0.24872(0.35743(0.4613)(0.80995))(0.3994(0.47666(0.50018(0.63489)(0.74173))(0.89687))(0.52643)))))")
    val tree2 = parseTree("(0.1855(0.77421)(0.22022(0.28954(0.45562(0.50216(0.63228(0.81031)(0.81991))(0.94098))(0.59316))(0.41307(0.99629)(0.55033)))(0.22118(1.0)(0.29119(0.4025(0.55033)(0.99492))(0.45562(0.55006(0.62748(0.82225)(0.80976))(0.94249))(0.5933))))))")

    return Visualization(tree1, tree2, pos) { tree1E, tree2E ->
        monotoneInterleaving(tree1E, tree2E)
    }
}

fun ionizationExample2(pos: Vector2): Visualization { //TS 25 vs 75 -> space-filling curve
    val tree1 = parseTree("(0.16119(0.68726)(0.19375(0.19509(0.26358(0.3994(0.41339(0.49857(0.74111)(0.63464))(0.896))(0.5263))(0.35706(0.81045)(0.46069)))(1.0))(0.24872(0.35743(0.4613)(0.80995))(0.3994(0.47666(0.50018(0.63489)(0.74173))(0.89687))(0.52643)))))")
    val tree2 = parseTree("(0.1855(0.77421)(0.22022(0.28954(0.45562(0.50216(0.63228(0.81031)(0.81991))(0.94098))(0.59316))(0.41307(0.99629)(0.55033)))(0.22118(1.0)(0.29119(0.4025(0.55033)(0.99492))(0.45562(0.55006(0.62748(0.82225)(0.80976))(0.94249))(0.5933))))))")
    val tree3 = parseTree("(0.16817(0.34362(0.34398(0.39567(0.49258)(0.64489))(0.52983))(0.53061))(0.19352(0.31891(0.56358)(0.37331))(0.19373(0.73369(1.0)(0.99644))(0.31898(0.56329)(0.37324)))))")
    return Visualization(tree1, tree3, pos) { tree1E, tree2E ->
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

        val visualization = ionizationExample(drawer.bounds.center)

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
        gui.add(visualization.gcs, "Gradient Color Settings")
        gui.add(dcs, "Diverging Color Settings")

        gui.add(viewSettings, "View")
        gui.add(exportSettings, "Export")

        gui.loadParameters(File("gui-parameters/paper.json"))

//        val f = File("colors.txt")
//        val lines = f.readLines()
//        visualization.tcs.t1c1 = ColorRGBa.fromHex(lines[0])
//        visualization.tcs.t1c2 = ColorRGBa.fromHex(lines[1])
//        visualization.tcs.t1c3 = ColorRGBa.fromHex(lines[2])
//        visualization.tcs.t2c1 = ColorRGBa.fromHex(lines[3])
//        visualization.tcs.t2c2 = ColorRGBa.fromHex(lines[4])
//        visualization.tcs.t2c3 = ColorRGBa.fromHex(lines[5])

        gui.onChange { name, value ->
            when (name) {
                "hue1", "hue2", "hue3", "sat1", "sat2", "sat3", "lig1", "lig2", "lig3" -> {
                    visualization.tcs = ThreeColorSettings(dcs)
                    visualization.compute()
                }

                else -> {
                    visualization.compute()
                }
            }

            // name is the name of the variable that changed
//            when (name) {
//                "drawNodes", "nodeWidth", "carveInwards", "connectorRadius", "connectorTop", "nonMappedRadius", "markRadius",
//                "verticalEdgeWidth", "verticalMappedRatio", "horizontalEdgeWidth", "nonMappedVerticalEdges", "collapseNonMapped", "thinNonMapped", "pathAreaPatchScale", "patchStrokeScale", "areaPatchStrokeScale",
//                "edgeColor", "edgeColor2", "blacken", "enableGradient", "colorInterpolation", "t1c1", "t1c2", "t1c3", "t2c1", "t2c2", "t2c3",
//                "gridlineThickness", "gridlinePadding", "gridColor", "gridAlpha"
//                    -> {
//                    visualization.compute()
//                }
//            }
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

        fun deepestNodeInBlob(blob: Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>): TreePosition<EmbeddedMergeTree>? {
            var deepest:TreePosition<EmbeddedMergeTree>? = null

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

        fun highestNodeInBlob(blob: Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>): TreePosition<EmbeddedMergeTree>? {
            var highest:TreePosition<EmbeddedMergeTree>? = null

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

        fun drawBlob(tree: EmbeddedMergeTree, blob: Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>, gradientInterval: Double, numberOfBlobs: Int) {
            val tree1 = (tree == visualization.tree1E)
            val deepestNodeInBlob = deepestNodeInBlob(blob);
            val highestNodeInBlob = highestNodeInBlob(blob)
            val deepestPos = deepestNodeInBlob
            //val highestBlobPos1 = if(highestNodeInBlob!!.parent != null) highestNodeInBlob.parent!!.pos else highestNodeInBlob.pos
            val blobs = if(tree1) visualization.tree1BlobsTest else visualization.tree2BlobsTest

            val highestBlobPos = visualization.highestPointInBlob(tree1, blobs, visualization.getBlobOfNode(blobs, highestNodeInBlob!!))


            //Lowest Hedge pos might not always be a leave
            //val pathPos = if (tree1) visualization.interleaving.f[highestNodeInBlob!!] else visualization.interleaving.g[highestNodeInBlob!!]
            //val pathNode = pathPos.firstDown

            //val pathID = if (tree1) visualization.getPathID(pathNode, visualization.tree2PathDecomposition) else visualization.getPathID(pathNode, visualization.tree1PathDecomposition)
            //val lowestPathNode = if (tree1) visualization.tree2PathDecomposition[pathID].first() else visualization.tree1PathDecomposition[pathID].first()
            //val lowestHedgePos = lowestPathNode.pos.y + visualization.interleaving.delta

            //val deepestPos = if (deepestNodeInBlob.leaves.isEmpty()) {


            drawer.apply {
                //strokeWeight = visualization.ds.markRadius / 3
                stroke = null

                if (visualization.globalcs.enableGradient)
                    visualization.colorGradiantValue(tree1, gradientInterval)
                else
                    fill = blob.third

                val leftLeaf = tree.leaves.first()
                val rightLeaf = tree.leaves.last()

                val startY = deepestPos!!.height
                //val midX = (leftLeaf.pos.x + rightLeaf.pos.x) / 2
                val width = abs(rightLeaf.pos.x - leftLeaf.pos.x) + (visualization.ds.blobRadius * 2)
                val height = abs(deepestPos.height - highestBlobPos.y)

                //strokeWeight = width + (visualization.ds.blobRadius * 2)
                //val pos = highestNodeInBlob.edgeContour!!.position(1.0)
                val isLeaf = highestNodeInBlob.firstDown.children.isEmpty()

                 //if (isLeaf) deepestPos.y else pos.y
                val leftX = leftLeaf.pos.x - visualization.ds.blobRadius

                var drawRectangles: MutableList<Shape> = mutableListOf()
                for (treePos in blob.first) {
                    val margin = if(treePos.firstDown.fullWidth) visualization.ds.blobRadius else visualization.ds.nonMappedRadius * visualization.ds.blobRadius


                    var leftTopY = highestBlobPos.y
                    var leftTopX = min(deepestNodeInBlob.firstDown.pos.x, treePos.firstDown.pos.x) - margin// visualization.ds.blobRadius

                    var rectWidth = abs(deepestNodeInBlob.firstDown.pos.x - treePos.firstDown.pos.x) + (margin * 2)// (visualization.ds.blobRadius * 2)
                    var rectHeight = treePos.height - highestBlobPos.y
                    //println("rectWidth: " + rectWidth)
                    //println("rectHeight: " + rectHeight)

                    if (rectHeight > 0){
                        drawRectangles.add(Rectangle(leftTopX, leftTopY, rectWidth, rectHeight).shape)
                    }
                }
                var drawRectangle = drawRectangles.first()

                for (rect in drawRectangles){
                    drawRectangle = drawRectangle.union(rect)
                }
                //var drawRectangle = union(drawRectangles, drawRectangles.first())
                //var drawRectangle = Rectangle(leftX, highestBlobPos.y,  width, height).shape

                //Draw hedge from root to root+delta
                if (highestNodeInBlob.firstUp == null){
                    val delta = visualization.interleaving.delta
                    val leftLeave = if (tree1) visualization.tree1E.leaves.first() else visualization.tree2E.leaves.first()
                    val leftMargin = if(leftLeave.fullWidth) visualization.ds.blobRadius else visualization.ds.nonMappedRadius * visualization.ds.blobRadius
                    val rightLeave = if (tree1) visualization.tree1E.leaves.last() else visualization.tree2E.leaves.last()
                    val rightMargin = if(rightLeave.fullWidth) visualization.ds.blobRadius else visualization.ds.nonMappedRadius * visualization.ds.blobRadius
                    val rootWidth = abs(leftLeave.pos.x - rightLeave.pos.x) + (leftMargin + rightMargin)// (visualization.ds.blobRadius * 2)
                    strokeWeight = rootWidth + (visualization.ds.blobRadius * 2)
                    val  rootMidX = (leftLeave.pos.x + rightLeave.pos.x) / 2

                    val topRect = Rectangle(leftLeave.pos.x - leftMargin, highestNodeInBlob.height - visualization.ds.blobRadius, rootWidth, visualization.ds.blobRadius).shape

                    drawRectangle = union(drawRectangle, topRect)
                }

                val lowestTreePositions: MutableList<TreePosition<EmbeddedMergeTree>> = mutableListOf()

                for (leave in tree.leaves){
                    if (false) {
                        if (!blob.first.contains(TreePosition(leave, 0.0)))
                            continue
                    }

                    val currentMaskHighY = tree.getDeepestLeave().pos.y + 1
                    val carveHeight = abs(leave.pos.y - currentMaskHighY)
                    val carveWidth = visualization.ds.nonMappedRadius * visualization.ds.blobRadius * 2// if (leave.fullWidth || !visualization.ds.collapseNonMapped) visualization.ds.blobRadius else visualization.tes.nodeWidth * visualization.ds.nonMappedRadius

                    val carveRect = Rectangle(leave.pos.x - carveWidth * 0.5, leave.pos.y- 0.01, carveWidth, carveHeight).shape

                    drawRectangle = drawRectangle.difference(carveRect)

                    val lowestTreePosInColumn = blob.first.filter { it.firstDown.pos.x == leave.pos.x }
                        .maxByOrNull { it.height }

                    if (lowestTreePosInColumn != null)
                        lowestTreePositions.add(lowestTreePosInColumn)
                }

                if (visualization.ds.carveInwards) {
                    for (i in lowestTreePositions.indices) {
                        if (i == lowestTreePositions.size - 1) break

                        val currentTreePos = lowestTreePositions[i]
                        val nextTreePos = lowestTreePositions[i + 1]

                        if (currentTreePos.height == nextTreePos.height) continue

                        val leftMargin = if(currentTreePos.firstDown.fullWidth) visualization.ds.blobRadius else visualization.ds.nonMappedRadius * visualization.ds.blobRadius
                        val rightMargin = if(nextTreePos.firstDown.fullWidth) visualization.ds.blobRadius else visualization.ds.nonMappedRadius * visualization.ds.blobRadius

                        val carveXTop = currentTreePos.firstDown.pos.x + leftMargin// visualization.ds.blobRadius
                        val carveYTop = min(currentTreePos.height, nextTreePos.height)
                        val carveWidth =
                            abs(currentTreePos.firstDown.pos.x - nextTreePos.firstDown.pos.x) - (leftMargin + rightMargin)// visualization.ds.blobRadius * 2
                        val carveHeight = abs(currentTreePos.height - nextTreePos.height)

                        if (carveWidth > 0 && carveHeight > 0) {
                            val carveRect = Rectangle(carveXTop, carveYTop, carveWidth, carveHeight).shape
                            drawRectangle = drawRectangle.difference(carveRect)
                        }
                    }
                }

                //Connectors
                val shortestContour = drawRectangle.contours.minBy {it.bounds.height }

                val connectors: MutableList<Shape> = mutableListOf()

                for (contour in drawRectangle.contours) {
                    if (contour == shortestContour) continue

                    val shortTestIsLeft = shortestContour.bounds.center.x < contour.bounds.center.x

                    var conTopX = 0.0
                    var conWidth = 0.0

                    var shortestSideSegment: Segment? = null
                    var contourSideSegment: Segment? = null

                    if (shortTestIsLeft) {
                        conTopX = shortestContour.bounds.x + shortestContour.bounds.width
                        conWidth = abs(contour.bounds.x - conTopX)

                        shortestSideSegment = shortestContour.segments.maxBy{ it.bounds.center.x }
                        contourSideSegment = contour.segments.minBy { it.bounds.center.x }

                    }
                    else {
                        conTopX = contour.bounds.x + contour.bounds.width
                        conWidth = abs(conTopX - shortestContour.bounds.x)

                        shortestSideSegment = shortestContour.segments.minBy{ it.bounds.center.x }
                        contourSideSegment = contour.segments.maxBy { it.bounds.center.x }

                    }

                    val conTopY = if (visualization.ds.connectorTop)
                        min(shortestSideSegment.bounds.y, contourSideSegment.bounds.y) else min(shortestSideSegment.bounds.center.y, contourSideSegment.bounds.center.y) - visualization.ds.connectorRadius * 0.5

                    val conHeight = visualization.ds.connectorRadius

                    if (conHeight > 0) {
                        val connector = Rectangle(conTopX, conTopY, conWidth, conHeight).shape
                        connectors.add(connector)
                    }
                }

                for (connector in connectors) {
                    drawRectangle = drawRectangle.union(connector)
                }



                val leavesLeftOfDeepest = mutableListOf<EmbeddedMergeTree>();
                val leavesRightOfDeepest = mutableListOf<EmbeddedMergeTree>();

                var highestCheck = if (highestNodeInBlob.firstUp == null) highestNodeInBlob else highestNodeInBlob.firstUp!!

//                while (highestCheck.parent != null) {
//                    highestCheck = highestCheck.parent!!
//                }

                for (leaf in tree.leaves) {
                    if (leaf.pos.x <= deepestNodeInBlob.firstDown.pos.x) {
                        leavesLeftOfDeepest.add(leaf)
                    }
                    else
                        leavesRightOfDeepest.add(leaf)
                }

                var currentMaskLeaf: EmbeddedMergeTree? = null
                var currentMaskHighY = tree.getDeepestLeave().pos.y + 1

                for (leaf in leavesLeftOfDeepest.reversed()){
                    if (!blob.first.contains(TreePosition(leaf, 0.0))){// && leaf.pos.y > highestBlobPos.y) { //Leaf is from another blob
                        //if (leaf.pos.y <= highestBlobPos.y) continue

                        //get the highest parent blob that is not part of this blob
                        var highest = leaf

                        var parentBlob = visualization.getAccurateParentBlob(tree1, blobs, visualization.getBlobOfNode(blobs, TreePosition(highest, 0.0)))

//                        while (parentBlob != -1 && blobs[parentBlob] != blob){ //!visualization.treePositionIsInBlob(tree1, blob, parentBlob))
////                            if (highest.parent!!.nodes().contains(highestNodeInBlob.firstDown)) {
////                                break
////                            }
//                            highest = highest.parent!! // if (tree1) visualization.tree1BlobsTest[parentBlob] else visualization.tree2BlobsTest[parentBlob]
//                            parentBlob = visualization.getAccurateParentBlob(tree1, blobs, visualization.getBlobOfNode(blobs, TreePosition(highest, 0.0)))
//
//                        }

                        while (highest.parent != null && !blob.first.contains(TreePosition(highest.parent!!, 0.0))) {

                            if (highest.parent!!.nodes().contains(highestNodeInBlob.firstDown)) {
                                break
                            }
                            highest = highest.parent!!
                        }

                        val highestOfCurrent = visualization.highestPointInBlob(tree1, blobs, visualization.getBlobOfNode(blobs, TreePosition(highest, 0.0))).y

                        if (currentMaskLeaf == null) {
                            currentMaskLeaf = highest
                            currentMaskHighY = highestOfCurrent
                        }
                        else {
                            if (highestOfCurrent < currentMaskHighY) {
                                currentMaskLeaf = highest
                                currentMaskHighY = highestOfCurrent
                            }
                        }
                    }

                    val xPos = leaf.pos.x - visualization.ds.blobRadius

                    //val isFromSameBlob = blob.first.contains(leaf)
                    //val highY = if(isFromSameBlob) leaf.pos.y else visualization.highestPointInBlob(tree1, blobs, visualization.getBlobOfNode(blobs, leaf)).y// currentMaskHighY// leaf.pos.y //if (isFromSameBlob) deepestNodeInBlob.pos.y else leaf.pos.y
                    val highY = currentMaskHighY// lowestHedgePos//currentMaskHighY//min(leaf.pos.y, currentMaskHighY)// leaf.pos.y //if (isFromSameBlob) deepestNodeInBlob.pos.y else leaf.pos.y
                    val lowY = tree.getDeepestLeave().pos.y + visualization.interleaving.delta + 10
                    val maskHeight = abs(highY - lowY)
                    val maskWidth = visualization.ds.blobRadius*2

                    if (maskHeight > 0) {
                        val mask = Rectangle(xPos, highY, maskWidth, maskHeight).shape
                        //drawRectangle = difference(drawRectangle, mask)
                    }

                }
                currentMaskLeaf = null
                currentMaskHighY = tree.getDeepestLeave().pos.y + 1



                for (leaf in leavesRightOfDeepest) {
                    if (!blob.first.contains(TreePosition(leaf, 0.0))){//  && leaf.pos.y > highestBlobPos.y) { //Leaf is from another blob

                        //TODO: GET MORE SOLID SOLUTION
                        //get the highest parent blob that is nog part of this blob
                        var highest = leaf
//                        while (highest.parent != null && !blob.first.contains(highest.parent)) {
//                            if (highest.parent!!.nodes().contains(highestNodeInBlob)) {
//                                break
//                            }
//
//                            highest = highest.parent!!
//                        }

                        val highestOfCurrent = visualization.highestPointInBlob(tree1, blobs, visualization.getBlobOfNode(blobs, TreePosition(leaf, 0.0))).y

                        if (currentMaskLeaf == null) {
                            currentMaskLeaf = leaf
                            currentMaskHighY = highestOfCurrent
                        }
                        else {
                            if (highestOfCurrent < currentMaskHighY) {
                                currentMaskLeaf = leaf
                                currentMaskHighY = highestOfCurrent
                            }
                        }
                    }

                    val xPos = leaf.pos.x - visualization.ds.blobRadius

                    val isFromSameBlob = blob.first.contains(TreePosition(leaf, 0.0))

                    val highY = currentMaskHighY// lowestHedgePos// currentMaskHighY// min(leaf.pos.y, currentMaskHighY)
                    //val highY = if(isFromSameBlob) leaf.pos.y else visualization.highestPointInBlob(tree1, blobs, visualization.getBlobOfNode(blobs, leaf)).y// currentMaskHighY// leaf.pos.y //if (isFromSameBlob) deepestNodeInBlob.pos.y else leaf.pos.y
                    val lowY = tree.getDeepestLeave().pos.y  + visualization.interleaving.delta + 10
                    val maskHeight = abs(highY - lowY)
                    val maskWidth = visualization.ds.blobRadius*2

                    if (maskHeight > 0) {
                        val mask = Rectangle(xPos, highY, maskWidth, maskHeight + 1).shape
                        //drawRectangle = difference(drawRectangle, mask)
                    }
                }

                fill = blob.third.mix(ColorRGBa.WHITE, visualization.ds.whiten)
                if (tree1)
                    shape(visualization.fromTree1Local(drawRectangle))
                else shape(visualization.fromTree2Local(drawRectangle))
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
            var values = alternatingSpacedValues(visualization.tree1BlobsTest.size)

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

        fun drawBlobPath(tree: EmbeddedMergeTree, blob: Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>, gradientInterval: Double, numberOfBlobs: Int) {
            val tree1 = (tree == visualization.tree1E)

            drawer.apply {
                fill = null
                strokeWeight = visualization.ds.verticalEdgeWidth

                stroke = if (visualization.globalcs.enableGradient)
                    visualization.colorGradiantValue(tree1, gradientInterval)
                else
                    blob.third

                //fill = blob.second

                val currentNode =
                    blob.first.maxByOrNull { it.height } //This is the deepest node in the blob (path is defined by that node.
                val pathNodes = if (tree1) visualization.tree2PathDecomposition[blob.second] else visualization.tree1PathDecomposition[blob.second]
                //if (lowestPathNode == null) return //return if we don't hit the other tree.
                if (pathNodes.isEmpty()) return
                val lowestPathPoint = if (visualization.interleaving.delta < 0.001) TreePosition(pathNodes.first(), 0.0) else TreePosition(pathNodes.first(), pathNodes.first().height -(currentNode!!.height - visualization.interleaving.delta))

                //if (treePositionToPoint(pathNodes.first() == null) return

                //val lowestPathPoint =
                //    if (tree1) visualization.interleaving.f.nodeMap[currentNode!!.firstDown] else visualization.interleaving.g.nodeMap[currentNode!!.firstDown]


                //Draw the lowest sub edge delta up from the leaf of the path
                val edge = lowestPathPoint.firstDown.edgeContour;
                if (edge == null) return
                val curveOffset = if (visualization.interleaving.delta < 0.001) 0.0 else edge!!.on(treePositionToPoint(lowestPathPoint)!!, .5);
                val subContour = edge.sub(0.0, curveOffset!!)
                val blackBottomMargin = visualization.ds.verticalEdgeWidth * (1- visualization.ds.verticalMappedRatio) / edge.length /2

                val backgroundSubContour = edge.sub(0.0, curveOffset!! + blackBottomMargin)// - )

                val drawContour = subContour

                var highestY = subContour.bounds.y

                val backgroundContours: MutableList<ShapeContour> = mutableListOf()
                val pathContours: MutableList<ShapeContour> = mutableListOf()

                backgroundContours.add(backgroundSubContour)
                pathContours.add(subContour)

                //draw rest of the path till the root node.
                var pathParent: EmbeddedMergeTree? = lowestPathPoint.firstUp;
                while (pathParent != null && pathNodes.contains(pathParent)) {
                    if (pathParent.edgeContour != null) {// && pathParent.pos.x == lowestPathPoint.firstDown.pos.x) {
                        highestY = pathParent.edgeContour!!.bounds.y
                        backgroundContours.add(pathParent.edgeContour!!)
                        pathContours.add(pathParent.edgeContour!!)
                    }

                    //Mapped paths
                    if (pathParent.edgeContour != null) {
                        if (tree1) {
                            stroke = blob.third
                            strokeWeight = visualization.ds.verticalEdgeWidth * visualization.ds.verticalMappedRatio
                            contour(visualization.fromTree2Local(pathParent.edgeContour!!))
                            drawContour.union(pathParent.edgeContour!!.shape)
                        }
                        else {
                            stroke = blob.third
                            strokeWeight = visualization.ds.verticalEdgeWidth * visualization.ds.verticalMappedRatio
                            contour(visualization.fromTree1Local(pathParent.edgeContour!!))
                        }
                    }
                    pathParent = pathParent.parent;
                }

                for (cont in backgroundContours) {
                    stroke = visualization.globalcs.edgeColor
                    strokeWeight = visualization.ds.verticalEdgeWidth

                    if (tree1) contour(visualization.fromTree2Local(cont))
                    else contour(visualization.fromTree1Local(cont))
                }

                //Draw Area Patch
                fill = blob.third
                strokeWeight = visualization.ds.verticalEdgeWidth * visualization.ds.patchStrokeScale
                stroke = if (tree1) visualization.globalcs.edgeColor else visualization.globalcs.edgeColor2

                val posY = if (pathParent != null) highestY else highestY - visualization.interleaving.delta - visualization.ds.blobRadius
                val posX = lowestPathPoint.firstDown.pos.x
                var pos = Vector2(posX, posY)
                pos = if (!tree1) visualization.fromTree1Local(pos) else visualization.fromTree2Local(pos)

                val rectWidth = visualization.ds.verticalEdgeWidth * visualization.ds.pathAreaPatchScale
                pos -= rectWidth / 2
                rectangle(pos, rectWidth)

                //Draw colored path
                for (cont in pathContours) {
                    stroke = blob.third
                    strokeWeight = visualization.ds.verticalEdgeWidth * visualization.ds.verticalMappedRatio

                    if (tree1) contour(visualization.fromTree2Local(cont))
                    else contour(visualization.fromTree1Local(cont))
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
                stroke = if (t1) visualization.globalcs.edgeColor else visualization.globalcs.edgeColor2

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

            //Draw Rays from root
            drawer.apply {
                fill = null

                //Draw Contour
                val pos1  = visualization.tree1E.pos
                val contour1 = LineSegment(pos1, Vector2(pos1.x, pos1.y - visualization.interleaving.delta - visualization.ds.blobRadius)).contour

                // Draw white casing
                stroke = visualization.globalcs.edgeColor
                strokeWeight = visualization.ds.verticalEdgeWidth
                contour(visualization.fromTree1Local(contour1))

                //Draw Contour
                val pos2  = visualization.tree2E.pos
                val contour2 = LineSegment(pos2, Vector2(pos2.x, pos2.y - visualization.interleaving.delta - visualization.ds.blobRadius)).contour

                // Draw white casing
                stroke = visualization.globalcs.edgeColor
                strokeWeight = visualization.ds.verticalEdgeWidth
                contour(visualization.fromTree2Local(contour2))
            }

            //Draw mapping of blob in the first tree onto the second tree
            for (blob in visualization.tree1BlobsTest) {
                drawBlobPath(visualization.tree1E, blob, t1values[blob.second], visualization.tree1BlobsTest.size)
                //drawPathSquares(false, visualization.tree2PathDecomposition[blob.second], t1values[blob.second], blob.third)
                count += 1
            }
//
//            count = 0
//            //Draw mapping of blob in the second tree onto the second tree
            for (blob in visualization.tree2BlobsTest) {
                drawBlobPath(visualization.tree2E, blob, t2values[blob.second], visualization.tree2BlobsTest.size)
                //drawPathSquares(true, visualization.tree1PathDecomposition[blob.second], t2values[blob.second], blob.third)// visualization.colorThreeValues(false, visualization.tree2BlobsTest.size)[blob.second])

                count+=1
            }

            //Draw Rays from root
            drawer.apply {
                stroke = ColorRGBa.BLACK
                fill = null
                strokeWeight = visualization.ds.verticalEdgeWidth*visualization.ds.verticalMappedRatio

                //Draw Contour
                val pos1  = visualization.tree1E.pos
                val contour1 = LineSegment(pos1, Vector2(pos1.x, pos1.y - visualization.interleaving.delta - visualization.ds.blobRadius)).contour

                //Set path Color
                val pathID1 = visualization.tree1BlobsTest.first().second
                stroke = if (visualization.globalcs.enableGradient) visualization.colorGradiantValue(false, t2values[pathID1]) // t2values[visualization.tree2Blobs.size-1])
                else visualization.tree2BlobsTest[0].third// visualization.colorThreeValues(false, visualization.tree2BlobsTest.size)[pathID1]
                contour(visualization.fromTree1Local(contour1))

                //Draw Contour
                val pos2  = visualization.tree2E.pos
                val contour2 = LineSegment(pos2, Vector2(pos2.x, pos2.y - visualization.interleaving.delta - visualization.ds.blobRadius)).contour

                //Set path Color
                val pathID2 = visualization.tree2BlobsTest.first().second
                stroke = if (visualization.globalcs.enableGradient) visualization.colorGradiantValue(true, t1values[pathID2])// t1values[visualization.tree1Blobs.size-1])
                else visualization.tree1BlobsTest[0].third//visualization.colorThreeValues(true, visualization.tree1BlobsTest.size)[pathID2]

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
                clear(visualization.ds.bgColor)
                visualization.globalcs.edgeColor2 = visualization.globalcs.edgeColor
                //drawBlobs();

//                var fillColor = ColorRGBa(0.0, 0.0, 0.0, visualization.ds.blacken)
//                fill = fillColor
//
//                rectangle(bounds)

                // Draw ray upward from roots
//                stroke = visualization.globalcs.edgeColor
//                val rootT1 = visualization.fromTree1Local(visualization.tree1E.pos)
//                strokeWeight = visualization.ds.verticalEdgeWidth
//                lineSegment(rootT1, Vector2(rootT1.x, (camera.view.inversed * Vector2(0.0, 0.01)).y))
//                val rootT2 = visualization.fromTree2Local(visualization.tree2E.pos)
//                lineSegment(rootT2, Vector2(rootT2.x, (camera.view.inversed * Vector2(0.0, 0.01)).y))

                //Draw tree
                composition(visualization.composition)

                //drawBlobPaths();

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