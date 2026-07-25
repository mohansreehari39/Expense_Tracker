package et.windows.ui

import java.net.Inet4Address
import java.net.NetworkInterface

/** Adapter names/descriptions that are never the real LAN-facing NIC, even though the JVM doesn't flag them as [NetworkInterface.isVirtual] — e.g. Windows' Hyper-V vEthernet switch used for WSL. */
private val VIRTUAL_NAME_PATTERN = Regex(
    "virtual|vethernet|hyper-v|docker|wsl|vmware|virtualbox|tailscale|zerotier|tap\\d|tun\\d|loopback",
    RegexOption.IGNORE_CASE,
)

/**
 * Best-effort guess at this machine's LAN-reachable IPv4 address, for
 * embedding in the join QR so a scanning phone knows where to connect —
 * there's no service discovery (JmDNS/mDNS) wired up yet, see
 * Implementation/Windows/README.md. Prefers a real physical/Wi-Fi adapter
 * over virtual ones by name, since [NetworkInterface.isVirtual] doesn't
 * catch Hyper-V's "vEthernet (WSL ...)" switch — that adapter is up,
 * non-loopback, and reports isVirtual=false, but a phone on the real LAN
 * can never reach it, only this machine's real NIC. On a multi-NIC machine
 * this can still guess wrong, so it's a starting point, not a guarantee.
 */
fun localNetworkAddress(): String {
    val candidates = NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { iface -> iface.inetAddresses.asSequence().filterIsInstance<Inet4Address>().map { iface to it } }
        .toList()

    val realNic = candidates.firstOrNull { (iface, _) ->
        !VIRTUAL_NAME_PATTERN.containsMatchIn(iface.name) && !VIRTUAL_NAME_PATTERN.containsMatchIn(iface.displayName)
    }
    return (realNic ?: candidates.firstOrNull())?.second?.hostAddress ?: "127.0.0.1"
}
