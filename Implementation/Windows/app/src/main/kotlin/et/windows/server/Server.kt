package et.windows.server

import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

const val DEFAULT_PORT = 47321

fun startServer(services: AppServices, port: Int = DEFAULT_PORT): ApplicationEngine =
    embeddedServer(Netty, port = port, module = { expenseTrackerModule(services) }).start(wait = false)

private fun Application.expenseTrackerModule(services: AppServices) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(CORS) {
        anyHost() // LAN-only tool; tighten once pairing/auth exists (Design/Core/04-pairing-and-crypto.md).
        allowHeader("Content-Type")
        allowMethod(HttpMethod.Post)
    }
    routing {
        apiV1(services)
    }
}
