package et.windows

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import et.core.model.HlcClock
import et.windows.db.SqlDelightOperationStore
import et.windows.db.SqlDelightRepository
import et.windows.db.bootstrapIfEmpty
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
    val file = File(System.getProperty("user.home"), ".expense-tracker/device-id.txt")
    if (file.exists()) return file.readText().trim()
    file.parentFile.mkdirs()
    val id = UUID.randomUUID().toString()
    file.writeText(id)
    return id
}

fun main() {
    val db = openDatabase()
    bootstrapIfEmpty(db)

    val deviceId = loadOrCreateDeviceId()
    val operationStore = SqlDelightOperationStore(db)
    val repository = SqlDelightRepository(db, operationStore, deviceId, HlcClock(deviceId))
    val services = AppServices(repository, deviceId)

    val server = startServer(services, DEFAULT_PORT)
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(gracePeriodMillis = 500, timeoutMillis = 2000) })

    application {
        Window(onCloseRequest = ::exitApplication, title = "Expense Tracker") {
            DashboardApp(ApiClient("http://localhost:$DEFAULT_PORT"))
        }
    }
}
