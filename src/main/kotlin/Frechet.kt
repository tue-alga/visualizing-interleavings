import kotlin.math.*

class Interval(
    var begin: Double = 1.0,
    var end: Double = 0.0,
    ){

    fun isEmpty(): Boolean {
        return begin > end;
    }

    fun intersects(other: Interval): Boolean {
        if (isEmpty() || other.isEmpty()){
            return false;
        }

        return ((begin >= other.begin && begin <= other.end) ||
                (end >= other.begin && end <= other.end) ||
                (begin <= other.begin && end >= other.end))
    }

    fun reset() {
        begin = 1.0;
        end = 0.0;
    }
    override fun toString(): String {
        return "[${begin}, ${end}]"
    }
}

fun <T: MergeTreeLike<T>> monotoneInterleaving(source: T, target: T): Interleaving<T> {
    val highestPoint = min(source.height, target.height)
    val rootHeight = highestPoint - 1
    val sourceCurve = inducedCurve(source, rootHeight)
    val targetCurve = inducedCurve(target, rootHeight)

    val (delta, reachableSpace) = computeFrechet(sourceCurve, targetCurve)
    val path = getReachablePath(reachableSpace)
    val matching = getMatching(path)
    val (sourceToTarget, targetToSource) = matching

    val stLeafMap = mutableMapOf<T, T>()
    val tsLeafMap = mutableMapOf<T, T>()

    val sLeaves = source.leaves()
    val tLeaves = target.leaves()

    for (i in 0 until sLeaves.size) {
        val pathVertexIndex = i * 2 + 1
        val map = sourceToTarget.first { it.first == pathVertexIndex }

        // Take floor and ceiling of curve parameter.
        // The integer that is odd is the 'leaf index'
        val f = floor(map.second).toInt()
        val c = ceil(map.second).toInt()
        val l = listOf(f, c).first { it % 2 == 1 }

        val sourceLeaf = sLeaves[(map.first - 1) / 2]
        val targetLeaf = tLeaves[(l - 1)/2]
        stLeafMap[sourceLeaf] = targetLeaf
    }

    for (i in 0 until target.leaves().size) {
        val pathVertexIndex = i * 2 + 1
        val map = targetToSource.first { it.first == pathVertexIndex }

        // Take floor and ceiling of curve parameter.
        // The integer that is odd is the 'leaf index'
        val f = floor(map.second).toInt()
        val c = ceil(map.second).toInt()
        val l = listOf(f, c).first { it % 2 == 1 }

        val targetLeaf = tLeaves[(map.first - 1) / 2]
        val sourceLeaf = sLeaves[(l - 1)/2]
        tsLeafMap[targetLeaf] = sourceLeaf
    }

    return Interleaving(leafMapping(stLeafMap, delta - 0.00001), leafMapping(tsLeafMap, delta - 0.00001), delta - 0.00001)
}

fun <T: MergeTreeLike<T>>inducedCurve(tree: T, rootHeight: Double) : List<Double> {
    val curve = mutableListOf<Double>()
    val leaves = tree.leaves()
    curve.add(rootHeight)
    leaves.zipWithNext { leaf, nextLeaf ->
        curve.add(leaf.height)
        curve.add(lca(leaf, nextLeaf).height)
    }
    curve.add(leaves.last().height)
    curve.add(rootHeight)
    return curve
}

fun <T: MergeTreeLike<T>> lca(t1: T, t2: T) : T {
    if (t1 == t2 || t1.parent == null) {
        return t1
    } else if (t2.parent == null) {
        return t2
    }

    if (t1.height > t2.height) {
        return lca(t1.parent!!, t2)
    }
    return lca(t1, t2.parent!!)
}

