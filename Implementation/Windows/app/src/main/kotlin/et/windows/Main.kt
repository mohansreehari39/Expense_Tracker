package et.windows

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import et.core.model.HlcClock
import et.windows.db.SqlDelightOperationStore
import et.windows.db.SqlDelightRepository
import et.windows.db.openDatabase
import et.windows.server.AppServices
import et.windows.server.DEFAULT_PORT
import et.windows.server.startServer
import et.windows.ui.ApiClient
import et.windows.ui.DashboardApp
import java.io.File
import java.util.UUID

/** Stable per-install device id — v0 stand-in for real pairing (Design/Core/04-pairing-and-crypto.md). */
private fun loadOrCreateDeviceId(): String {
    val file = File(System.getProperty("user.home"), ".kharcha/device-id.txt")
    if (file.exists()) return file.readText().trim()
    file.parentFile.mkdirs()
    val id = UUID.randomUUID().toString()
    file.writeText(id)
    return id
}

fun main() {
    val db = openDatabase()

    val deviceId = loadOrCreateDeviceId()
    val operationStore = SqlDelightOperationStore(db)
    val repository = SqlDelightRepository(db, operationStore, deviceId, HlcClock(deviceId))
    val services = AppServices(repository, deviceId)

    val server = startServer(services, DEFAULT_PORT)
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(gracePeriodMillis = 500, timeoutMillis = 2000) })

    application {
        val icon = remember { BitmapPainter(useResource("icon.png") { loadImageBitmap(it) }) }
        val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)

        // Undecorated so we can draw our own title bar/window controls — see
        // DashboardApp's title bar composable. Still resizable via edge-drag.
        Window(
            onCloseRequest = ::exitApplication,
            title = "Kharcha",
            icon = icon,
            state = windowState,
            undecorated = true,
            resizable = true,
        ) {
            DashboardApp(
                api = ApiClient("http://localhost:$DEFAULT_PORT"),
                icon = icon,
                windowState = windowState,
                onMinimize = { windowState.isMinimized = true },
                onClose = ::exitApplication,
            )
        }
    }
}
