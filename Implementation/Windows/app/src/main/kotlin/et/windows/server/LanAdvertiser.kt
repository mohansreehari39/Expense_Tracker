package et.windows.server

import et.windows.KharchaConfig
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/** Matches Design/Windows/02-transport-implementation.md's discovery type, so a future real Transport can reuse it. */
const val SERVICE_TYPE = "_expensetracker._tcp.local."

/**
 * Advertises this server on the LAN via mDNS so the Android app can find
 * it without a manually-entered IP — solves both "QR-only in production"
 * (the QR no longer needs to carry a literal address) and the dynamic-IP
 * problem (Android re-resolves the live address each time it connects,
 * rather than trusting a cached one). Best-effort: some networks/firewalls
 * block multicast, in which case this quietly does nothing and the app
 * still works — manual entry remains available in debug builds as a
 * fallback, see `Implementation/Android/.../ui/ConnectScreen.kt`.
 */
fun advertiseOnLan(port: Int): JmDNS? = runCatching {
    val jmdns = JmDNS.create(InetAddress.getLocalHost())
    val serviceInfo = ServiceInfo.create(SERVICE_TYPE, KharchaConfig.serverDisplayName(), port, "Kharcha household expense tracker")
    jmdns.registerService(serviceInfo)
    jmdns
}.getOrNull()
