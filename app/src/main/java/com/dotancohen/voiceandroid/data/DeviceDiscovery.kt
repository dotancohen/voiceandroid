package com.dotancohen.voiceandroid.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.dotancohen.voiceandroid.util.AppLogger
import java.security.MessageDigest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Finding each other on the local network (Stage 7): a listener announces
 * `_voicesync._tcp` with the SHA-256 of its account id, never the id, and
 * its certificate fingerprint; a caller browses and compares hashes. The
 * remembered address is tried first; the browse runs when it fails.
 */
class DeviceDiscovery(context: Context) {
    private val app = context.applicationContext
    private val nsd = app.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registration: NsdManager.RegistrationListener? = null
    private var lock: WifiManager.MulticastLock? = null

    /** Announce this phone's listener while it runs. */
    fun announce(port: Int, accountId: String, thisDeviceId: String, thisDeviceName: String, fingerprint: String) {
        stopAnnouncing()
        val info = NsdServiceInfo().apply {
            serviceName = "voice-" + thisDeviceId.take(12)
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("v", "1")
            setAttribute("a", accountHash(accountId))
            setAttribute("d", thisDeviceId)
            setAttribute("n", thisDeviceName.take(60))
            setAttribute("f", fingerprint)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) { AppLogger.i(TAG, "Announced as ${serviceInfo.serviceName}") }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { AppLogger.w(TAG, "Not announced: error $errorCode") }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registration = listener
        acquireLock()
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stopAnnouncing() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
        releaseLock()
    }

    /** One device of the account, by id: where it listens, or null within the timeout. */
    suspend fun find(accountId: String, deviceId: String, timeoutMs: Long = 3000): Found? {
        val wanted = accountHash(accountId)
        acquireLock()
        try {
            return withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    var discovery: NsdManager.DiscoveryListener? = null
                    val resolve = object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val attributes = serviceInfo.attributes.mapValues { (_, bytes) -> bytes?.let { String(it, Charsets.UTF_8) } ?: "" }
                            if (attributes["a"] != wanted || attributes["d"] != deviceId) return
                            val host = serviceInfo.host?.hostAddress ?: return
                            val url = if (host.contains(":")) "https://[$host]:${serviceInfo.port}" else "https://$host:${serviceInfo.port}"
                            discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
                            if (continuation.isActive) continuation.resume(Found(deviceId, attributes["n"] ?: "", url, attributes["f"] ?: ""))
                        }
                    }
                    discovery = object : NsdManager.DiscoveryListener {
                        override fun onDiscoveryStarted(serviceType: String) {}
                        override fun onDiscoveryStopped(serviceType: String) {}
                        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { if (continuation.isActive) continuation.resume(null) }
                        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                            if (serviceInfo.serviceType.trimEnd('.') == SERVICE_TYPE.trimEnd('.')) {
                                @Suppress("DEPRECATION")
                                nsd.resolveService(serviceInfo, resolve)
                            }
                        }
                        override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                    }
                    nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
                    continuation.invokeOnCancellation { discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } } }
                }
            }
        } finally {
            if (registration == null) releaseLock()
        }
    }

    private fun acquireLock() {
        if (lock == null) {
            val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            lock = wifi.createMulticastLock("voice-discovery").apply { setReferenceCounted(false); acquire() }
        }
    }

    private fun releaseLock() {
        lock?.let { if (it.isHeld) it.release() }
        lock = null
    }

    /** A device found on the network. */
    data class Found(val deviceId: String, val name: String, val url: String, val certificateFingerprint: String)

    companion object {
        private const val TAG = "DeviceDiscovery"
        const val SERVICE_TYPE = "_voicesync._tcp."

        /** The SHA-256 of the account id, lowercase hex, as the broadcast carries it. */
        fun accountHash(accountId: String): String =
            MessageDigest.getInstance("SHA-256").digest(accountId.trim().lowercase().toByteArray(Charsets.US_ASCII)).joinToString("") { "%02x".format(it) }

        /** The sentences a network failure carries, as the core words them; a refusal is none of these. */
        fun looksUnreachable(message: String?): Boolean =
            message != null && listOf("does not answer", "error sending request", "Connection refused", "connect", "timed out", "Could not reach").any { message.contains(it) }
    }
}
