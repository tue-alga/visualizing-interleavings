import kotlin.math.abs
import kotlin.math.max

class Interval(
    var begin: Double = 0.0,
    var end: Double = 1.0,
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
}

fun computeFrechet(source: Array<Double>, target: Array<Double>) : Double{
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
    while (upperbound - lowerbound > 0.01) {
        val mid = (upperbound + lowerbound) / 2
        if (frechetDecision(mid, source, target)) {
            upperbound = mid
        } else {
            lowerbound = mid
        }
    }
    return upperbound;
}

fun frechetDecision(delta: Double, source: Array<Double>, target: Array<Double>) : Boolean {
    if (abs(source[0] - target[0]) > delta || abs(source.last() - target.last()) > delta) {
        return false
    }
//
//    val (freeLeft, freeBottom) = computeFreeSpace(delta, source, target)
//
//    val (reachableLeft, reachableBottom) = computeReachableSpace(delta, source, target, freeLeft, freeBottom)
//
//    return (reachableLeft.last().last() > -1)
    return true;
}
