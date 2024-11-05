import org.openrndr.color.ColorRGBa
import org.openrndr.math.Vector2
import org.openrndr.shape.CompositionDrawer
import org.openrndr.shape.Rectangle
import org.openrndr.shape.Shape
import org.openrndr.shape.union
import kotlin.math.abs
import kotlin.math.min

class Hedge(
    val visualization: Visualization,
    val t1: Boolean,
    val treePositions: MutableList<TreePosition<EmbeddedMergeTree>>,
    val id: Int,
    val pathID: Int,
    var color: ColorRGBa = ColorRGBa.BLACK,
) {
    val treeMapping: TreeMapping<EmbeddedMergeTree> = if (t1) visualization.interleaving.f else visualization.interleaving.g
    val deepestNode = computeDeepestNode()
    val highestNode = computeHighestNode()
    val highestPoint = computeHighestPoint()
    val isRootHedge = highestNode.firstUp == null
    val accParentHedge = getAccurateParentHedge()
    val colors = if (t1) mutableListOf(visualization.tcs.t1c1, visualization.tcs.t1c2, visualization.tcs.t1c3) else mutableListOf(visualization.tcs.t2c1, visualization.tcs.t2c2, visualization.tcs.t2c3)
    val drawShape = computeShape()

    init {
        color = ColorRGBa.GRAY

        println("roothedge: " + isRootHedge)
        println("pathID: " + pathID)
    }

    fun draw(drawer: CompositionDrawer) {
        drawer.apply {
            stroke = null
            fill = color.mix(ColorRGBa.WHITE, visualization.ds.whiten)

            shape(drawShape)
        }
    }

    fun setColor() {
        if (accParentHedge == null) {
            color = colors.first()
            println("blobID: " + id)
            println("accurate parent = null")
            return
        }
        println("setting color")


        val parentColor = accParentHedge.color
        println("parentColor: $parentColor")

        color = getRemainderColor(mutableListOf(parentColor))
        //color = ColorRGBa.CYAN
    }

    private fun getRemainderColor(usedColors: MutableList<ColorRGBa>): ColorRGBa {
        var nonUsedColor = colors.firstOrNull { it !in usedColors }

        if (nonUsedColor == null)
            nonUsedColor = ColorRGBa.BLACK

        return nonUsedColor
    }

    private fun getParentHedge(): Hedge? {
        val parentNode = highestNode.firstUp  //returns null if parent = null -> means that blob contains the root node
        if (parentNode == null) {
            if (t1)
                return null
        }

        return visualization.getHedgeOfNode(t1, TreePosition(parentNode!!, 0.0))
    }

    private fun getAccurateParentHedge(): Hedge? {
        println("Accurate parent debug")
        println("blobID: " + id.toString())

        if (isRootHedge) return null

        val parentNode = highestNode.firstUp  //returns null if parent = null -> means that blob contains the root node

        if (parentNode == null) {
            println("parent node is null")
            return null
        }

        val h = abs(highestNode.firstDown.pos.y - highestPoint.y)
        val treePos = TreePosition(highestNode.firstDown, h + 1)

        val pathNode = treeMapping[treePos]

        val pathID = visualization.getPathID(pathNode.firstDown, if (t1) visualization.tree2PathDecomposition else visualization.tree1PathDecomposition)
        println("checked pathID: $pathID")
        var parentHedge: Hedge? = null

        for (hedge in if (t1) visualization.tree1Hedges else visualization.tree2Hedges) {
            if (pathID == hedge.pathID)
                parentHedge = visualization.getHedgeOfNode(t1, hedge.treePositions.first())
        }

        return parentHedge
    }

    private fun computeShape(): Shape{
        val hedgeBins: MutableList<Shape> = mutableListOf()

        for (treePos in treePositions){
            val leftTopY = highestPoint.y
            val leftTopX = min(deepestNode.firstDown.pos.x, treePos.firstDown.pos.x) - visualization.ds.blobRadius
            val width = abs(deepestNode.firstDown.pos.x - treePos.firstDown.pos.x) + (visualization.ds.blobRadius * 2)
            val height = treePos.height - highestPoint.y

            if (height > 0)
                hedgeBins.add(Rectangle(leftTopX, leftTopY, width, height).shape)
        }

        var mergedBins = hedgeBins.first()

        for (rect in hedgeBins){
            mergedBins = mergedBins.union(rect)
        }

        if (isRootHedge) {
            mergedBins = mergedBins.union(
                Rectangle(
                    mergedBins.bounds.x,
                    mergedBins.bounds.y - visualization.ds.blobRadius,
                    mergedBins.bounds.width,
                    visualization.ds.blobRadius
                ).shape
            )
        }
        return mergedBins
    }

    private fun computeDeepestNode(): TreePosition<EmbeddedMergeTree> {
        var deepest: TreePosition<EmbeddedMergeTree>? = null

        for (treePos in treePositions){
            if (deepest == null){
                deepest = treePos
                continue
            }
            if (treePos.height > deepest.height){
                deepest = treePos
            }
        }
        return deepest!!
    }

    private fun computeHighestNode(): TreePosition<EmbeddedMergeTree> {
        var highest: TreePosition<EmbeddedMergeTree>? = null

        for (treePos in treePositions) {
            if (treeMapping.nodeMap[treePos.firstDown] == null) continue
            if (treePos.heightDelta != 0.0) continue
            if (highest == null) highest = treePos
            if (treePos.height < highest.height) {
                highest = treePos
            }
        }
        return highest!!
    }

    private fun computeHighestPoint(): Vector2 {
        var highestPathNode = if (t1) visualization.tree2PathDecomposition[pathID].last() else visualization.tree1PathDecomposition[pathID].last()

        if (highestPathNode.parent != null) {
            highestPathNode = highestPathNode.parent!!

            return Vector2(highestNode.firstDown.pos.x, highestPathNode.pos.y + visualization.interleaving.delta)
        }
        return Vector2(highestNode.firstDown.pos.x, highestPathNode.pos.y)

//        val treePos = treeMapping[highestNode]
//        var parent = treePos.firstUp
//        val child = treePos.firstDown
//
//        if (parent != null &&  parent.pos.x == child.pos.x) {
//            while (parent?.parent != null) {
//                if (parent.pos.x != parent.parent!!.pos.x) {
//                    parent = parent.parent!!
//                    break
//                }
//                parent = parent.parent
//            }
//        }
//
//        if (parent != null) {
//            return Vector2(highestNode.firstDown.pos.x, parent.pos.y + visualization.interleaving.delta)
//        }
//        else return Vector2(highestNode.firstDown.pos.x, highestNode.firstDown.pos.y)
    }
}