import org.openrndr.KEY_SPACEBAR
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.parameters.ActionParameter
import org.openrndr.extra.parameters.TextParameter
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.shape.*
import org.openrndr.svg.saveToFile
import java.io.File
import kotlin.math.min

data class MergeTree(val height: Double, val children: List<MergeTree> = emptyList())

fun MergeTree.reverse(): MergeTree {
    if (children.isEmpty()) return this.copy()
    return MergeTree(height, children.map { it.reverse() }.reversed())
}

fun treeComposition(tree: MergeTree, pos: Vector2, tds: TreeDrawSettings) =
    drawComposition {
        isolated {
            translate(pos)
            tidyTree(tree, tds)
        }
    }

fun main() = application {
    configure {
        width = 800
        height = 800
    }
    program {
        // Construct a merge tree from a String
        val initialTree = parseTree("(0" +
                "(30" +
                "(60" +
                "(80)" +
                "(90))" +
                "(70))" +
                "(40" +
                "(50" +
                "(60)" +
                "(70))" +
                "(80)" +
                ")")
        val tree = MergeTree(initialTree.height, initialTree.children + initialTree.children.map { it.reverse() })

        val camera = Camera()
        val tds = TreeDrawSettings()

        var composition = treeComposition(tree, drawer.bounds.center, tds)
        val compBounds = composition.findShapes().map { it.bounds }.bounds
        val bbox = compBounds.offsetEdges(min(compBounds.width, compBounds.height) * 0.1)

        val viewSettings = object {
            @ActionParameter("Fit to screen")
            fun fitToScreen() {
                camera.view = Matrix44.fit(bbox, drawer.bounds)
            }
        }

        val exportSettings = object {
            @TextParameter("File name")
            var svgFileName: String = "output"

            @ActionParameter("Export to SVG")
            fun exportToSVG() {
                composition.saveToFile(File("${svgFileName}.svg"))
                composition.documentStyle.viewBox
            }
        }

        keyboard.keyDown.listen {
            if (it.key == KEY_SPACEBAR) {
                viewSettings.fitToScreen()
            }
        }

        val gui = GUI()
        gui.add(tds, "Tree drawing")
        gui.add(viewSettings, "View")
        gui.add(exportSettings, "Export")

        gui.onChange { name, value ->
            // name is the name of the variable that changed
            when (name) {
                "nodeWidth", "markRadius", "showRectangles" -> {
                    composition = treeComposition(tree, drawer.bounds.center, tds)
                }
            }
        }

        extend(gui)
        extend(camera) {
            enableRotation = true;
        }
        extend {
            drawer.apply {
                clear(ColorRGBa.WHITE)
                composition(composition)
            }
        }
    }
}