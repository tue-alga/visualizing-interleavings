import kotlin.math.abs

/** We represent a point on the tree by the first vertex you encounter when moving down.
  * [heightDelta] is the height distance you traveled until encountering the [firstDown] vertex. */
data class TreePosition<T: MergeTreeLike<T>>(val firstDown: T, val heightDelta: Double) {
    val height get() = firstDown.height - heightDelta
    val firstUp get() = firstDown.parent
}

/** A mapping between two merge trees.
  * The map that is passed is required to map only the leaves of the one tree to points a fixed delta higher on the other tree.*/
// Note! Currently, all height logic is flipped to match the computer graphics convention that the top has height 0 and the height increases when going down.
class TreeMapping<T: MergeTreeLike<T>>(leafMap: Map<T, TreePosition<T>>) {
    val nodeMap: MutableMap<T, TreePosition<T>> = mutableMapOf()
    val edgeMap: MutableMap<T, List<Pair<Double, T>>> = mutableMapOf()
    val inverseNodeEpsilonMap: MutableMap<T, MutableList<TreePosition<T>>> = mutableMapOf()
    val pathCharges: MutableMap<T, Int> = mutableMapOf()


    //A list containing groups of leaves (represented as a list) that map to the same monotonically increasing path in the other merge tree.
    val leafGroups: MutableList<MutableList<T>> = mutableListOf()

    //Stores the leave that belongs to the nodes path <node, leaves>
    val pathDecomposition: MutableList<MutableList<T>> = mutableListOf()

    init {
        val key = leafMap.keys.first()
        val delta = abs(leafMap[key]!!.height - key.height)

        val q = mutableListOf<Pair<T, TreePosition<T>>>()

        for (kvPair in leafMap) {
            q.add(kvPair.toPair())
        }

        //Create Groups of leaves that map to the same monotone path.
        for (currentLeaf in leafMap.keys.reversed()) {
            var addedToGroup = false;
            for (group in leafGroups) {
                if(shareMonotonePath(leafMap[group[0]]!!.firstDown, leafMap[currentLeaf]!!.firstDown)) {
                    group.add(currentLeaf)
                    addedToGroup = true
                    break
                }
            }
            if (!addedToGroup) {
                leafGroups.add(mutableListOf(currentLeaf))
            }
        }
        //Sort groups based on deepest leaf
        leafGroups.sortByDescending { group -> group.maxOf {it.height} }

        while (q.isNotEmpty()) {
            val (node, point) = q.first()
            q.removeFirst()
            if (edgeMap.contains(node)) continue
            nodeMap[node] = point

            // A list y1 > y2 > ... > yn where at height yi the edge start mapping to a different edge on the other tree.
            // A point that lies at height y on this edge, with y in interval
            // (y1.first, node.height] is mapped to TreePosition(point.firstDown, point.deltaHeight + (node.height - y))
            // (y2.first, y1.first]    is mapped to TreePosition(y1.second, y1.first - y)
            // ...
            // (-infty, yn.first]      is mapped to TreePosition(yn.second, yn.first - y)
            val thisEdgeMap: MutableList<Pair<Double, T>> = mutableListOf()

            // The next node on the other tree, which creates mapping intervals
            var current: T? = point.firstDown.parent

            while (current != null) {
                // The height of the point on this tree mapping to current
                // note that y-coordinate is flipped so we add delta
                val thisHeight = current.height + delta

                // Check if current is mapped to by a point on this edge
                if (thisHeight >= node.parent!!.height){// || (thisHeight > node.parent!!.height && node.parent == null)) {
                    //heightDelta = node.height - (lowestPathPoint.firstUp!!.height + delta)
                    //thisHeight - (node.height - current.height)
                    val treePoint = TreePosition(node, 0.0)//thisHeight - (node.height))// - current.height))

                    if (thisHeight > node.parent!!.height) {
                        if (!inverseNodeEpsilonMap.contains(current)) {
                            inverseNodeEpsilonMap[current] = mutableListOf()
                        }
                        inverseNodeEpsilonMap[current]!!.add(treePoint)
                    }
                    thisEdgeMap.add(Pair(thisHeight, current))
                    current = current.parent

                } else {
                    break
                }
            }

            if (node.parent != null) {
                val parentResult = if (thisEdgeMap.isEmpty()) TreePosition(point.firstDown, point.heightDelta + node.height - node.parent!!.height)
                else TreePosition(thisEdgeMap.last().second, thisEdgeMap.last().first - node.parent!!.height)
                q.add(node.parent!! to parentResult)
            }

            edgeMap[node] = thisEdgeMap
        }
        val root = getRoot(inverseNodeEpsilonMap.keys.first())

        //Set charge of all nodes (1 charge for each path that maps to that node)
        for (n in inverseNodeEpsilonMap.keys) {
            if (inverseNodeEpsilonMap[n] == null) continue;

            for (i in inverseNodeEpsilonMap[n]!!.indices) {

                val treePos = nodeMap[inverseNodeEpsilonMap[n]!![i].firstDown]
                val pathNode = treePos!!.firstDown;

                if (!pathCharges.contains(pathNode)) {
                    pathCharges[pathNode] = 0;
                }
                pathCharges[pathNode] = pathCharges[pathNode]!! + 1;
            }
        }
        createPaths(root)
    }