fun computeFrechet(source: List<Double>, target: List<Double>) : Pair<Double, ReachableSpace> {
    val n1 = source.size;
    val n2 = target.size;

    if (n1 < 2 || n2 < 2) {
        error("WARNING: comparison possible only for curves of at least 2 points!")
    }

    var lowerbound = 0.0;

    // Can always match everything above root of the tree
    val ub1 = max(0.0, source.max() - target.min())
    val ub2 = max(0.0, target.max() - source.min())
    var upperbound = max(ub1, ub2);

    // Binary search to find frechet distance
    // Error margin currently 0.01
    var count = 0
    var finalReachableSpace: ReachableSpace? = null
    while (upperbound - lowerbound > 0.01) {
        val mid = (upperbound + lowerbound) / 2
        val (passed, reachableSpace) = frechetDecision(mid, source, target)
        if (passed) {
            upperbound = mid
            finalReachableSpace = reachableSpace
        } else {
            lowerbound = mid
        }
        count++
    }

    return upperbound to finalReachableSpace!!
}

fun frechetDecision(delta: Double, source: List<Double>, target: List<Double>) : Pair<Boolean, ReachableSpace?> {
    if (abs(source[0] - target[0]) > delta || abs(source.last() - target.last()) > delta) {
        return false to null
    }

    val (freeLeft, freeBottom) = computeFreeSpace(delta, source, target)

    val reachable = computeReachableSpace(delta, source, target, freeLeft, freeBottom)
    return (reachable.left.last().last() < Double.POSITIVE_INFINITY) to reachable
}

fun computeFreeSpace(delta: Double, source: List<Double>, target: List<Double>) : Pair<MutableList<MutableList<Interval>>, MutableList<MutableList<Interval>>> {
    var freeLeft = mutableListOf<MutableList<Interval>>()
    var freeBottom = mutableListOf<MutableList<Interval>>()
    val n1 = source.size
    val n2 = target.size

    for (i in 0..<n1) {
        var leftRow = mutableListOf<Interval>()
        var bottomRow = mutableListOf<Interval>()

        for (j in 0..<n2) {
            if (i < n1 - 1) {
                leftRow.add(freeBoundary(delta, target[j], source[i], source[i+1]))
            }
            if (j < n2 - 1) {
                bottomRow.add(freeBoundary(delta, source[i], target[j], target[j+1]))
            }
        }
        if (i < n1 - 1) {
            freeLeft.add(leftRow)
        }
        freeBottom.add(bottomRow)
    }

    return Pair(freeLeft, freeBottom)
}

fun freeBoundary(delta: Double, h: Double, start: Double, end: Double) : Interval {
    if (start == end) {
        if (abs(h- start) < delta) { return Interval(0.0, 1.0) }
        return Interval()
    }

    val ineq1 = 1.0 / (end-start) * (h - delta - start)
    val ineq2 = 1.0 / (end - start) * (delta - start + h)

    if (start < end) {
        if (ineq1 > 1.0 || ineq2 < 0.0) { return Interval() }
        return Interval(max(0.0, ineq1), min(1.0, ineq2))
    }

    if (ineq2 > 1.0 || ineq1 < 0.0) { return Interval() }

    return Interval(max(0.0, ineq2), min(1.0, ineq1))
}

typealias Grid<T> = List<List<T>>
data class ReachableSpace(val left: Grid<Double>, val bottom: Grid<Double>)

