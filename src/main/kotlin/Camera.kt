import org.openrndr.Extension
import org.openrndr.MouseButton
import org.openrndr.MouseEvents
import org.openrndr.Program
import org.openrndr.draw.Drawer
import org.openrndr.draw.RenderTarget
import org.openrndr.events.Event
import org.openrndr.extra.camera.ChangeEvents
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.transforms.buildTransform

class Camera : Extension, ChangeEvents {
    override var enabled = true

    var view = Matrix44.IDENTITY
    var rotationCenter = Vector2.ZERO

    var enableRotation = true;

    override val changed = Event<Unit>()

    private var dirty = true
        set(value) {
            if (value && !field) {
                changed.trigger(Unit)
            }
            field = value
        }
    override val hasChanged: Boolean
        get() = dirty

    fun setupMouseEvents(mouse: MouseEvents) {
        mouse.buttonDown.listen {
            rotationCenter = it.position
        }
        mouse.dragged.listen {
            if (!it.propagationCancelled) {
                when (it.button) {
                    MouseButton.LEFT -> view = buildTransform {
                        translate(it.dragDisplacement)
                    } * view

                    MouseButton.RIGHT -> view = buildTransform {

                        if (enableRotation) {
                            translate(rotationCenter)
                            rotate(it.dragDisplacement.x + it.dragDisplacement.y)
                            translate(-rotationCenter)
                        }
                    } * view


                    else -> Unit
                }
                dirty = true
            }
        }
        mouse.scrolled.listen {
            if (!it.propagationCancelled) {
                val scaleFactor = 1.0 + it.rotation.y * 0.1
                view = buildTransform {
                    translate(it.position)
                    scale(scaleFactor)
                    translate(-it.position)
                } * view
                dirty = true
            }
        }
    }

    override fun setup(program: Program) {
        setupMouseEvents(program.mouse)
    }

    override fun beforeDraw(drawer: Drawer, program: Program) {
        drawer.pushTransforms()
        drawer.ortho(RenderTarget.active)
        drawer.view = view
    }

    override fun afterDraw(drawer: Drawer, program: Program) {
        dirty = false
        drawer.popTransforms()
    }
}