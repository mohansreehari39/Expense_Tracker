package et.windows.server

import et.windows.KharchaConfig
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun startServer(services: AppServices, port: Int = KharchaConfig.port): ApplicationEngine =
    embeddedServer(Netty, port = port, module = { expenseTrackerModule(services) }).start(wait = false)

private fun Application.expenseTrackerModule(services: AppServices) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(CORS) {
        anyHost() // LAN-only tool; requests now need real device credentials regardless (see installDeviceAuth below).
        allowHeader("Content-Type")
        allowHeader("X-Device-Id")
        allowHeader("X-Pairing-Key")
        allowMethod(HttpMethod.Post)
    }
    install(deviceAuthPlugin(services))
    routing {
        apiV1(services)
    }
}

/**
 * Requires a valid, currently-paired device's id + pairingKey (headers
 * `X-Device-Id`/`X-Pairing-Key`) on every api/v1 request except: (a)
 * device pairing itself (`POST /devices` — nothing to present a key for
 * yet, guarded instead by [PairingSession]'s one-time secret) and (b) the
 * heartbeat endpoint (has its own key check with its own 410 "revoked"
 * semantics Android depends on). Exempts requests from this same machine
 * (the Windows app's own dashboard UI, always loopback) since that's
 * already a trusted local process, not a remote device that could only
 * have gotten in by pairing. Closes the actual gap in v0: every household,
 * expense, etc. route had zero authentication before this — any device
 * that could merely reach the port was fully trusted.
 */
private fun deviceAuthPlugin(services: AppServices) = createApplicationPlugin("DeviceAuth") {
    onCall { call ->
        val request = call.request
        val path = request.path()
        if (!path.startsWith("/api/v1/")) return@onCall
        val remoteHost = request.origin.remoteHost
        if (remoteHost == "127.0.0.1" || remoteHost == "::1" || remoteHost == "0:0:0:0:0:0:0:1") return@onCall
        if (path == "/api/v1/devices" && request.httpMethod == HttpMethod.Post) return@onCall
        if (path.startsWith("/api/v1/devices/") && path.endsWith("/heartbeat")) return@onCall

        val deviceId = request.header("X-Device-Id")
        val pairingKey = request.header("X-Pairing-Key")
        val valid = deviceId != null && pairingKey != null && services.pairedDevices.isValid(deviceId, pairingKey)
        if (!valid) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "missing or invalid device credentials"))
        }
    }
}
