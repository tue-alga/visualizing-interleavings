import java.util.ArrayList;
import java.util.List;

public class Atze {

    public static class Tree {
        double w, h;          // Width and height.
        double x, y;          // Coordinates (x, y).
        Tree[] c;             // Array of child nodes.
        int childCount;       // Number of children.

        Tree(double w, double h, double y, Tree... children) {
            this.w = w; this.h = h; this.y = y; this.c = children;
            this.childCount = children.length;
        }
    }

    // Main layout function
    static void layout(Tree t) {
        setLeavePositions(t);  // Adjust the x-axis spacing of leaves in their order
        setInternalNodePositions(t);  // First pass to calculate initial positions
    }

    // First walk through the tree to assign basic positions
    static void setInternalNodePositions(Tree t) {
        if (t.childCount == 0) {
            return;  // If no children, it's a leaf
        }

        // Recursively apply firstWalk to all children
        for (int i = 0; i < t.childCount; i++) {
            setInternalNodePositions(t.c[i]);
        }

        Tree deepestLeaf = findDeepestLeaf(t);
        t.x = deepestLeaf.x;  // Align with the deepest leaf
    }

    // Adjust x-axis spacing so that all leaf nodes are evenly spaced and ordered
    static void setLeavePositions(Tree t) {
        List<Tree> leaves = new ArrayList<>();
        collectLeaves(t, leaves);

        // Find the deepest leaf
        Tree deepestLeaf = findDeepestLeaf(t);
        int deepestLeafIndex = leaves.indexOf(deepestLeaf);  // Get the position of the deepest leaf

        // Calculate the spacing based on the number of leaves
        double leafSpacing = 10;  // Arbitrary value for space between leaves
        // Adjust the startX based on the deepest leaf position
        double startX = -(deepestLeafIndex+1) * leafSpacing / 2.0;

        // Set x positions of leaves while preserving their order
        for (int i = 0; i < leaves.size(); i++) {
            leaves.get(i).x = startX + i * leafSpacing;
        }
    }

    // Collect all leaf nodes in the tree while preserving the order
    static void collectLeaves(Tree t, List<Tree> leaves) {
        if (t.childCount == 0) {
            leaves.add(t);  // This is a leaf node
        } else {
            // Traverse children in their original order
            for (Tree child : t.c) {
                collectLeaves(child, leaves);
            }
        }
    }

    // Find the deepest leaf node in the tree
    static Tree findDeepestLeaf(Tree t) {
        if (t.childCount == 0) {
            return t;  // This is a leaf
        }

        Tree deepest = null;
        double maxDepth = -1;

        for (Tree child : t.c) {
            Tree deep = findDeepestLeaf(child);
            if (deep != null && deep.y >= maxDepth) {
                maxDepth = deep.y;
                deepest = deep;
            }
        }

        return deepest;
    }
}