fun computeReachableSpace(
    delta: Double,
    source: List<Double>,
    target: List<Double>,
    freeLeft: Grid<Interval>,
    freeBottom: Grid<Interval>
) : ReachableSpace {

    val reachableLeft = mutableListOf<MutableList<Double>>()
    val reachableBottom = mutableListOf<MutableList<Double>>()
    val n1 = source.size
    val n2 = target.size

    // Initialize the reachable space by setting infinity everywhere
    for (i in 0..<n1) {
        val leftRow = mutableListOf<Double>()
        val bottomRow = mutableListOf<Double>()
        for (j in 0..<n2) {
            leftRow.add(Double.POSITIVE_INFINITY)
            if (j < n2 - 1) {
                bottomRow.add(Double.POSITIVE_INFINITY)
            }
        }
        if (i < n1 - 1) {
            reachableLeft.add(leftRow)
        }
        reachableBottom.add(bottomRow)
    }

    // Calculate the borders
    for (i in 0..<n1-1) {
        if (abs(source[i] - target[0]) > delta) {
            break
        }
        reachableLeft[i][0] = 0.0
    }

    for (j in 0..<n2-1) {
        if (abs(source[0] - target[j]) > delta) {
            break
        }
        reachableBottom[0][j] = 0.0
    }

    // Compute lowest (leftmost) reachable point of the left (bottom) edge of each cell
    for (i in 0..<n1) {
        for (j in 0..<n2) {
            // Set reachability left edge (right edge of previous cell)
            if (i < n1 - 1 && j > 0) {
                // If the edge is not free, then it is not reachable
                if (!freeLeft[i][j].isEmpty()) {
                    // If the bottom edge of the cell is reachable, then the lowest point on the right (current) edge is reachable
                    if (reachableBottom[i][j - 1] < Double.POSITIVE_INFINITY) {
                        reachableLeft[i][j] = freeLeft[i][j].begin
                    } else if (reachableLeft[i][j - 1] <= freeLeft[i][j].end) {
                        // Else, find lowest reachable point on left edge that is above the free interval on the right (current) edge
                        reachableLeft[i][j] = max(freeLeft[i][j].begin, reachableLeft[i][j - 1])
                    }
                }
            }

            // Set reachability bottom edge (top edge of previous cell)
            if (j < n2 - 1 && i > 0) {
                if (!freeBottom[i][j].isEmpty()) {
                    if (reachableLeft[i-1][j] < Double.POSITIVE_INFINITY) {
                        reachableBottom[i][j] = freeBottom[i][j].begin
                    } else if (reachableBottom[i-1][j] <= freeBottom[i][j].end) {
                        reachableBottom[i][j] =
                            max(freeBottom[i][j].begin, reachableBottom[i - 1][j])
                    }
                }
            }
        }
    }

    return ReachableSpace(reachableLeft, reachableBottom)
}

data class ReachablePathPoint (
    val i : Int, val j: Int, val left: Double, val bottom: Double
) {
    override fun toString(): String {
        return "(${i}, ${j}, ${bottom}, ${left})"
    }
}

fun getReachablePath(
    reachableSpace: ReachableSpace
) : MutableList<ReachablePathPoint> {
    val reachableLeft = reachableSpace.left
    val reachableBottom = reachableSpace.bottom
    var i = reachableLeft.size - 1
    var j = reachableBottom[0].size - 1
    val points = mutableListOf(ReachablePathPoint(i+1, j+1, 0.0, 0.0))

    var fromTop = true
    var start = 0.0

    while (i >= 0 && j >= 0) {
        val left = reachableLeft[i][j]
        val bottom = reachableBottom[i][j]
        // Not precise
        if (left == 0.0 || bottom == 0.0) {
            points.add(ReachablePathPoint(i, j, 0.0, 0.0))
            i--
            j--
            fromTop = false
            start = 0.0
        } else if (left < Double.POSITIVE_INFINITY || (!fromTop && left < start)) {
            points.add(ReachablePathPoint(i,j,left,0.0))
            j -= 1
            fromTop = false
            start = 1.0-left
        } else {
            points.add(ReachablePathPoint(i, j, 0.0, bottom))
            i -= 1
            fromTop = true
            start = 1.0-bottom
        }
    }

    for (k in 0..i) {
        points.add(ReachablePathPoint(k, 0, 0.0, 0.0))
    }
    for (k in 0..j) {
        points.add(ReachablePathPoint(0, k, 0.0, 0.0))
    }
    return points
}

fun getMatching(points : MutableList<ReachablePathPoint>) :
        Pair<MutableList<Pair<Int, Double>>,MutableList<Pair<Int, Double>>> {
    val i = points[0].i
    val j = points[0].j

    val alpha = mutableListOf<Pair<Int, Double>>(Pair(i, j.toDouble()))
    val beta = mutableListOf<Pair<Int, Double>>(Pair(j, i.toDouble()))

    for (point in points) {
        if (point.i < i) {
            alpha.add(Pair(point.i, point.j.toDouble() + point.bottom))
        }
        if (point.j < j) {
            beta.add(Pair(point.j, point.i.toDouble() + point.left))
        }
    }
    alpha.reverse()
    beta.reverse()
    return Pair(alpha, beta)
}