package et.android.kharcha.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val SERVICE_TYPE = "_expensetracker._tcp."

data class DiscoveredServer(val host: String, val port: Int) {
    val baseUrl: String get() = "http://$host:$port"
}

/**
 * Finds a Kharcha Windows server on the LAN via mDNS/NSD — matches the
 * Windows side's advertisement (`_expensetracker._tcp.local.`, see
 * `Implementation/Windows/.../server/LanAdvertiser.kt`). Used so the app
 * never has to trust a stale IP: every connect re-resolves the server's
 * *current* address, which is what actually solves the "the Windows PC's
 * IP changed" problem, rather than a QR scanned once and never updated.
 * Returns null if nothing answers within [timeoutMs] — some networks
 * block multicast, or the Windows app isn't running; callers should fall
 * back to a manually-entered address (debug builds only) or the QR's own
 * embedded address hint.
 */
suspend fun discoverKharchaServer(context: Context, timeoutMs: Long = 6000): DiscoveredServer? =
    withTimeoutOrNull(timeoutMs) {
        val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        // Without this, some devices silently drop multicast packets while
        // the wifi radio is in a power-saving state, making discovery flaky.
        val multicastLock = wifiManager?.createMulticastLock("kharcha-nsd")?.apply {
            setReferenceCounted(true)
            runCatching { acquire() }
        }

        suspendCancellableCoroutine { continuation ->
            lateinit var discoveryListener: NsdManager.DiscoveryListener

            fun finish(value: DiscoveredServer?) {
                if (continuation.isActive) {
                    runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
                    runCatching { multicastLock?.release() }
                    continuation.resume(value)
                }
            }

            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = finish(null)
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress
                    finish(if (host != null) DiscoveredServer(host, serviceInfo.port) else null)
                }
            }

            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    runCatching { nsdManager.resolveService(serviceInfo, resolveListener) }
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = finish(null)
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            }

            continuation.invokeOnCancellation {
                runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
                runCatching { multicastLock?.release() }
            }

            runCatching { nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener) }
                .onFailure { finish(null) }
        }
    }
