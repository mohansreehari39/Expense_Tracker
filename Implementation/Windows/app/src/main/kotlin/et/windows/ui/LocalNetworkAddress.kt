package et.windows.ui

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Best-effort guess at this machine's LAN-reachable IPv4 address, for
 * embedding in the join QR so a scanning phone knows where to connect —
 * there's no service discovery (JmDNS/mDNS) wired up yet, see
 * Implementation/Windows/README.md. Picks the first non-loopback,
 * non-virtual IPv4 address; on a multi-NIC machine (VPN, Docker, WSL) this
 * can guess wrong, so it's a starting point, not a guarantee.
 */
fun localNetworkAddress(): String =
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull()
        ?.hostAddress
        ?: "127.0.0.1"
