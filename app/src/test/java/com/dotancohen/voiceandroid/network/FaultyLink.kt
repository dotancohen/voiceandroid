package com.dotancohen.voiceandroid.network

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * A network link that fails on command, for testing the phone's storage and
 * sync the way phones really lose their connections. The same link as the
 * desktop's tests/faulty_network.py.
 *
 * A TCP proxy on this computer between the core's client and a server. Point
 * the client at [url] instead of the server, then say how the link fails:
 * [refuse], [cutAfter], [freezeAfter], [stall], [throttle], or [passThrough]
 * to work again. A fault applies to the connections accepted after it is set.
 */
class FaultyLink(private val targetPort: Int, private val targetHost: String = "127.0.0.1") : AutoCloseable {
    private sealed class Fault {
        data class Cut(val bytesDown: Long?, val bytesUp: Long?) : Fault()
        data class Freeze(val bytesDown: Long?, val bytesUp: Long?) : Fault()
        data object Stall : Fault()
        data class Throttle(val bytesPerSecond: Long) : Fault()
    }

    @Volatile private var fault: Fault? = null
    @Volatile private var listener: ServerSocket? = null
    private val stopping = AtomicBoolean(false)
    private val open = CopyOnWriteArrayList<Socket>()
    val bytesUp = AtomicLong(0)
    val bytesDown = AtomicLong(0)
    var port: Int = 0
        private set

    init {
        listen(0)
    }

    val url: String get() = "http://127.0.0.1:$port"

    private fun listen(onPort: Int) {
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), onPort))
        socket.soTimeout = 200
        listener = socket
        port = socket.localPort
        thread(isDaemon = true, name = "faulty-link-accept") { acceptLoop(socket) }
    }

    fun passThrough(): FaultyLink {
        fault = null
        if (listener == null && !stopping.get()) listen(port)
        return this
    }

    /** Close the port: the next connections are refused by the system. */
    fun refuse(): FaultyLink {
        fault = null
        listener?.close()
        listener = null
        Thread.sleep(300)
        return this
    }

    /** Reset a connection once that many bytes have crossed in that direction. */
    fun cutAfter(bytesDown: Long? = null, bytesUp: Long? = null): FaultyLink {
        fault = Fault.Cut(bytesDown, bytesUp)
        return this
    }

    /** After that many bytes nothing more is forwarded or read, and nothing is closed. */
    fun freezeAfter(bytesDown: Long? = null, bytesUp: Long? = null): FaultyLink {
        fault = Fault.Freeze(bytesDown, bytesUp)
        return this
    }

    /** Accept connections and never answer. */
    fun stall(): FaultyLink {
        fault = Fault.Stall
        return this
    }

    /** Every byte gets through, slowly. */
    fun throttle(bytesPerSecond: Long): FaultyLink {
        fault = Fault.Throttle(bytesPerSecond)
        return this
    }

    override fun close() {
        stopping.set(true)
        listener?.close()
        listener = null
        open.forEach { reset(it) }
        open.clear()
    }

    private fun reset(socket: Socket) {
        try {
            socket.setSoLinger(true, 0)
        } catch (_: IOException) {
        }
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!stopping.get()) {
            val client = try {
                socket.accept()
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: IOException) {
                return
            }
            open += client
            val snapshot = fault
            thread(isDaemon = true, name = "faulty-link-connection") { serve(client, snapshot) }
        }
    }

    private fun serve(client: Socket, snapshot: Fault?) {
        if (snapshot is Fault.Stall) {
            client.soTimeout = 500
            val buffer = ByteArray(64 * 1024)
            while (!stopping.get()) {
                try {
                    if (client.getInputStream().read(buffer) < 0) break
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: IOException) {
                    break
                }
            }
            reset(client)
            return
        }
        val server = try {
            Socket(targetHost, targetPort)
        } catch (_: IOException) {
            reset(client)
            return
        }
        open += server
        val ended = AtomicBoolean(false)
        val (upLimit, downLimit, freeze) = when (snapshot) {
            is Fault.Cut -> Triple(snapshot.bytesUp, snapshot.bytesDown, false)
            is Fault.Freeze -> Triple(snapshot.bytesUp, snapshot.bytesDown, true)
            else -> Triple(null, null, false)
        }
        val rate = (snapshot as? Fault.Throttle)?.bytesPerSecond
        fun pump(from: Socket, to: Socket, upward: Boolean, limit: Long?) {
            val buffer = ByteArray(64 * 1024)
            var moved = 0L
            try {
                while (!ended.get()) {
                    val n = from.getInputStream().read(buffer)
                    if (n < 0) break
                    if (limit != null && moved + n >= limit) {
                        val keep = (limit - moved).toInt().coerceAtLeast(0)
                        if (keep > 0) {
                            to.getOutputStream().write(buffer, 0, keep)
                            (if (upward) bytesUp else bytesDown).addAndGet(keep.toLong())
                        }
                        if (freeze) {
                            while (!ended.get() && !stopping.get()) Thread.sleep(200)
                            return
                        }
                        break
                    }
                    if (rate != null) Thread.sleep(n * 1000L / rate)
                    to.getOutputStream().write(buffer, 0, n)
                    moved += n
                    (if (upward) bytesUp else bytesDown).addAndGet(n.toLong())
                }
            } catch (_: IOException) {
            } finally {
                ended.set(true)
                reset(client)
                reset(server)
            }
        }
        thread(isDaemon = true, name = "faulty-link-up") { pump(client, server, true, upLimit) }
        thread(isDaemon = true, name = "faulty-link-down") { pump(server, client, false, downLimit) }
    }
}
