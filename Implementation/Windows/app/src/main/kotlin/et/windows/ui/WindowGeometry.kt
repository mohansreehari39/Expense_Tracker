package et.windows.ui

import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit

/** The monitor configuration whose bounds contain [point], falling back to the default screen. */
fun screenConfigurationAt(point: Point): GraphicsConfiguration =
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        .map { it.defaultConfiguration }
        .find { it.bounds.contains(point) }
        ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration

/**
 * Screen bounds minus the taskbar/dock for [configuration]'s monitor —
 * undecorated AWT windows don't pick this up on their own the way
 * natively-decorated ones do (see Implementation/Windows/README.md).
 */
fun usableScreenBounds(configuration: GraphicsConfiguration): Rectangle {
    val screenBounds = configuration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    return Rectangle(
        screenBounds.x + insets.left,
        screenBounds.y + insets.top,
        screenBounds.width - insets.left - insets.right,
        screenBounds.height - insets.top - insets.bottom,
    )
}