    private fun createPaths(node: T) {
        for (child in node.children) {
            createPaths(child)
        }

        //First node of path
        if (!hasChargedChildren(node) && pathCharges.contains(node)) {
            pathDecomposition.add(mutableListOf(node))
            if (!pathCharges.contains(node.parent)){
                pathCharges[node.parent!!] = 1
            }
        }
        else if (node.children.isNotEmpty()) {
            if (getIdOfPriorityPath(node) != -1) {
                pathDecomposition[getIdOfPriorityPath(node)].add(node)
            }
        }
    }

    private fun getRoot(node: T): T {
        var current: T? = node

        while (current!!.parent != null){
            current = current.parent
        }

        return current
    }

    private fun hasChargedChildren(node: MergeTreeLike<T>): Boolean {
        if (node.children.isEmpty()) return false

        for (child in node.children) {
            if (pathCharges.contains(child) && pathCharges[child] != null) {
                return true;
            }
        }
        return false;
    }

    private fun getIdOfPriorityPath(node: T): Int {
        val highCharged: MutableList<T> = mutableListOf()

        val chargeList: MutableMap<Int, MutableList<T>> = mutableMapOf()

        var highestCharge = 0

        for (child in node.children) {
            if (pathCharges[child] != null) {
                if (chargeList[pathCharges[child]] == null){
                    chargeList[pathCharges[child]!!] = mutableListOf()
                    chargeList[pathCharges[child]!!]!!.add(child)

                    if (pathCharges[child]!! >= highestCharge) {
                        highestCharge = pathCharges[child]!!;
                    }
                }
                else if (chargeList.contains(pathCharges[child])){
                    chargeList[pathCharges[child]]!!.add(child)
                }
            }
        }
        if (chargeList[highestCharge]!!.count() == 1) {
            getPathID(chargeList[highestCharge]!!.first())//id of chargeList[highestCharge].first()
        }

        return getPathID(getDeepestPath(chargeList[highestCharge]!!))
    }

    fun getPathID(node: T): Int {
        for (i in pathDecomposition.indices) {
            if (pathDecomposition[i].contains(node)) {
                return i;
            }
        }
        return -1
    }

    private fun getDeepestPath(nodes: MutableList<T>): T{

        var deepestPath: T = nodes.first()

        for (i in nodes.indices){
            if (i == 0) continue

            if(getPathID(nodes[i]) == -1){ continue }

            if (getPathID(deepestPath) == -1){
                deepestPath = nodes[i]
                continue
            }

            if (pathDecomposition[getPathID(nodes[i])].first().height >= pathDecomposition[getPathID(deepestPath)].first().height) {
                deepestPath = nodes[i]
            }

        }

        return deepestPath
    }

    operator fun get(pos: TreePosition<T>): TreePosition<T> {
        val nodeResult = nodeMap[pos.firstDown]!!
        if (pos.heightDelta == 0.0) {
            return nodeResult
        }
        val edgeResult = edgeMap[pos.firstDown]!!
        val y = pos.firstDown.height - pos.heightDelta
        return edgeResult.takeWhile { y < it.first }.lastOrNull()?.let {
            TreePosition(it.second, it.first - y)
        } ?: TreePosition(nodeResult.firstDown, nodeResult.heightDelta + pos.heightDelta)
    }
}

// tm : TreeMapping
// tp: TreePosition
// tm[tp]
// tm.get(tp)

data class Interleaving<T: MergeTreeLike<T>>(val f: TreeMapping<T>, val g: TreeMapping<T>, val delta: Double)

/** Helper function to create a tree mapping. [leafMap] should map leaves to leaves. The function returns
 * a mapping that maps leaves to ancestors of their original image but delta higher. */
fun <T: MergeTreeLike<T>> leafMapping(leafMap: Map<T, T>, delta: Double): TreeMapping<T> {
    val leafToPointMap = buildMap {
        for ((leaf1, leaf2) in leafMap) {
            var current = leaf2
            while (current.parent != null && current.parent!!.height > leaf1.height - delta - 0.0001) {
                current = current.parent!!
            }
            set(leaf1, TreePosition(current, current.height - (leaf1.height - delta - 0.0001)))
        }
    }

    return TreeMapping(leafToPointMap)
}

fun <T: MergeTreeLike<T>> getDeepestNodeInPath(path: MutableList<T>): MergeTreeLike<T> {
    var deepest: MergeTreeLike<T>? = null

    for (node in path) {
        if (deepest == null) { deepest = node}
        else {
            if (node.height <= deepest.height) {
                deepest = node
            }
        }
    }
    return deepest!!
}

//Helper method to check if two nodes are on one monotone path.
fun <T1: MergeTreeLike<T1>, T2: MergeTreeLike<T2>> shareMonotonePath(tree1: T1, tree2: T2)
: Boolean {
    //return true if roots are equal
    if(tree1 == tree2) return true

    //return true if they share a common child.
    for (current in tree1.nodes()) {
        for (other in tree2.nodes()) {
            if (current == other) return true
        }
    }

    return false
}