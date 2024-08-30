import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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

fun monotoneDistance(source: MergeTree, target: MergeTree) : Double{
    val sourceCurve = inducedCurve(source)
    val targetCurve = inducedCurve(target)

    println(sourceCurve)
    println(targetCurve)
    return computeFrechet(sourceCurve, targetCurve)
}

fun inducedCurve(tree: MergeTree) : List<Double> {
    val curve = mutableListOf<Double>()
    val leaves = tree.leaves()
    curve.add(100.0)
    for ((leaf, nextLeaf) in leaves.subList(0, leaves.size-1).zip(leaves.subList(1, leaves.size))) {
        curve.add(leaf.height)
        curve.add(lca(leaf, nextLeaf).height)
    }
    curve.add(leaves.last().height)
    curve.add(100.0)
    return curve
}

fun lca(t1: MergeTree, t2: MergeTree) : MergeTree {
    if (t1 == t2 || t1.parent == null) {
        return t1
    } else if (t2.parent == null) {
        return t2
    }

    if (t1.height > t2.height) {
        return lca(t1.parent, t2)
    }
    return lca(t1, t2.parent)
}

fun test() {
    val source = listOf(20.0, 0.0, 5.0, 2.0, 10.0, 0.0, 20.0)
    val target = listOf(20.0, 2.0, 5.0, 0.0, 10.0, 0.0, 20.0)

    computeFrechet(source, target)
}

fun computeFrechet(source: List<Double>, target: List<Double>) : Double{
    val n1 = source.size;
    val n2 = target.size;

    if (n1 < 2 || n2 < 2) {
        println("WARNING: comparison possible only for curves of at least 2 points!");
        return -1.0;
    }

    var lowerbound = 0.0;

    // Can always match everything above root of the tree
    val ub1 = max(0.0, source.max() - target.min())
    val ub2 = max(0.0, target.max() - source.min())
    var upperbound = max(ub1, ub2);

    // Binary search to find frechet distance
    // Error margin currently 0.01
    var count = 0
    while (upperbound - lowerbound > 0.01) {
        val mid = (upperbound + lowerbound) / 2
        if (frechetDecision(mid, source, target)) {
            upperbound = mid
        } else {
            lowerbound = mid
        }
        count++
    }

    println(upperbound)
    return upperbound;
}

fun frechetDecision(delta: Double, source: List<Double>, target: List<Double>) : Boolean {
    if (abs(source[0] - target[0]) > delta || abs(source.last() - target.last()) > delta) {
        return false
    }

    val (freeLeft, freeBottom) = computeFreeSpace(delta, source, target)
    println(freeLeft)

    val (reachableLeft, reachableBottom) = computeReachableSpace(delta, source, target, freeLeft, freeBottom)
    println(delta)
    println(reachableLeft)
    return (reachableLeft.last().last() < Double.POSITIVE_INFINITY)
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

fun computeReachableSpace(
    delta: Double,
    source: List<Double>,
    target: List<Double>,
    freeLeft: MutableList<MutableList<Interval>>,
    freeBottom: MutableList<MutableList<Interval>>
) : Pair<MutableList<MutableList<Double>>, MutableList<MutableList<Double>>> {

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

    return Pair(reachableLeft, reachableBottom)
}