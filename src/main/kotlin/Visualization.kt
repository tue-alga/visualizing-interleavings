import org.openrndr.color.ColorHSVa
import org.openrndr.color.ColorRGBa
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.shape.*
import java.awt.Color
import kotlin.math.max
import kotlin.math.min

class Visualization(val tree1: MergeTree,
                    val tree2: MergeTree,
                    val pos: Vector2,
                    val tes: TreeEmbedSettings = TreeEmbedSettings(),
                    val ds: DrawSettings = DrawSettings(),
                    val globalcs: GlobalColorSettings = GlobalColorSettings(),
                    val tcs: ThreeColorSettings = ThreeColorSettings(),
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
    var tree1BlobsTest: MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>> = mutableListOf();
    var tree2BlobsTest: MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>> = mutableListOf();
    var tree1Colors: MutableList<ColorRGBa> = mutableListOf();
    var tree2Colors: MutableList<ColorRGBa> = mutableListOf();
    var nodes1ToColor: MutableList<EmbeddedMergeTree> = mutableListOf();
    var nodes2ToColor: MutableList<EmbeddedMergeTree> = mutableListOf();

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

        nodes1ToColor = tree1E.nodes().toMutableList()
        nodes2ToColor = tree2E.nodes().toMutableList()
        nodes1ToColor.removeAll { it.children.isEmpty() }
        nodes2ToColor.removeAll { it.children.isEmpty() }
        nodes1ToColor.sortBy { it.height }
        nodes2ToColor.sortBy { it.height }


        setBlobColors(true, 0, -1, -1, tcs.t1c1)
        setBlobColors(false, 0, -1, -1, tcs.t2c1)

        //val rightblobs = rightMostBlobsInSubtree(tree2BlobsTest, highestNodeInBlob(tree2BlobsTest, 0))

//        println("yeet")
//        println(highestNodeInBlob(tree2BlobsTest, 0))
//        println(rightblobs.size)
//        for (blob in rightblobs) {
//            println(tree2BlobsTest[blob].third)
//            println(highestNodeInBlob(tree2BlobsTest, blob))
//        }
       // getBlobOfNode(true, tree1E)
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

    private fun highestNodeInBlob(blobs: MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>>, blobID: Int): EmbeddedMergeTree{
        //println(blobs[blobID].first)
        var highest = blobs[blobID].first.first()

        for (node in blobs[blobID].first) {
            if (node.height < highest.height) {
                highest = node
            }
        }
        return highest
    }

    private fun highestPointInBlob(t1: Boolean, blobs: MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>>, blobID: Int): Vector2 {
        val highestNode = highestNodeInBlob(blobs, blobID)

        val parent = highestNode.parent

        if (parent != null)
            return Vector2 (highestNode.pos.x, parent.pos.y + interleaving.delta)

        else return Vector2(highestNode.pos.x, highestNode.pos.y - interleaving.delta)
    }

    private fun getLeavesInBlob(blobs: MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>>, blobsID: Int): MutableList<EmbeddedMergeTree> {
        val leavesInBlob = mutableListOf<EmbeddedMergeTree>()

        for (node in blobs[blobsID].first) {
            if (node.children.isEmpty()){
                leavesInBlob.add(node)
            }
        }

        return leavesInBlob
    }

    private fun getBlobOfNode(blobs: MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>>, node: EmbeddedMergeTree): Int {
        //val blobs = if (t1) tree1BlobsTest else tree2BlobsTest;

        for (i in blobs.indices) {
            if (blobs[i].first.contains(node)){
                return i
            }
        }
        return -1
    }

    private fun getParentBlob(blobs:  MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>>, blobID: Int): Int {
        //val blobs = if (t1) tree1BlobsTest else tree2BlobsTest;

        val parentNode = highestNodeInBlob(blobs, blobID).parent ?: return -1 //returns null if parent = null -> means that blob contains the root node

        return getBlobOfNode(blobs, parentNode)
        //return tree.first()
    }

    private fun getChildBlobs(blobs:  MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>>, blobID: Int): MutableList<Int>{
        val children: MutableList<Int> = mutableListOf()

        for (node in blobs[blobID].first) {
            for (child in node.children) {
                if (blobs[blobID].first.contains(child)) continue

                children.add(getBlobOfNode(blobs, child))
            }
        }

        return children.distinct().toMutableList()
    }

    private fun getHighestChildBlobs(blobs:  MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>>, node: EmbeddedMergeTree): MutableList<Int> {
        val childrenNotInBlob: MutableList<Int> = mutableListOf()

        val blobID = getBlobOfNode(blobs, node)
        println("blob")
        println(blobs[blobID])
        for (child in node.children) {
            if (blobs[blobID].first.contains(child)) continue

            childrenNotInBlob.add(getBlobOfNode(blobs, child))
        }
        return childrenNotInBlob
    }

    private fun getRemainingColors(usedColor: ColorRGBa, colors: MutableList<ColorRGBa>): MutableList<ColorRGBa>{
        val remainingColors: MutableList<ColorRGBa> = mutableListOf()

        for (i in colors.indices){
            if (usedColor != colors[i]){
                remainingColors.add(colors[i])
            }
        }
        return remainingColors
    }

    private fun getColorOrder(firstColor: ColorRGBa, excludeColor: ColorRGBa, colors: MutableList<ColorRGBa>): MutableList<ColorRGBa>{
        val outPutColors: MutableList<ColorRGBa> = mutableListOf()

        outPutColors.add(firstColor)

        for (i in colors.indices){
            if (firstColor != colors[i] && excludeColor != colors[i]){
                outPutColors.add(colors[i])
            }
        }
        return outPutColors
    }

    private fun leftMostBlobsInSubtree(blobs: MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>>, subTreeRoot: EmbeddedMergeTree): MutableList<Int> {
        val leftMostBlobs = mutableListOf<Int>()

        val leftMostBlobID = getBlobOfNode(blobs, subTreeRoot.leaves.first())

        val highestLeftMost = highestNodeInBlob(blobs, leftMostBlobID)

        leftMostBlobs.add(leftMostBlobID)

        val leavesToLoop = subTreeRoot.leaves.drop(1)
        for (leaf in leavesToLoop) {
            val leafBlobID = getBlobOfNode(blobs, leaf)
            val highestInBlob = highestNodeInBlob(blobs, leafBlobID)

            if (highestInBlob.height < highestNodeInBlob(blobs, leftMostBlobs.last()).height){
                leftMostBlobs.add(leafBlobID)
            }
        }

        return leftMostBlobs
    }

    private fun rightMostBlobsInSubtree(blobs: MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>>, subTreeRoot: EmbeddedMergeTree): MutableList<Int> {
        val rightMostBlobs = mutableListOf<Int>()

        val blob  = getBlobOfNode(blobs, subTreeRoot)

        val leavesInBlob = getLeavesInBlob(blobs, blob)

        //val leftMostBlobID = getBlobOfNode(blobs, subTreeRoot.leaves.last())
        val leftMostBlobID = getBlobOfNode(blobs, leavesInBlob.last())

        for (leave in leavesInBlob) {
            print(leave.pos.toString()  + " | ")
        }

        rightMostBlobs.add(leftMostBlobID)

        val leavesToLoop = subTreeRoot.leaves.drop(1)
        for (leaf in leavesToLoop.reversed()) {
            val leafBlobID = getBlobOfNode(blobs, leaf)
            val highestInBlob = highestNodeInBlob(blobs, leafBlobID)

            if (highestInBlob.height < highestNodeInBlob(blobs, rightMostBlobs.last()).height){
                rightMostBlobs.add(leafBlobID)
            }
        }

        return rightMostBlobs
    }

    private fun getDirectVerticalChildInBlob(blobs: MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>>, blobID: Int) {

    }

    private fun lowBlobsTouch(t1: Boolean, blobs: MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>>, leftBlobID: Int, rightBlobID: Int): Boolean {
        //PROBLEM = top = bot if only leave is in the node.
        val leftLeave = getLeavesInBlob(blobs, leftBlobID).first()
        val leftTop = highestNodeInBlob(blobs, leftBlobID)

        val leftHighestPos = highestPointInBlob(t1, blobs, leftBlobID)

        val rightLeave = getLeavesInBlob(blobs, rightBlobID).last()
        val rightTop = highestNodeInBlob(blobs, rightBlobID)
        val rightHighestPos = highestPointInBlob(t1, blobs, rightBlobID)


        println("checking lowblobs")
        println("leftBlobID: " + leftBlobID)
        println("rightBlobID: " + rightBlobID)
        println("lefttop: " + leftTop.pos)
        println("righttop: " + rightTop.pos)
        println("leftleave: " + leftLeave.pos)
        println("rightleave: " + rightLeave.pos)


        if (leftHighestPos.y < rightLeave.pos.y && rightHighestPos.y < leftLeave.pos.y) {
            println("LOW BLOBS TOUCH")
            return true
        }

        return false
    }

    private fun highBlobsTouch(blobs: MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>>, lowNodeID: Int, highNodeID: Int, highSideID: Int, highRight: Boolean): Boolean {

        val lowNodeIDTop = highestNodeInBlob(blobs, lowNodeID)
        val highNodeIDTop = highestNodeInBlob(blobs, highNodeID)


        if (highRight && lowNodeIDTop.pos.x > highNodeIDTop.pos.x) return false
        if (!highRight && lowNodeIDTop.pos.x < highNodeIDTop.pos.x) return false

        //val lowLeave = getLeavesInBlob(blobs, lowNodeID).first()

        if (highNodeID != highSideID) return false


        return true
    }

    private fun getBlobsTouchingLeft(t1: Boolean, blobs: MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>>, blobID: Int, leftBlobID: Int): MutableList<Int> {
        val touchingBlobs = mutableListOf<Int>()

        val tree = if(t1) tree1E else tree2E
        val leftMostLeave = getLeavesInBlob(blobs, blobID).first()
        val id = tree.leaves.indexOf(leftMostLeave)

        if (id != 0) {
            val lowestTouching = tree.leaves[id - 1]
            val lowestBlob = getBlobOfNode(blobs, lowestTouching)

            //TODO, HERE WE WANT TO EARLY OUT IF OTHER BLOB IS HIGHER THAN CURRENT BLOB SINCE WE DO NOT TOUCH PARENTS IN THAT CASE
//            if (highestPointInBlob(t1, blobs, blobID).y <= highestPointInBlob(t1, blobs, lowestBlob).y) {
//                return touchingBlobs
//            }

            if (lowBlobsTouch(t1, blobs, lowestBlob, blobID)) {
                touchingBlobs.add(lowestBlob)

                if (highestPointInBlob(t1, blobs, blobID).y >= highestPointInBlob(t1, blobs, lowestBlob).y) {
                    return touchingBlobs
                }
            }

            var parent = getParentBlob(blobs, lowestBlob)
            touchingBlobs.add(parent)

            parent = getParentBlob(blobs, parent)

            while(parent != -1){
                //If parent highest is to the right, it should not map
                if (highBlobsTouch(blobs, blobID, parent, leftBlobID,false))
                    touchingBlobs.add(parent)
                else break

                parent = getParentBlob(blobs, parent)
            }
        }

        return touchingBlobs
    }

    private fun getBlobsTouchingRight(t1: Boolean, blobs: MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>>, blobID: Int, rightBlobID: Int): MutableList<Int> {
        val touchingBlobs = mutableListOf<Int>()

        val tree = if(t1) tree1E else tree2E

        val rightMostLeave = getLeavesInBlob(blobs, blobID).last()

        val id = tree.leaves.indexOf(rightMostLeave)
        println("leaveID: " + id)

        if (id != tree.leaves.size -1) {
            val lowestTouching = tree.leaves[id + 1]
            val lowestBlob = getBlobOfNode(blobs, lowestTouching)
            println("Compare")
            println("left: " + id + " " + blobID)
            println("right: " + id + 1 + " " + lowestBlob)

//            if (highestPointInBlob(t1, blobs, blobID).y <= highestPointInBlob(t1, blobs, lowestBlob).y) {
//                return touchingBlobs
//            }

            if (lowBlobsTouch(t1, blobs, blobID, lowestBlob)) {
                touchingBlobs.add(lowestBlob)
                //TODO, HERE WE WANT TO EARLY OUT IF OTHER BLOB IS HIGHER THAN CURRENT BLOB SINCE WE DO NOT TOUCH PARENTS IN THAT CASE
                if (highestPointInBlob(t1, blobs, blobID).y >= highestPointInBlob(t1, blobs, lowestBlob).y) {
                    return touchingBlobs
                }
            }
            var parent = getParentBlob(blobs, lowestBlob)
            touchingBlobs.add(parent)

            parent = getParentBlob(blobs, parent)

            while(parent != -1){
                if (highBlobsTouch(blobs, blobID, parent, rightBlobID, true))
                    touchingBlobs.add(parent)
                else break

                parent = getParentBlob(blobs, parent)
            }
        }

        return touchingBlobs
    }

    //return neighbouring colors that are not a child of this blob
    private fun getTouchingColors(yes: Boolean, t1: Boolean, blobs: MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>>, blobID: Int, leftBlobID: Int, rightBlobID: Int, leftMost: Boolean=false, rightMost: Boolean=false)
    : MutableList<ColorRGBa>{
        val touchingColors = mutableListOf<ColorRGBa>()

        //return empty list if blob is root, since all other blobs are children
        val parentBlobID = getParentBlob(blobs, blobID)
        println("parentID: " + parentBlobID)
//        val parentIsRoot = parentBlobID == -1
//        if (parentIsRoot) return touchingColors

        //You always touch your parent blob
        val parentColor = blobs[parentBlobID].third
        println("parentColor: " + parentColor)
        touchingColors.add(parentColor)

        if (leftMost && leftBlobID != -1){
            //val touchingBlobIDs = rightMostBlobsInSubtree(blobs, highestNodeInBlob(blobs, leftBlobID))
            println("blobID: " + blobID)
            println("leftBlobID: " + leftBlobID)

            val touchingBlobIDs = getBlobsTouchingLeft(t1, blobs, blobID, leftBlobID)

            //if (!yes) return touchingColors


            println("touchingBlobIDs: " + touchingBlobIDs)
            for (id in touchingBlobIDs){

                if (blobs[id].third != ColorRGBa.BLACK) {
                    touchingColors.add(blobs[id].third)
                }
            }
        }
        if (rightMost && rightBlobID != -1){
            //val touchingBlobIDs = leftMostBlobsInSubtree(blobs, highestNodeInBlob(blobs, rightBlobID))
            val  touchingBlobIDs = getBlobsTouchingRight(t1, blobs, blobID, rightBlobID)
            println("touchingBlobIDs: " + touchingBlobIDs)

            for (id in touchingBlobIDs) {
                if (blobs[id].third != ColorRGBa.BLACK) {
                    println("TOUCHING")

                    touchingColors.add(blobs[id].third)
                }
            }
        }
        return touchingColors
    }

    private fun getLeftRightParentBlobID(t1: Boolean, blobs: MutableList<Triple<MutableList<EmbeddedMergeTree>, Int, ColorRGBa>>, node: EmbeddedMergeTree): Pair<Int, Int> {
        var parent = node.parent
        if (parent == null) return Pair(-1, -1) //No neighboring parent to the root node

        var leftID = -1
        var rightID = -1

        val currentBlob = getBlobOfNode(blobs, node)
        val leaves = getLeavesInBlob(blobs, currentBlob)
        val currentLeafXpos = leaves.first().pos.x

        val tree = if(t1) tree1E else tree2E
        val id = tree.leaves.indexOf(leaves.first())

        print("idd: " + id)

        if (id != tree.leaves.size -1) {
            val rightLeave = getBlobOfNode(blobs, tree.leaves[id + 1])
            rightID = getParentBlob(blobs, rightLeave)

            if (rightID == -1)
                rightID = rightLeave
        }

        if (id != 0) {
            val leftLeave = getBlobOfNode(blobs, tree.leaves[id - 1])
            leftID = getParentBlob(blobs, leftLeave)

            if (leftID == -1)
                leftID = leftLeave
        }

        print("currentBlob: " + currentBlob)
        print("leftID: " + leftID)
        if (leftID == currentBlob) leftID = -1
        if (rightID == currentBlob) rightID = -1

        return Pair(leftID, rightID)


        val leftChildBlobIDs = mutableListOf<Int>()
        val rightChildBlobIDs = mutableListOf<Int>()

        var sameBlob = getBlobOfNode(blobs, parent) == currentBlob

        var sameSiblings = getHighestChildBlobs(blobs, parent)
        var otherSiblings = mutableListOf<Int>()

        for (child in parent.children) {
            otherSiblings.add(getBlobOfNode(blobs, child))
        }

        if (!sameBlob) println("notsame")

        for (childID in if(sameBlob) sameSiblings else otherSiblings) {
            val childLeaveXpos = getLeavesInBlob(blobs, childID).first().pos.x
            if (childLeaveXpos < currentLeafXpos) {
                leftChildBlobIDs.add(childID)
            } else rightChildBlobIDs.add(childID)
        }

        while(rightChildBlobIDs.isEmpty()){
            parent = parent!!.parent

            if (parent == null) break

            sameBlob = getBlobOfNode(blobs, parent) == currentBlob

            sameSiblings = getHighestChildBlobs(blobs, parent)
            otherSiblings = mutableListOf<Int>()

            for (child in parent.children) {
                otherSiblings.add(getBlobOfNode(blobs, child))
            }

            //if (!sameBlob) println("notsame")

            for (childID in if(sameBlob) sameSiblings else otherSiblings) {
                val childLeaveXpos = getLeavesInBlob(blobs, childID).first().pos.x
                if (childLeaveXpos >= currentLeafXpos) {
                    rightChildBlobIDs.add(childID)
                }
            }
        }


        leftID = if (leftChildBlobIDs.isNotEmpty()) leftChildBlobIDs.last() else -1
        rightID = if (rightChildBlobIDs.isNotEmpty()) rightChildBlobIDs.first() else -1

        println("parentBlobID's")
        println(leftID)
        println(rightID)


        if (leftID == currentBlob) leftID = -1
        if (rightID == currentBlob) rightID = -1

        //if (leftID != -1 || rightID != -1) println("Nominus")

        return Pair(leftID, rightID)
    }

    private fun setBlobColors(t1: Boolean, blobID: Int, leftBlobID: Int=-1, rightBlobID: Int=-1, color: ColorRGBa, rightOfRoot: Boolean=false){
        val blobs = if(t1) tree1BlobsTest else tree2BlobsTest
        val colors = if(t1) tree1Colors else tree2Colors

        val parentBlobID = getParentBlob(blobs, blobID)

        val parentIsRoot = parentBlobID == -1
        //if (parentBlobID == -1) { blobs[blobID] = Triple(blobs[blobID].first, blobs[blobID].second, colors.first()) } //TODO: Make mutableTriple so that we can change the color, or use other datatype
        if (parentBlobID == -1) blobs[blobID] = Triple(blobs[blobID].first, blobs[blobID].second, color)

        var index = 0
        while (if(t1) nodes1ToColor.isNotEmpty() else nodes2ToColor.isNotEmpty()) {
            //if (index > 6) break

            //val thisColor = blobs[blobID].third

            val currentNode = if (t1) nodes1ToColor.first() else nodes2ToColor.first()
            val currentBlob = getBlobOfNode(blobs, currentNode)
            val currentColor = blobs[currentBlob].third

            if (currentNode.pos.y == 30.0) {
                print("thisONE")
            }

            //For all highest children in blob
            val highestChildBlobIDs = getHighestChildBlobs(blobs, currentNode)

//            if (highestChildBlobIDs.size == 0)  {
//                var onlyLeaveChilds = true
//
//                for (child in currentNode.children) {
//                    if (child.children.isNotEmpty())
//                        onlyLeaveChilds = false
//                }
//
//                if (onlyLeaveChilds) {
//                    if (t1) nodes1ToColor.remove(currentNode) else nodes2ToColor.remove(currentNode)
//                    continue
//                }
//            }

            println("highestchildthingSize: " + highestChildBlobIDs.size)

            //leaveXpos
            val leaves = getLeavesInBlob(blobs, currentBlob)

            println(leaves.size)
            val leaveXpos = leaves.first().pos.x
            println("leavePos: " + leaveXpos)

            val leftChildBlobIDs = mutableListOf<Int>() //mutableListOf<Int>()
            val rightChildBlobIDs = mutableListOf<Int>()

            for (childID in highestChildBlobIDs) {
                val childLeaveXpos = getLeavesInBlob(blobs, childID).first().pos.x
                println(childLeaveXpos)
                if (childLeaveXpos < leaveXpos) {
                    leftChildBlobIDs.add(childID)
                } else rightChildBlobIDs.add(childID)
            }
            //if(!yes) return


            println(leftChildBlobIDs)
            println("currentNode: " + currentNode)

            if (leftChildBlobIDs.isNotEmpty()) {
                val parentNeighbours = getLeftRightParentBlobID(t1, blobs, getLeavesInBlob(blobs, leftChildBlobIDs.first()).first())

                println("break")
                println(getLeavesInBlob(blobs, leftChildBlobIDs.first()).first().pos)
                println("leftChildBlobID: " + leftChildBlobIDs.first())
                println("parentNeighbours: " + parentNeighbours)


                //if(index == 6) break
                val go = index != 6
                val leftTouchingColors =
                    getTouchingColors(go, t1, blobs, leftChildBlobIDs.first(), parentNeighbours.first, parentNeighbours.second, leftMost = true)
                //leftTouchingColors.add(currentColor)
                //leftTouchingColors.add(currentColor)
//            println("LeavePOS")
//            println(getLeavesInBlob(blobs, leftChildBlobIDs.first()).first().pos )
//            println(tree2E.leaves.indexOf(getLeavesInBlob(blobs, leftChildBlobIDs.first()).first()))
                println("leftTouchingColors: " + leftTouchingColors)
                //if(index == 6) break

                println("checkPos: " + leaves.first().pos)
              println("lefttouchingColors: " + leftTouchingColors)
                var leftColor = ColorRGBa.BLACK
                for (c in colors) {
                    if (!leftTouchingColors.contains(c)) {
                        leftColor = c
                        break
                    }
                }

                val leftColorOrder = getColorOrder(leftColor, currentColor, colors)
                println("leftColor: " + leftColor)
                println("leftColorOrderSize: " + leftColorOrder.size)
                println(leftColorOrder)

                for (i in leftChildBlobIDs.indices) {
                    val childColor = leftColorOrder[i % leftColorOrder.size]
                    blobs[leftChildBlobIDs[i]] =
                        Triple(blobs[leftChildBlobIDs[i]].first, blobs[leftChildBlobIDs[i]].second, childColor)

                    println("childPos = " + getLeavesInBlob(blobs, blobs[leftChildBlobIDs[i]].second))
                }
                if(index == 6) break


                //TODO: Probably sort based on highest childs -> we want to color higher blobs first
//                leftChildBlobIDs.sortBy { index -> highestNodeInBlob(blobs, index).height }
//                for (i in leftChildBlobIDs.indices) {
//                    val childColor = leftColorOrder[i % leftColorOrder.size]
//                    val rightID = if (i + 1 <= leftChildBlobIDs.size) i + 1 else -1;
//                    setBlobColors(false, t1, leftChildBlobIDs[i], i - 1, rightID, childColor
//                    ) //This should not be done here
//                }
            }

            //TODO: HIER VERDER
            if (rightChildBlobIDs.isNotEmpty()) {

                //If I have a node, simply look at the parent and then at the right sibling
                //val node = getBlobOfNode(blobs, blobID)
                val parentNeighbours = getLeftRightParentBlobID(t1, blobs, getLeavesInBlob(blobs, rightChildBlobIDs.first()).first())
                val rightTouchingColors =
                    getTouchingColors(true, t1, blobs, rightChildBlobIDs.last(), parentNeighbours.first, parentNeighbours.second, rightMost = true)
                //rightTouchingColors.add(currentColor)
                println("LeavePOS")
                println("actualparentCol: " + currentColor)
                println(getLeavesInBlob(blobs, rightChildBlobIDs.last()).last().pos)
                println(tree2E.leaves.indexOf(getLeavesInBlob(blobs, rightChildBlobIDs.first()).first()))

                println("rightTouchingColors: " + rightTouchingColors.size)
                println(rightTouchingColors)
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

                    println("childPos = " + getLeavesInBlob(blobs, blobs[rightChildBlobIDs[i]].second))
                }

//                rightChildBlobIDs.sortBy { index -> highestNodeInBlob(blobs, index).height }
//                for (i in rightChildBlobIDs.indices.reversed()) {
//                    val childColor = rightColorOrder[(i) % rightColorOrder.size]
//                    val rightID = if (i + 1 <= rightChildBlobIDs.size) i + 1 else -1;
//                    setBlobColors(true, t1, rightChildBlobIDs[i], i - 1, rightID, childColor)
//                }
            }


            if (t1) nodes1ToColor.remove(currentNode) else nodes2ToColor.remove(currentNode)
            index += 1
        }
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

    fun colorThreeValues(t1: Boolean, size: Int): List<ColorRGBa> {
        val c1 = if (t1) tcs.t1c1 else tcs.t2c1;
        val c2 = if (t1) tcs.t1c2 else tcs.t2c2;
        val c3 = if (t1) tcs.t1c3 else tcs.t2c3;

        //val values = if (t1) listOf(c3, c3, c1, c3, c2, c3) else listOf(c1, c2, c3, c2, c3, c2)
        val values = if (t1) listOf(c1, c2, c3) else listOf(c1, c2, c3)

        return List(size) { index -> values[index % values.size] }

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
                tree1BlobsTest.add(Triple(mutableListOf(), i, ColorRGBa.BLACK))
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
                tree2BlobsTest.add(Triple(mutableListOf(), i, ColorRGBa.BLACK))
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