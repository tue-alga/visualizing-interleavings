import org.openrndr.color.ColorHSVa
import org.openrndr.color.ColorRGBa
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.panel.elements.Div
import org.openrndr.shape.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.openrndr.draw.Drawer


class Visualization(
    val tree1: MergeTree,
    val tree2: MergeTree,
    val pos: Vector2,
    val tes: TreeEmbedSettings = TreeEmbedSettings(),
    val ds: DrawSettings = DrawSettings(),
    val globalcs: GlobalColorSettings = GlobalColorSettings(),
    var tcs: ThreeColorSettings = ThreeColorSettings(DivergingColorSettings()),
    val gcs: GradientColorSettings = GradientColorSettings(),
    val createInterleaving: (EmbeddedMergeTree, EmbeddedMergeTree) -> Interleaving<EmbeddedMergeTree>
) {
    lateinit var tree1E: EmbeddedMergeTree
    lateinit var tree2E: EmbeddedMergeTree
    lateinit var interleaving: Interleaving<EmbeddedMergeTree>
    lateinit var composition: Composition
    lateinit var nodeComposition: Composition

    //Blobs sorted from the deepest path to the highest path.
    private var tree1Blobs: MutableList<Pair<MutableList<EmbeddedMergeTree>, ColorRGBa>> = mutableListOf();
    private var tree2Blobs: MutableList<Pair<MutableList<EmbeddedMergeTree>, ColorRGBa>> = mutableListOf();
    var tree1BlobsTest: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>> =
        mutableListOf();
    var tree2BlobsTest: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>> =
        mutableListOf();
    var tree1BlobIndicesSorted: MutableList<Int> = mutableListOf()
    var tree2BlobIndicesSorted: MutableList<Int> = mutableListOf()
    private var tree1Colors: MutableList<ColorRGBa> = mutableListOf();
    private var tree2Colors: MutableList<ColorRGBa> = mutableListOf();
    private var nodes1ToColor: MutableList<EmbeddedMergeTree> = mutableListOf();
    private var nodes2ToColor: MutableList<EmbeddedMergeTree> = mutableListOf();

    //Hedge rewrite
    var tree1Hedges: MutableList<Hedge> = mutableListOf()
    var tree2Hedges: MutableList<Hedge> = mutableListOf()

    //TODO: Find path decomposition and use that to create blobs
    //Path decompositions: List of paths. Path is defined by a leaf and the highest node <leaf, highestnode>
    var tree1PathDecomposition: MutableList<MutableList<EmbeddedMergeTree>> = mutableListOf();
    var tree2PathDecomposition: MutableList<MutableList<EmbeddedMergeTree>> = mutableListOf();

    private lateinit var tree1EMatrix: Matrix44
    private lateinit var tree2EMatrix: Matrix44
    private val compBounds: Rectangle get() = composition.findShapes().map { it.effectiveShape.bounds }.bounds
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
        if (interleaving.g.inverseNodeEpsilonMap.keys.first().parent == null) {
            interleaving.g.inverseNodeEpsilonMap.keys.first().setID(0)
        }
        if (interleaving.f.inverseNodeEpsilonMap.keys.first().parent == null) {
            interleaving.f.inverseNodeEpsilonMap.keys.first().setID(0)
        }
        tree1Colors.clear()
        tree2Colors.clear()
        tree1Colors.add(tcs.t1c1)
        tree1Colors.add(tcs.t1c2)
        tree1Colors.add(tcs.t1c3)
        tree2Colors.add(tcs.t2c1)
        tree2Colors.add(tcs.t2c2)
        tree2Colors.add(tcs.t2c3)

        pathDecomposition(true)
        pathDecomposition(false)

        repositionNodes(true, tree1E)
        repositionNodes(false, tree2E)

        if (ds.collapseNonMapped) {
            setNodeWidths()
            reduceNonMappedSpacing(true, tree1E)
            reduceNonMappedSpacing(false, tree2E)
            repositionNodes(true, tree1E)
            repositionNodes(false, tree2E)
        }

        treePairComposition()
//        hedgeComposition(true)
//        hedgeComposition(false)
    }

    private fun pathDecomposition(t1: Boolean) {
        val tree = if (t1) interleaving.g else interleaving.f

        if (t1) tree1PathDecomposition.clear() else tree2PathDecomposition.clear()

        for (path in tree.pathDecomposition) {
            if (t1) {
                tree1PathDecomposition.add(path)
            } else {
                tree2PathDecomposition.add(path)
            }
        }
    }

    fun getPathID(t: EmbeddedMergeTree, paths: MutableList<MutableList<EmbeddedMergeTree>>): Int {
        for (i in paths.indices) {
            for (node in paths[i]) {
                if (node.id == t.id) return i
            }
        }
        return -1
    }

    private fun setHedgeColors() {
        tree1Hedges.sortBy { it.highestPoint.y }
        for (hedge in tree1Hedges) {
            hedge.setColor()
        }
        tree2Hedges.sortBy { it.highestPoint.y }
        for (hedge in tree2Hedges) {
            hedge.setColor()
        }
    }

    private fun setNodeWidths() {
        for (node in tree1E.nodes()) {
            node.fullWidth = nodeIsInMappedColumn(true, node)
        }
        for (node in tree2E.nodes()) {
            node.fullWidth = nodeIsInMappedColumn(false, node)
        }
    }

    private fun repositionNodes(t1: Boolean, t: EmbeddedMergeTree) {
        if (t.children.isEmpty()) return //Don't reposition leaves

        for (c in t.children) {
            repositionNodes(t1, c)
        }

        val childInPath = getChildInSamePath(t1, t)

        if (childInPath != null && t.id != childInPath.id) {
            t.pos = Vector2(childInPath.pos.x, t.pos.y)
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

    private fun nodeIsInMappedColumn(t1: Boolean, node: EmbeddedMergeTree): Boolean {
        val xPos = node.pos.x
        var current = node

        var isInMappedColumn = false

        if (current.parent == null) return true

        val pathID = getPathID(current, if (t1) tree1PathDecomposition else tree2PathDecomposition)
        if (pathID != -1) return true

        if (pathID == -1 && current.parent!!.pos.x != xPos) {
            return false
        }

        while (current.pos.x == current.parent!!.pos.x) {
            if (getPathID(current.parent!!, if (t1) tree1PathDecomposition else tree2PathDecomposition) != -1) {
                isInMappedColumn = true
                break
            }

            current = current.parent!!
            //if (current.parent == null) return true
        }

        return isInMappedColumn
    }

    private fun reduceNonMappedSpacing(t1: Boolean, t: EmbeddedMergeTree) {
        val spaceReduction = abs(tes.nodeWidth * ds.nonMappedRadius - tes.nodeWidth)// / 2

        //0.25
        //8
        // 2  - 8 = 6 / 2 = 3

        // 1 -> 9
        // 1 -> 9-3 = 6


        for (i in t.leaves.indices) {
            var inMappedColumn = t.leaves[i].fullWidth// nodeIsInMappedColumn(t1, t.leaves[i])

            //t.leaves[i].fullWidth = false

            var rightSpaceReduction = spaceReduction

            if (inMappedColumn) rightSpaceReduction -= spaceReduction / 2

//            if (i == 0)
//                leftSpaceReduction = 0.0
//            else
//                if (t.leaves[i - 1].fullWidth)
//                    leftSpaceReduction = spaceReduction / 2


            if (i < t.leaves.size - 1)
                if (t.leaves[i + 1].fullWidth)
                    rightSpaceReduction -= spaceReduction / 2

            //t.leaves[i].pos = Vector2(t.leaves[i].pos.x - leftSpaceReduction, t.leaves[i].pos.y)
            for (j in i + 1 until t.leaves.size) {
                //if (!leaveIsInMappedColumn(t1, t.leaves[j]))
                t.leaves[j].pos = Vector2(t.leaves[j].pos.x - rightSpaceReduction, t.leaves[j].pos.y)

            }

        }
    }

    private fun getChildInSamePath(t1: Boolean, t: EmbeddedMergeTree): EmbeddedMergeTree? {
        if (t.children.isEmpty()) return null

        val pathID = if (t1) getPathID(t, tree1PathDecomposition) else getPathID(t, tree2PathDecomposition)

        for (c in t.children) {
            val childPathID = if (t1) getPathID(c, tree1PathDecomposition) else getPathID(c, tree2PathDecomposition)
            if (pathID == childPathID) {
                return c
            }
        }

        return t.children[t.children.size / 2]

        //return null
    }

    private fun highestNodeInBlob(
        blobs: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>>,
        blobID: Int
    ): TreePosition<EmbeddedMergeTree> {
        var highest: TreePosition<EmbeddedMergeTree>? = null// blobs[blobID].first.last()

        for (node in blobs[blobID].first) {
            if (interleaving.f.nodeMap[node.firstDown] == null && interleaving.g.nodeMap[node.firstDown] == null) continue
            if (node.heightDelta != 0.0) continue
            if (highest == null) highest = node
            if (node.height < highest.height) {
                highest = node
            }
        }
        return highest!!
    }

    private fun highestTreeposInBlob(
        blobs: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>>,
        blobID: Int
    ): TreePosition<EmbeddedMergeTree> {
        var highest = blobs[blobID].first.first()

        for (node in blobs[blobID].first) {
            if (node.firstDown.parent == null) return node
            if (node.height < highest.height) {
                highest = node
            }
        }
        return highest
    }

    fun highestPointInBlob(
        t1: Boolean,
        blobs: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>>,
        blobID: Int
    ): Vector2 {
        val highestNode = highestNodeInBlob(blobs, blobID)

        val treePos = if (t1) interleaving.f[highestNode] else interleaving.g[highestNode]
        var parent = treePos.firstUp
        val child = treePos.firstDown

        if (parent != null && parent.pos.x == child.pos.x) {
            while (parent?.parent != null) {
                if (parent.pos.x != parent.parent!!.pos.x) {
                    parent = parent.parent!!
                    break
                }

                parent = parent.parent

            }
        }

        if (parent != null) {
            //val paths = if (t1) tree2PathDecomposition else tree1PathDecomposition
            //val pathID = getPathID(parent, paths)
            //val highestInPath = paths[pathID].last()
            //parent?.pos?.y ?: (path.last().pos.y - visualization.interleaving.delta - visualization.ds.blobRadius)

            return Vector2(highestNode.firstDown.pos.x, parent.pos.y + interleaving.delta)
        } else return Vector2(highestNode.firstDown.pos.x, highestNode.firstDown.pos.y)
    }

    private fun getLeavesInBlob(
        blobs: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>>,
        blobsID: Int
    ): MutableList<TreePosition<EmbeddedMergeTree>> {
        val leavesInBlob = mutableListOf<TreePosition<EmbeddedMergeTree>>()

        for (node in blobs[blobsID].first) {
            if (node.firstDown.children.isEmpty()) {
                leavesInBlob.add(node)
            }
        }
        return leavesInBlob
    }

    fun getBlobOfNode(
        blobs: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>>,
        node: TreePosition<EmbeddedMergeTree>
    ): Int {
        for (i in blobs.indices) {
            if (blobs[i].first.contains(node)) {
                return i
            }
        }
        return -1
    }

    fun getHedgeOfNode(t1: Boolean, node: TreePosition<EmbeddedMergeTree>): Hedge? {
        val hedges = if (t1) tree1Hedges else tree2Hedges

        for (hedge in hedges) {
            if (hedge.treePositions.contains(node)) {
                return hedge
            }
        }
        return null
    }

    private fun getParentBlob(
        t1: Boolean,
        blobs: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>>,
        blobID: Int
    ): Int {
        val parentNode = highestNodeInBlob(
            blobs,
            blobID
        ).firstUp  //returns null if parent = null -> means that blob contains the root node
        if (parentNode == null) {
            //if (t1) println("FOUND THE ROOT")
            return -1
        }


        return getBlobOfNode(blobs, TreePosition(parentNode, 0.0))
    }

    fun getAccurateParentBlob(
        t1: Boolean,
        blobs: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>>,
        blobID: Int
    ): Int {
        val parentNode = highestNodeInBlob(
            blobs,
            blobID
        ).firstUp  //returns null if parent = null -> means that blob contains the root node

        if (parentNode == null) {
            return -1
        }

        val highestPos = highestPointInBlob(t1, blobs, blobID)
        //println("=================")
        //println("highestPos: " + highestPos)
        val highestNode = highestNodeInBlob(blobs, blobID)
        val h = abs(highestNode.firstDown.pos.y - highestPos.y)

        val treePos = TreePosition(highestNode!!.firstDown, h + .1)// highestPos.y - 50)

        val pathNode = if (t1) interleaving.f[treePos] else interleaving.g[treePos]

        val pathID = getPathID(pathNode.firstDown, if (t1) tree2PathDecomposition else tree1PathDecomposition)

        var parentBlob = -1

        for (blob in blobs) {
            if (pathID == blob.second)
                parentBlob = getBlobOfNode(blobs, blob.first.first())
        }

        return parentBlob

        //return getBlobOfNode(blobs, parentNode)
    }

    fun treePositionIsInBlob(
        t1: Boolean,
        blob: Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>,
        treePos: TreePosition<EmbeddedMergeTree>
    ): Boolean {
        val pathID = if (t1) interleaving.f.getPathID(interleaving.f[treePos].firstDown) else interleaving.g.getPathID(
            interleaving.g[treePos].firstDown
        )

        return pathID == blob.second
    }

    //TODO: use same logic as 'getAccurateParentBlob'
    private fun getChildBlobsOfNode(
        blobs: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>>,
        node: TreePosition<EmbeddedMergeTree>
    ): MutableList<Int> {
        val childrenNotInBlob: MutableList<Int> = mutableListOf()
        val blobID = getBlobOfNode(blobs, node)

        for (child in node.firstDown.children) {
            if (blobs[blobID].first.contains(TreePosition(child, 0.0))) continue

            childrenNotInBlob.add(getBlobOfNode(blobs, TreePosition(child, 0.0)))
        }
        return childrenNotInBlob
    }

    private fun getHighestLeftChildBlob(
        blobs: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>>,
        blobID: Int
    ): Int {
        //val childBlobs = getChildBlobsOfNode(blobs, highestNodeInBlob(blobs, blobID))
        val blobsCopy = blobs[blobID].first
        blobsCopy.sortBy { it.height }
        for (node in blobsCopy) {
            for (child in node.firstDown.children) {
                if (child.pos.x >= node.firstDown.pos.x) continue

                if (!blobsCopy.contains(TreePosition(child, 0.0))) {
                    return getBlobOfNode(blobs, TreePosition(child, 0.0))
                }
            }
        }

        return -1
    }

    private fun getColorOrder(
        firstColor: ColorRGBa,
        excludeColor: ColorRGBa,
        colors: MutableList<ColorRGBa>
    ): MutableList<ColorRGBa> {
        val outPutColors: MutableList<ColorRGBa> = mutableListOf()
        outPutColors.add(firstColor)

        for (i in colors.indices) {
            if (firstColor != colors[i] && excludeColor != colors[i]) {
                outPutColors.add(colors[i])
            }
        }
        return outPutColors
    }

    private fun lowBlobsTouch(
        t1: Boolean,
        blobs: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>>,
        otherIsLeft: Boolean,
        currentBlobID: Int,
        otherLeave: EmbeddedMergeTree,
//        leftBlobID: Int,
//        leftLeave: EmbeddedMergeTree,
//        rightBlobID: Int,
//        rightleave: EmbeddedMergeTree
    ): Boolean {
        val thisLeaveHighestPos = highestPointInBlob(t1, blobs, currentBlobID)
        val otherHighestPos = highestPointInBlob(t1, blobs, getBlobOfNode(blobs, TreePosition(otherLeave, 0.0)))

        val thisLeave = if (otherIsLeft) TreePosition(blobs[currentBlobID].first.minBy { it.firstDown.pos.x }.firstDown.leaves.first(), 0.0) else
            TreePosition(blobs[currentBlobID].first.maxBy { it.firstDown.pos.x }.firstDown.leaves.last(), 0.0)

        return thisLeaveHighestPos.y < otherLeave.pos.y && otherHighestPos.y < thisLeave.firstDown.pos.y
    }

    private fun touchLowBlobLeft(
        t1: Boolean,
        blobs: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>>,
        currentBounds: Pair<Double, Double>,
        leftBlobID: Int
    ): Boolean {
        val rightLeave =
            highestNodeInBlob(blobs, leftBlobID)!!.firstDown.leaves.last()// getLeavesInBlob(blobs, rightBlobID).last()
        val rightHighestPos = highestPointInBlob(t1, blobs, leftBlobID)

        return currentBounds.second > rightLeave.pos.y && currentBounds.first < rightHighestPos.y
    }

    private fun highBlobsTouch(
        blobs: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>>,
        lowNodeID: Int,
        highNodeID: Int,
        highSideID: Int,
        highRight: Boolean
    ): Boolean {
        //lowNodeID = current
        //HighNodeID = parent of current
        //HighSideID = first parent to the right parent of highNode
        val lowNodeIDTop = highestNodeInBlob(blobs, lowNodeID)
        val highNodeIDTop = highestNodeInBlob(blobs, highNodeID)

        if (highRight && lowNodeIDTop!!.firstDown.pos.x > highNodeIDTop!!.firstDown.pos.x) return false
        if (!highRight && lowNodeIDTop!!.firstDown.pos.x < highNodeIDTop!!.firstDown.pos.x) return false

        if (highNodeID != highSideID) return false

        return true
    }

    private fun getBlobsTouchingLeft(
        t1: Boolean,
        blobs: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>>,
        blobID: Int,
        leftBlobID: Int
    ): MutableList<Int> {
        val touchingBlobs = mutableListOf<Int>()

        val tree = if (t1) tree1E else tree2E
        val leftMostLeave = blobs[blobID].first.minBy { it.firstDown.pos.x }.firstDown.leaves.first()
//        val leftMostLeave =
//            highestNodeInBlob(blobs, blobID)!!.firstDown.leaves.first()// getLeavesInBlob(blobs, blobID).first() //TODO: make work for non-connected blobs
        val id = tree.leaves.indexOf(leftMostLeave)

        if (id != 0) {
            val lowestTouching = tree.leaves[id - 1]
            val lowestBlob = getBlobOfNode(blobs, TreePosition(lowestTouching, 0.0))

//            if (!t1 && highestNodeInBlob(blobs, lowestBlob).firstUp == null && id == 1) {
//                println("checking leave of root blob")
//            }

            if (lowBlobsTouch(
                    t1,
                    blobs,
                    true,
                    blobID,
                    lowestTouching
                )
            ) {//  (touchLowBlobLeft(t1, blobs, bounds, lowestBlob)) { //    (lowBlobsTouch(t1, blobs, lowestBlob, blobID)) {
                touchingBlobs.add(lowestBlob)

                if (highestPointInBlob(t1, blobs, blobID).y >= highestPointInBlob(t1, blobs, lowestBlob).y) {
                    return touchingBlobs
                }
            }
            else if (!t1 && id == 1) {
                print("no touching")
            }

            var parent = getAccurateParentBlob(t1, blobs, lowestBlob)
//            touchingBlobs.add(parent)
//
//            parent = getParentBlob(blobs, parent)

            while (parent != -1) {
                if (highBlobsTouch(blobs, blobID, parent, leftBlobID, false))
                    touchingBlobs.add(parent)
                else break

                parent = getParentBlob(t1, blobs, parent)
            }
        }

        return touchingBlobs
    }

    private fun getBlobsTouchingRight(
        t1: Boolean,
        blobs: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>>,
        blobID: Int,
        rightBlobID: Int
    ): MutableList<Int> {
        val touchingBlobs = mutableListOf<Int>()
        val tree = if (t1) tree1E else tree2E
        val rightMostLeave = blobs[blobID].first.maxBy { it.firstDown.pos.x }.firstDown.leaves.last()

//        val rightMostLeave =
//            highestNodeInBlob(blobs, blobID)!!.firstDown.leaves.last() //getLeavesInBlob(blobs, blobID).last()

        val id = tree.leaves.indexOf(rightMostLeave)

        if (id != tree.leaves.size - 1) {
            val lowestTouching = tree.leaves[id + 1]
            val lowestBlob = getBlobOfNode(blobs, TreePosition(lowestTouching, 0.0))

            var parent = getParentBlob(t1, blobs, lowestBlob)

            if (parent == -1) {
                touchingBlobs.add(lowestBlob)
            }

            if (lowBlobsTouch(t1, blobs, false, blobID, lowestTouching)) {
                touchingBlobs.add(lowestBlob)
                if (highestPointInBlob(t1, blobs, blobID).y >= highestPointInBlob(t1, blobs, lowestBlob).y) {
                    return touchingBlobs
                }
            }

//            touchingBlobs.add(parent)
//
//            parent = getParentBlob(blobs, parent)

            while (parent != -1) {
                if (highBlobsTouch(blobs, blobID, parent, rightBlobID, true))
                    touchingBlobs.add(parent)
                else break

                parent = getParentBlob(t1, blobs, parent)
            }
        }
        return touchingBlobs
    }


    //return neighbouring colors that are not a child of this blob
    private fun getTouchingColors(
        t1: Boolean,
        blobs: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>>,
        blobID: Int,
        leftBlobID: Int,
        rightBlobID: Int,
        leftMost: Boolean = false,
        rightMost: Boolean = false
    )
            : MutableList<ColorRGBa> {
        val touchingColors = mutableListOf<ColorRGBa>()

        //return empty list if blob is root, since all other blobs are children
        //println("getting touchColor: pathID: " + blobs[blobID].second)
//        val parentBlobID = getAccurateParentBlob(
//            t1,
//            blobs,
//            blobID
//        )// getAccurateParentBlob(t1, blobs, blobID) //TODO: get hedge parent, not node parent.
//        //val parentID = path
//        if (parentBlobID == -1) return mutableListOf()
        //You always touch your parent blob

//        if (blobs[parentBlobID].third == ColorRGBa.BLACK) {
//            setBlobColors(t1, parentBlobID, ColorRGBa.GREEN)
//        }

//        if (parentBlobID != -1 && blobs[parentBlobID].third == ColorRGBa.BLACK) {
//
//            val c = if (t1) listOf(tcs.t1c1, tcs.t1c2, tcs.t1c3) else listOf(tcs.t2c1, tcs.t2c2, tcs.t2c3)
//            //println("RANDOM")
//            touchingColors.add(c.random())
//        }
        //val id = if (parentBlobID == -1) 0 else parentBlobID
        //val parentColor = blobs[parentBlobID].third
        val parentBlobID = blobID
        val parentColor = blobs[parentBlobID].third

        if (!t1) {
            println("----------------------------------------")
            println("blob colors")
            println("blobID: $blobID")
            println("highest blob pos: " + highestPointInBlob(t1, blobs, blobID))
            println("blobColor: ${blobs[blobID].third}")
            println("parentID: $parentBlobID")
            println("highest parent pos " + highestPointInBlob(t1, blobs, parentBlobID))
            println("parentColor: $parentColor")
        }

        touchingColors.add(parentColor)

        if (leftMost && leftBlobID != -1) {
            val touchingBlobIDs = getBlobsTouchingLeft(t1, blobs, blobID, leftBlobID)

            for (id in touchingBlobIDs) {
                if (blobs[id].third != ColorRGBa.BLACK) {
                    touchingColors.add(blobs[id].third)
                }
            }
        }
        if (rightMost && rightBlobID != -1) {
            val touchingBlobIDs = getBlobsTouchingRight(t1, blobs, blobID, rightBlobID)

            for (id in touchingBlobIDs) {
                if (blobs[id].third != ColorRGBa.BLACK) {
                    touchingColors.add(blobs[id].third)
                }
            }
        }
        return touchingColors
    }

    private fun getLeftRightParentBlobID(
        t1: Boolean,
        blobs: MutableList<Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>>,
        node: TreePosition<EmbeddedMergeTree>
    ): Pair<Int, Int> {
        var parent = node.firstUp
        if (parent == null) return Pair(-1, -1) //No neighboring parent to the root node

        var leftID = -1
        var rightID = -1

        val currentBlob = getBlobOfNode(blobs, node)
        val leaves = highestNodeInBlob(blobs, currentBlob)!!.firstDown.leaves()// getLeavesInBlob(blobs, currentBlob)

        val tree = if (t1) tree1E else tree2E
        val idLeft = tree.leaves.indexOf(leaves.first())
        val idRight = tree.leaves.indexOf(leaves.last())

        if (idRight != tree.leaves.size - 1) {
            val rightLeave = getBlobOfNode(blobs, TreePosition(tree.leaves[idRight + 1], 0.0))
            rightID = getParentBlob(t1, blobs, rightLeave)

            if (rightID == -1)
                rightID = rightLeave
        }

        if (idLeft > 0 && idLeft < tree.leaves.size - 1) {
            val leftLeave = getBlobOfNode(blobs, TreePosition(tree.leaves[idLeft - 1], 0.0))
            leftID = getParentBlob(t1, blobs, leftLeave)

            if (leftID == -1)
                leftID = leftLeave
        }

        if (leftID == currentBlob) leftID = -1
        if (rightID == currentBlob) rightID = -1

        return Pair(leftID, rightID)
    }

    private fun setBlobColorsTest(t1: Boolean) {
        val blobsIndices = if (t1) tree1BlobIndicesSorted else tree2BlobIndicesSorted
        val blobs = if (t1) tree1BlobsTest else tree2BlobsTest
        val colors = if (t1) tree1Colors else tree2Colors

        for (blobID in blobsIndices) {
            //Root blob
            val parentBlobID = getAccurateParentBlob(t1, blobs, blobID)
            if (parentBlobID == -1) {
                blobs[blobID] = Triple(blobs[blobID].first, blobs[blobID].second, colors.first())
                continue
            }
            //non-root blobs
            val leaves = getLeavesInBlob(blobs, blobID)
            val leaveXpos = leaves.first()!!.firstDown.pos.x

            val leftOfParent = (leaveXpos < deepestNodeInBlob(blobs[parentBlobID])!!.firstDown.pos.x)

            var touchingColors: MutableList<ColorRGBa> = mutableListOf()
            var touchingBlobs: MutableList<Int>

            val parentNeighbours =
                getLeftRightParentBlobID(t1, blobs, getLeavesInBlob(blobs, blobID).first())

            if (leftOfParent)
                touchingBlobs = getBlobsTouchingLeft(t1, blobs, blobID, parentNeighbours.first)//getTouchingColors(t1, blobs, parentBlobID, blobID, -1, leftMost = true)
            else
                touchingBlobs = getBlobsTouchingRight(t1, blobs, blobID, parentNeighbours.second)// getTouchingColors(t1, blobs, parentBlobID, -1, blobID, rightMost = true)


            for (blob in touchingBlobs)
                touchingColors.add(blobs[blob].third)
            touchingColors.add(blobs[parentBlobID].third)

            val parentOfParent = getAccurateParentBlob(t1, blobs, parentBlobID)

            touchingColors = touchingColors.apply { removeAll{ it == ColorRGBa.BLACK } }
            if (touchingColors.distinct().size < 2 && parentOfParent != -1){
                println("yaaaaaaaaa")
                touchingColors.add(blobs[parentOfParent].third)
            }

            var color = ColorRGBa.BLACK
            for (c in colors) {
                if (!touchingColors.contains(c)) {
                    color = c
                    break
                }
            }
            blobs[blobID] = Triple(blobs[blobID].first, blobs[blobID].second, color)
        }
    }

    private fun setBlobColors(t1: Boolean, blobID: Int, color: ColorRGBa) {
        val blobs = if (t1) tree1BlobsTest else tree2BlobsTest
        val colors = if (t1) tree1Colors else tree2Colors

        val parentBlobID = getAccurateParentBlob(t1, blobs, blobID)
        if (parentBlobID == -1) blobs[blobID] = Triple(blobs[blobID].first, blobs[blobID].second, color)

        var index = 0
        while (if (t1) nodes1ToColor.isNotEmpty() else nodes2ToColor.isNotEmpty()) {
            val currentNode = if (t1) nodes1ToColor.first() else nodes2ToColor.first()
            val currentBlob = getBlobOfNode(blobs, TreePosition(currentNode, 0.0))
            val currentColor = blobs[currentBlob].third

            //For all highest children in blob
            val highestChildBlobIDs = getChildBlobsOfNode(blobs, TreePosition(currentNode, 0.0))

            val leaves = getLeavesInBlob(blobs, currentBlob)
            val leaveXpos = leaves.first()!!.firstDown.pos.x

            val leftChildBlobIDs = mutableListOf<Int>()
            val rightChildBlobIDs = mutableListOf<Int>()

            for (childID in highestChildBlobIDs) {
                val childLeaveXpos = getLeavesInBlob(blobs, childID).first()!!.firstDown.pos.x
                if (childLeaveXpos < leaveXpos) {
                    leftChildBlobIDs.add(childID)
                } else rightChildBlobIDs.add(childID)
            }

            if (leftChildBlobIDs.isNotEmpty()) {
                val parentNeighbours =
                    getLeftRightParentBlobID(t1, blobs, getLeavesInBlob(blobs, leftChildBlobIDs.first()).first())

                val leftTouchingColors =
                    getTouchingColors(
                        t1,
                        blobs,
                        leftChildBlobIDs.first(),
                        parentNeighbours.first,
                        parentNeighbours.second,
                        leftMost = true
                    )

                var leftColor = ColorRGBa.BLACK
                for (c in colors) {
                    if (!leftTouchingColors.contains(c)) {
                        leftColor = c
                        break
                    }
                }

                val leftColorOrder = getColorOrder(leftColor, currentColor, colors)

                for (i in leftChildBlobIDs.indices) {
                    val childColor = leftColorOrder[i % leftColorOrder.size]
                    blobs[leftChildBlobIDs[i]] =
                        Triple(blobs[leftChildBlobIDs[i]].first, blobs[leftChildBlobIDs[i]].second, childColor)
                }
            }

            if (rightChildBlobIDs.isNotEmpty()) {
                //If I have a node, simply look at the parent and then at the right sibling
                val parentNeighbours =
                    getLeftRightParentBlobID(t1, blobs, getLeavesInBlob(blobs, rightChildBlobIDs.last()).first())
                val rightTouchingColors =
                    getTouchingColors(
                        t1,
                        blobs,
                        rightChildBlobIDs.last(),
                        parentNeighbours.first,
                        parentNeighbours.second,
                        rightMost = true
                    )

                var rightColor = ColorRGBa.BLACK
                for (c in colors) {
                    if (!rightTouchingColors.contains(c)) {
                        rightColor = c
                        break
                    }
                }
                val rightColorOrder = getColorOrder(rightColor, currentColor, colors)

                for (i in rightChildBlobIDs.indices) {
                    val childColor = rightColorOrder[i % rightColorOrder.size]
                    blobs[rightChildBlobIDs[i]] =
                        Triple(blobs[rightChildBlobIDs[i]].first, blobs[rightChildBlobIDs[i]].second, childColor)
                }
            }
            if (t1) nodes1ToColor.remove(currentNode) else nodes2ToColor.remove(currentNode)
            index += 1
        }
    }

    fun colorGradiantValue(t1: Boolean, t: Double): ColorRGBa {
        val startColor = if (t1) gcs.t1c1 else gcs.t2c1;
        val endColor = if (t1) gcs.t1c2 else gcs.t2c2;

        when (gcs.colorInterpolation) {
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


    fun colorThreeValues(t1: Boolean, size: Int): List<ColorRGBa> {
        val c1 = if (t1) tcs.t1c1 else tcs.t2c1;
        val c2 = if (t1) tcs.t1c2 else tcs.t2c2;
        val c3 = if (t1) tcs.t1c3 else tcs.t2c3;

        //val values = if (t1) listOf(c3, c3, c1, c3, c2, c3) else listOf(c1, c2, c3, c2, c3, c2)
        val values = if (t1) listOf(c1, c2, c3) else listOf(c1, c2, c3)

        return List(size) { index -> values[index % values.size] }

    }

    fun deepestNodeInBlob(blob: Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>): TreePosition<EmbeddedMergeTree>? {
        var deepest: TreePosition<EmbeddedMergeTree>? = null

        for (node in blob.first) {
            if (deepest == null) {
                deepest = node
                continue
            }
            if (node.height > deepest.height) {
                deepest = node
            }
        }
        return deepest
    }

    fun highestNodeInBlob(blob: Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>): TreePosition<EmbeddedMergeTree>? {
        var highest: TreePosition<EmbeddedMergeTree>? = null

        for (node in blob.first) {
            if (highest == null) {
                highest = node
                continue
            }
            if (node.height < highest.height) {
                highest = node
            }
        }
        return highest
    }

    fun drawBlob(
        drawer: CompositionDrawer,
        tree: EmbeddedMergeTree,
        blob: Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>,
        numberOfBlobs: Int
    ) {
        val tree1 = (tree == tree1E)
        val deepestNodeInBlob = deepestNodeInBlob(blob);
        val highestNodeInBlob = highestNodeInBlob(blob)
        val blobs = if (tree1) tree1BlobsTest else tree2BlobsTest

        val highestBlobPos = highestPointInBlob(tree1, blobs, getBlobOfNode(blobs, highestNodeInBlob!!))

        drawer.apply {
            stroke = null
            fill = blob.third

            val leftLeaf = tree.leaves.first()
            val rightLeaf = tree.leaves.last()

            val drawRectangles: MutableList<Shape> = mutableListOf()
            for (treePos in blob.first) {
                val margin = if (treePos.firstDown.fullWidth) ds.blobRadius else ds.nonMappedRadius * ds.blobRadius
                val secondMargin = if (deepestNodeInBlob!!.firstDown.fullWidth) ds.blobRadius else ds.nonMappedRadius * ds.blobRadius

                val leftMargin = if (treePos.firstDown.pos.x < deepestNodeInBlob!!.firstDown.pos.x) margin else secondMargin

                val leftTopY = highestBlobPos.y
                val leftTopX = min(
                    deepestNodeInBlob!!.firstDown.pos.x,
                    treePos.firstDown.pos.x
                ) - leftMargin// visualization.ds.blobRadius
                val rectWidth =
                    abs(deepestNodeInBlob.firstDown.pos.x - treePos.firstDown.pos.x) + (margin + secondMargin)// (visualization.ds.blobRadius * 2)
                val rectHeight = treePos.height - highestBlobPos.y

                if (rectHeight > 0) {
                    drawRectangles.add(Rectangle(leftTopX, leftTopY, rectWidth, rectHeight).shape)
                }
            }
            var drawRectangle = drawRectangles.first()

            for (rect in drawRectangles) {
                drawRectangle = drawRectangle.union(rect)
            }

            val lowestTreePositions: MutableList<TreePosition<EmbeddedMergeTree>> = mutableListOf()

            for (leave in tree.leaves) {
                val currentMaskHighY = tree.getDeepestLeave().pos.y + 1
                val carveHeight = abs(leave.pos.y - currentMaskHighY)
                val carveWidth = if(leave.fullWidth) ds.blobRadius * 2 else ds.nonMappedRadius * ds.blobRadius * 2
                val carveRect =
                    Rectangle(leave.pos.x - carveWidth * 0.5, leave.pos.y - 0.01, carveWidth, carveHeight).shape

                drawRectangle = drawRectangle.difference(carveRect)

                val lowestTreePosInColumn = blob.first.filter { it.firstDown.pos.x == leave.pos.x }
                    .maxByOrNull { it.height }

                if (lowestTreePosInColumn != null)
                    lowestTreePositions.add(lowestTreePosInColumn)
            }

            //Draw hedge from root to root+delta
            if (highestNodeInBlob.firstUp == null) {
                //val leftLeave = if (tree1) tree1E.leaves.first() else tree2E.leaves.first()
                val leftLeave = lowestTreePositions.minBy { it.firstDown.pos.x }.firstDown
                val leftMargin = if (leftLeave.fullWidth) ds.blobRadius else ds.nonMappedRadius * ds.blobRadius
                //val rightLeave = if (tree1) tree1E.leaves.last() else tree2E.leaves.last()
                val rightLeave = lowestTreePositions.maxBy { it.firstDown.pos.x }.firstDown
                val rightMargin = if (rightLeave.fullWidth) ds.blobRadius else ds.nonMappedRadius * ds.blobRadius
                val rootWidth = abs(leftLeave.pos.x - rightLeave.pos.x) + (leftMargin + rightMargin)
                strokeWeight = rootWidth + (ds.blobRadius * 2)

                val otherHighestPos = if (tree1) tree2E.pos else tree1E.pos
                val topY = min(highestNodeInBlob.height - ds.blobRadius, otherHighestPos.y - ds.blobRadius)

                val topRect = Rectangle(
                    leftLeave.pos.x - leftMargin,
                    topY,
                    rootWidth,
                    abs(topY - highestNodeInBlob.height)
                ).shape

                drawRectangle = union(drawRectangle, topRect)
            }

            if (ds.carveInwards) {
                for (i in lowestTreePositions.indices) {
                    if (i == lowestTreePositions.size - 1) break

                    val currentTreePos = lowestTreePositions[i]
                    val nextTreePos = lowestTreePositions[i + 1]

                    if (currentTreePos.height == nextTreePos.height) continue

                    val leftMargin =
                        if (currentTreePos.firstDown.fullWidth) ds.blobRadius else ds.nonMappedRadius * ds.blobRadius
                    val rightMargin =
                        if (nextTreePos.firstDown.fullWidth) ds.blobRadius else ds.nonMappedRadius * ds.blobRadius

                    val carveXTop = currentTreePos.firstDown.pos.x + leftMargin
                    val carveYTop = min(currentTreePos.height, nextTreePos.height)
                    val carveWidth =
                        abs(currentTreePos.firstDown.pos.x - nextTreePos.firstDown.pos.x) - (leftMargin + rightMargin)
                    val carveHeight = abs(currentTreePos.height - nextTreePos.height)

                    if (carveWidth > 0 && carveHeight > 0) {
                        val carveRect = Rectangle(carveXTop, carveYTop, carveWidth, carveHeight).shape
                        drawRectangle = drawRectangle.difference(carveRect)
                    }
                }
            }

            //Connectors
            val shortestContour = drawRectangle.contours.minBy { it.bounds.height }

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

                    shortestSideSegment = shortestContour.segments.maxBy { it.bounds.center.x }
                    contourSideSegment = contour.segments.minBy { it.bounds.center.x }

                } else {
                    conTopX = contour.bounds.x + contour.bounds.width
                    conWidth = abs(conTopX - shortestContour.bounds.x)

                    shortestSideSegment = shortestContour.segments.minBy { it.bounds.center.x }
                    contourSideSegment = contour.segments.maxBy { it.bounds.center.x }

                }

                val conTopY = if (ds.connectorTop)
                    min(
                        shortestSideSegment.bounds.y,
                        contourSideSegment.bounds.y
                    ) else min(
                    shortestSideSegment.bounds.center.y,
                    contourSideSegment.bounds.center.y
                ) - ds.connectorRadius * 0.5

                val conHeight = ds.connectorRadius

                if (conHeight > 0) {
                    val connector = Rectangle(conTopX, conTopY, conWidth, conHeight).shape
                    connectors.add(connector)
                }
            }

            for (connector in connectors) {
                drawRectangle = drawRectangle.union(connector)
            }

            fill = blob.third.mix(ColorRGBa.WHITE, ds.whiten)
            shape(drawRectangle)
        }
    }

    fun drawBlobs(drawer: CompositionDrawer, t1: Boolean) {
        //if (!blobsEnabled) return;
        var count: Int = 0;

        if (t1) {
            for (blob in tree1BlobsTest.reversed()) {
                drawBlob(drawer, tree1E, blob, tree1BlobsTest.size)
                count += 1
            }
        } else {
            count = 0
            //Draw blobs of tree2 (reversed to draw large blobs on top of smaller blobs)
            for (blob in tree2BlobsTest.reversed()) {
                drawBlob(drawer, tree2E, blob, tree2BlobsTest.size)
                count += 1
            }
        }
    }

    fun drawPath(
        drawer: CompositionDrawer,
        tree: EmbeddedMergeTree,
        blob: Triple<MutableList<TreePosition<EmbeddedMergeTree>>, Int, ColorRGBa>,
        numberOfBlobs: Int
    ) {
        val tree1 = (tree == tree1E)

        drawer.apply {
            fill = null
            strokeWeight = ds.verticalEdgeWidth

            stroke = blob.third

            val currentNode =
                blob.first.maxByOrNull { it.height } //This is the deepest node in the blob (path is defined by that node.
            val pathNodes = if (tree1) tree2PathDecomposition[blob.second] else tree1PathDecomposition[blob.second]
            if (pathNodes.isEmpty()) return
            val lowestPathPoint = if (interleaving.delta < 0.001) TreePosition(
                pathNodes.first(),
                0.0
            ) else TreePosition(
                pathNodes.first(),
                pathNodes.first().height - (currentNode!!.height - interleaving.delta)
            )

            //Draw the lowest sub edge delta up from the leaf of the path
            val edge = lowestPathPoint.firstDown.edgeContour;
            if (edge == null) return
            val curveOffset =
                if (interleaving.delta < 0.001) 0.0 else edge!!.on(treePositionToPoint(lowestPathPoint)!!, 0.5);
            val subContour = edge.sub(0.0, curveOffset!!)
            val blackBottomMargin = ds.verticalEdgeWidth * (1 - ds.verticalMappedRatio) / edge.length / 2

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
                        strokeWeight = ds.verticalEdgeWidth * ds.verticalMappedRatio
                        //contour(fromTree2Local(pathParent.edgeContour!!))
                        //drawContour.union(pathParent.edgeContour!!.shape)
                    } else {
                        stroke = blob.third
                        strokeWeight = ds.verticalEdgeWidth * ds.verticalMappedRatio
                        //contour(fromTree1Local(pathParent.edgeContour!!))
                    }
                }
                pathParent = pathParent.parent;
            }

            var rootContour: ShapeContour? = null
            if (pathParent == null){
                val tree1Pos = tree2E.pos
                val tree2Pos = tree1E.pos

                //val otherHighestPos = if (tree1) tree2E.pos else tree1E.pos
                val topY = min(tree1Pos.y, tree2Pos.y)

                if (tree1) {
                    //val startPos = Vector2(tree1Pos.x, tree1Pos.y + 0.1)
                    rootContour = LineSegment(
                        tree1Pos,
                        Vector2(tree1Pos.x, topY - interleaving.delta - ds.blobRadius)
                    ).contour
                }
                else {
                    //val pos2 = tree2E.pos
                    rootContour = LineSegment(tree2Pos, Vector2(tree2Pos.x, topY - interleaving.delta - ds.blobRadius)).contour
                }
            }

            stroke = globalcs.edgeColor
            strokeWeight = ds.verticalEdgeWidth
            var backdraw = backgroundContours.first()
            for (cont in backgroundContours) {

                backdraw += cont
                //contour(cont)
            }
            if (rootContour != null) {
                backdraw += rootContour
            }

            contour(backdraw)


            //Draw Area Patch
            fill = blob.third
            strokeWeight = ds.verticalEdgeWidth * ds.patchStrokeScale
            stroke = if (tree1) globalcs.edgeColor else globalcs.edgeColor2

            val highY = min(tree1E.pos.y, tree2E.pos.y)

            val posY = if (pathParent != null) highestY else highY - interleaving.delta - ds.blobRadius
            val posX = lowestPathPoint.firstDown.pos.x
            var pos = Vector2(posX, posY)
            //pos = if (!tree1) fromTree1Local(pos) else fromTree2Local(pos)

            val rectWidth = ds.verticalEdgeWidth * ds.pathAreaPatchScale
            pos -= rectWidth / 2
            rectangle(Rectangle(pos, rectWidth))

            stroke = blob.third
            strokeWeight = ds.verticalEdgeWidth * ds.verticalMappedRatio
            var draw = pathContours.first()
            //Draw colored path
            for (cont in pathContours) {
                //stroke = blob.third
                //strokeWeight = ds.verticalEdgeWidth * ds.verticalMappedRatio

                draw += cont
                //contour(cont)
            }
            if (rootContour != null) {
                draw += rootContour
            }
            contour(draw)
        }
    }

    fun drawPaths(drawer: CompositionDrawer, t1: Boolean) {
        //if (!blobsEnabled) return;
        var count: Int = 0;

        //Draw rays from root
        drawer.apply {
            fill = null

            val tree1Pos = tree1E.pos
            val tree2Pos = tree2E.pos

            if (!t1) {
                //Draw Contour
                val pos1 = tree1E.pos
                val contour1 = LineSegment(tree1Pos, Vector2(tree1Pos.x, tree2Pos.y - interleaving.delta - ds.blobRadius)).contour

                // Draw white casing
                stroke = globalcs.edgeColor
                strokeWeight = ds.verticalEdgeWidth
                contour(contour1)
            } else {
                //Draw Contour
                val pos2 = tree2E.pos
                val contour2 = LineSegment(tree2Pos, Vector2(tree2Pos.x, tree1Pos.y - interleaving.delta - ds.blobRadius)).contour

                // Draw white casing
                stroke = globalcs.edgeColor
                strokeWeight = ds.verticalEdgeWidth
                contour(contour2)
            }
        }

        if (t1) {
            //Draw mapping of blob in the first tree onto the second tree
            for (blob in tree1BlobsTest) {
                drawPath(drawer, tree1E, blob, tree1BlobsTest.size)
                count += 1
            }
        } else {
            //Draw mapping of blob in the second tree onto the second tree
            for (blob in tree2BlobsTest) {
                drawPath(drawer, tree2E, blob, tree2BlobsTest.size)
                count += 1
            }
        }

        //Draw Rays from root
        drawer.apply {
            stroke = ColorRGBa.BLACK
            fill = null
            strokeWeight = ds.verticalEdgeWidth * ds.verticalMappedRatio

            val tree1Pos = tree1E.pos
            val tree2Pos = tree2E.pos

            if (!t1) {
                //Draw Contour
                val startPos = Vector2(tree1Pos.x, tree1Pos.y+0.1)
                val contour1 = LineSegment(startPos, Vector2(tree1Pos.x, tree2Pos.y - interleaving.delta - ds.blobRadius)).contour

                //Set path Color
                val pathID1 = tree1BlobsTest.first().second
                stroke = tree2BlobsTest[0].third
                //contour(contour1)
            }
            else {
                //Draw Contour
                val pos2 = tree2E.pos
                val contour2 = LineSegment(tree2Pos, Vector2(tree2Pos.x, tree1Pos.y - interleaving.delta - ds.blobRadius)).contour

                //Set path Color
                val pathID2 = tree2BlobsTest.first().second
                stroke = tree1BlobsTest[0].third

                //contour(contour2)
            }
            //Draw nodes of the trees on top of the path decomposition
            if(ds.drawNodes)
                composition(nodeComposition)
        }
    }

    private fun drawGrid(drawer: CompositionDrawer, bound1: Rectangle, bound2:Rectangle, halfGap: Double){
        val startHeight = min(tree1E.pos.y, tree2E.pos.y) - interleaving.delta - ds.blobRadius
        val deepestLeaveY = max(tree1E.getDeepestLeave().pos.y, tree1E.getDeepestLeave().pos.y)

        var leftX = bound1.x -bound1.width / 2 - halfGap
        leftX -= if(tree1E.leaves().first().fullWidth) ds.blobRadius else ds.nonMappedRadius
        leftX -= ds.gridlinePadding

        var rightX = bound2.x+ bound2.width + bound2.width / 2 + halfGap// bound1.width/2 + halfGap*2 + (bound2.width) + ds.gridlinePadding
        rightX += ds.gridlinePadding
        rightX += if(tree2E.leaves().last().fullWidth) ds.blobRadius else ds.nonMappedRadius

//        var leftX = tree.leaves().first().pos.x - ds.gridlinePadding
//
//        leftX -= if(tree.leaves().first().fullWidth) ds.blobRadius else ds.nonMappedRadius
//
//        var rightX = tree.leaves.last().pos.x + ds.gridlinePadding
//        rightX += if(tree.leaves().last().fullWidth) ds.blobRadius else ds.nonMappedRadius
//
//        if (tree==tree1E){
//            rightX += halfGap
//        }
//        else {
//            leftX -= halfGap
//        }

        //rightX += if(tree1E.leaves().last().fullWidth) ds.blobRadius else ds.nonMappedRadius

        //rightX += halfGap *2
//        rightX += if(tree2E.leaves().first().fullWidth) ds.blobRadius else ds.nonMappedRadius
//        rightX += if(tree2E.leaves().last().fullWidth) ds.blobRadius else ds.nonMappedRadius
//
//        rightX += abs(tree2E.leaves().first().pos.x - tree2E.leaves().last().pos.x)
        //rightX += ds.gridlinePadding

        val yStep = interleaving.delta / 4

        drawer.apply {
            strokeWeight = ds.gridlineThickness
            stroke = globalcs.gridColor
            var currentY = startHeight
            while (currentY <= deepestLeaveY + yStep){
                val line = LineSegment(leftX, currentY, rightX, currentY).contour
                contour(line)

                currentY += yStep
            }
        }
    }

    /** Draw tree1E and tree2E side by side */
    private fun treePairComposition() {
        val tree1C = drawComposition {
            //drawBlobs(this)
            tree1E.draw(this, true, ds, globalcs)

        }
        val tree2C = drawComposition { tree2E.draw(this, false, ds, globalcs) }
        val tree1NC = drawComposition { tree1E.drawNodes(this, ds.markRadius) }
        val tree2NC = drawComposition { tree2E.drawNodes(this, ds.markRadius) }
        val bounds1 = tree1C.findShapes().map { it.bounds }.bounds
        val bounds2 = tree2C.findShapes().map { it.bounds }.bounds
        val halfGap = ds.markRadius * 20

        blobCompositionTest(true)
        blobCompositionTest(false)

        tree1BlobIndicesSorted = tree1BlobsTest.indices.toMutableList()
        tree2BlobIndicesSorted = tree2BlobsTest.indices.toMutableList()
        tree1BlobIndicesSorted.sortBy { highestPointInBlob(true, tree1BlobsTest, it).y }
        tree2BlobIndicesSorted.sortBy { highestPointInBlob(false, tree2BlobsTest, it).y }

//        setHedgeColors()
        setBlobColorsTest(true)
        setBlobColorsTest(false)

        val tree1BlobDrawing = drawComposition { drawBlobs(this, true) }
        val tree2BlobDrawing = drawComposition { drawBlobs(this, false) }

        val tree1PathDrawing = drawComposition { drawPaths(this, false) }
        val tree2PathDrawing = drawComposition { drawPaths(this, true) }

        val grid1 = drawComposition { drawGrid(this, bounds1, bounds2, halfGap) }
        //val grid2 = drawComposition { drawGrid(this, tree2E, halfGap) }


//        val tree1Hedges = drawComposition {
//            for (hedge in tree1Hedges) {
//                hedge.draw(this)
//            }
//        }
//
//        val tree2Hedges = drawComposition {
//            for (hedge in tree2Hedges) {
//                hedge.draw(this)
//            }
//        }

        composition = drawComposition {
            translate(pos)
            composition(grid1)

            isolated {
                translate(-bounds1.width / 2 - halfGap, 0.0)
                tree1EMatrix = model
                //composition(tree1Hedges)
                composition(tree1BlobDrawing)
                composition(tree1C)

                composition(tree1PathDrawing)
                if(ds.drawNodes)
                    composition(nodeComposition)
            }
            isolated {
                translate(bounds2.width / 2 + halfGap, 0.0)
                tree2EMatrix = model
                //composition(tree2Hedges)
                composition(tree2BlobDrawing)
                composition(tree2C)
                composition(tree2PathDrawing)
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

    private fun hedgeComposition(t1: Boolean) {
        val tree = if (t1) tree1E else tree2E
        val treeMapping = if (t1) interleaving.f else interleaving.g
        val paths = if (t1) tree2PathDecomposition else tree1PathDecomposition

        if (t1)
            tree1Hedges.clear()
        else tree2Hedges.clear()

        val hedges: MutableList<Pair<MutableList<TreePosition<EmbeddedMergeTree>>, Int>> = mutableListOf()
        for (i in paths.indices) {
            hedges.add(Pair(mutableListOf(), i))

            for (pathNode in paths[i]) {
                val treePosList = treeMapping.inverseNodeEpsilonMap[pathNode]
                    ?: continue

                for (treePos in treePosList) {
                    hedges.last().first.add(treePos)
                }
            }
        }
        for (node in tree.nodes()) {
            val other = treeMapping.nodeMap[node];
            val otherPathID = getPathID(other!!.firstDown, paths)

            hedges[otherPathID].first.add(TreePosition(node, 0.0))
        }

        for (i in hedges.indices) {
            if (t1)
                tree1Hedges.add(Hedge(this, t1, hedges[i].first, i, hedges[i].second))
            else tree2Hedges.add(Hedge(this, t1, hedges[i].first, i, hedges[i].second))
        }
    }

    private fun blobCompositionTest(t1: Boolean) {
        val tree = if (t1) interleaving.f else interleaving.g;

        if (t1) {
            tree1BlobsTest.clear()
            for (i in tree2PathDecomposition.indices) {
                tree1BlobsTest.add(Triple(mutableListOf(), i, ColorRGBa.BLACK))
                for (treePos in tree2PathDecomposition[i]) {
                    val blobTreePosList = interleaving.f.inverseNodeEpsilonMap[treePos]
                        ?: continue// MutableList<TreePosition<EmbeddedMergeTree>> = mutableListOf()

                    for (blobTreePos in blobTreePosList) {
                        tree1BlobsTest.last().first.add(blobTreePos)
                    }
                }
            }
            for (node in tree1E.nodes()) {
                val other = tree.nodeMap[node];
                var otherPathID = getPathID(other!!.firstDown, tree2PathDecomposition)
                //if (otherPathID == -1) otherPathID = 0

                tree1BlobsTest[otherPathID].first.add(TreePosition(node, 0.0))
            }

            tree1BlobsTest.sortBy { sublist -> sublist.first.minOf { it.height } }


//            println("testingblobs")
//            for (blob in tree1BlobsTest.indices){
//                println("blobID: " + blob + "  pathID: " + tree1BlobsTest[blob].second)
//                for (treepos in tree1BlobsTest[blob].first){
//                    println(treepos)
//                }
//            }
        } else {
            tree2BlobsTest.clear()
            for (i in tree1PathDecomposition.indices) {
                tree2BlobsTest.add(Triple(mutableListOf(), i, ColorRGBa.BLACK))
                for (node in tree1PathDecomposition[i]) {

                    val blobTreePosList = interleaving.g.inverseNodeEpsilonMap[node]
                        ?: continue// MutableList<TreePosition<EmbeddedMergeTree>> = mutableListOf()

                    for (blobTreePos in blobTreePosList) {
                        tree2BlobsTest.last().first.add(blobTreePos)
                    }
                }
            }
            for (node in tree2E.nodes()) {
                val other = tree.nodeMap[node]
                var otherPathID = getPathID(other!!.firstDown, tree1PathDecomposition)
                if (otherPathID == -1) otherPathID = 0
                tree2BlobsTest[otherPathID].first.add(TreePosition(node, 0.0))
            }

            tree2BlobsTest.sortBy { sublist -> sublist.first.minOf { it.height } }


        }
    }

    /** Construct blob decomposition into tree1Blobs if t1=true and into tree2Blobs if t1=false */
    private fun blobComposition(t1: Boolean, color1: ColorRGBa, color2: ColorRGBa) {
        val tree = if (t1) interleaving.f else interleaving.g;

        if (t1) tree1Blobs.clear() else tree2Blobs.clear()

        var groupColor = color1;

        for (group in tree.leafGroups) {
            var colorDetermined = false;
            val path = mutableListOf<EmbeddedMergeTree>()

            for (leaf in group) {
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

    fun fromTree1Local(shape: Shape): Shape {
        return shape.transform(tree1EMatrix);
    }

    fun fromTree2Local(shape: Shape): Shape {
        return shape.transform(tree2EMatrix);
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

    private fun closestPositionTi(
        tree: EmbeddedMergeTree,
        pos: Vector2,
        radius: Double
    ): TreePosition<EmbeddedMergeTree>? {
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