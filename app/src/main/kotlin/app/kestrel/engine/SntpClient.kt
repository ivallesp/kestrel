package app.kestrel.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * A plain SNTP client (RFC 4330): one 48-byte NTPv4 client request over UDP and a validated reply, giving the phone
 * clock's offset and the round trip. Times on the phone side come from [systemUtcMillis] for the request and from
 * [elapsedRealtimeMillis] for the interval, so a wall-clock change during the exchange cannot skew the result.
 */
class SntpClient internal constructor(
    private val systemUtcMillis: () -> Long,
    private val elapsedRealtimeMillis: () -> Long,
    private val transport: DatagramTransport,
    private val random: Random,
    ioDispatcher: CoroutineDispatcher,
) {
    constructor(
        systemUtcMillis: () -> Long,
        elapsedRealtimeMillis: () -> Long,
    ) : this(systemUtcMillis, elapsedRealtimeMillis, UdpTransport, Random.Default, Dispatchers.IO)

    // DNS lookups cannot be interrupted, so the exchange runs outside the caller's scope and the caller stops waiting
    // after QUERY_TIMEOUT_MILLIS instead of being held by a stuck lookup.
    private val detachedScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /** One SNTP exchange with [host]; null on any network error, timeout or invalid reply. */
    suspend fun query(host: String): SntpSample? {
        val exchange = detachedScope.async { exchangeBlocking(host) }
        return try {
            (withTimeoutOrNull(QUERY_TIMEOUT_MILLIS) { exchange.await() } as? SntpVerdict.Valid)?.sample
        } finally {
            exchange.cancel()
        }
    }

    internal fun exchangeBlocking(host: String): SntpVerdict {
        try {
            val connection =
                transport.connect(host, NTP_PORT, REPLY_TIMEOUT_MILLIS)
                    ?: return SntpVerdict.NoReply("could not resolve $host")
            connection.use {
                val requestUtcMillis = systemUtcMillis()
                val transmitTimestamp = NtpTime.fromUnixMillis(requestUtcMillis, random.nextInt())
                val sentAtElapsedMillis = elapsedRealtimeMillis()
                it.send(NtpPacket.request(transmitTimestamp))
                val reply =
                    it.receive(MAXIMUM_REPLY_BYTES)
                        ?: return SntpVerdict.NoReply("no reply within $REPLY_TIMEOUT_MILLIS ms")
                val receivedAtElapsedMillis = elapsedRealtimeMillis()
                return evaluateReply(
                    reply,
                    transmitTimestamp,
                    requestUtcMillis,
                    sentAtElapsedMillis,
                    receivedAtElapsedMillis,
                )
            }
        } catch (error: IOException) {
            return SntpVerdict.NoReply(error.message ?: "network error")
        } catch (error: SecurityException) {
            return SntpVerdict.NoReply(error.message ?: "network not allowed")
        }
    }

    internal companion object {
        const val NTP_PORT = 123
        const val REPLY_TIMEOUT_MILLIS = 1_000
        const val QUERY_TIMEOUT_MILLIS = 3_000L
        private const val MAXIMUM_REPLY_BYTES = 1_024
        private const val LEAP_NOT_SYNCHRONISED = 3
        private const val MODE_SERVER = 4
        private const val HIGHEST_VALID_STRATUM = 15

        // Millisecond rounding on the phone side can make a very fast exchange look a little negative.
        private const val NEGATIVE_ROUND_TRIP_TOLERANCE_MICROS = 2_000L

        /** Checks a reply and computes offset = ((t1 - t0) + (t2 - t3)) / 2 and round trip = (t3 - t0) - (t2 - t1). */
        fun evaluateReply(
            reply: ByteArray,
            requestTransmitTimestamp: Long,
            requestUtcMillis: Long,
            sentAtElapsedMillis: Long,
            receivedAtElapsedMillis: Long,
        ): SntpVerdict {
            if (reply.size < NtpPacket.SIZE) return SntpVerdict.Rejected("reply too short")
            val firstByte = reply[0].toInt() and 0xFF
            val leapIndicator = firstByte ushr 6
            val mode = firstByte and 0x7
            val stratum = reply[1].toInt() and 0xFF
            if (leapIndicator == LEAP_NOT_SYNCHRONISED) return SntpVerdict.Rejected("server clock not synchronised")
            if (mode != MODE_SERVER) return SntpVerdict.Rejected("not a server reply (mode $mode)")
            if (stratum !in 1..HIGHEST_VALID_STRATUM) return SntpVerdict.Rejected("unusable stratum $stratum")
            val reference = NtpPacket.readTimestamp(reply, NtpPacket.REFERENCE_OFFSET)
            val originate = NtpPacket.readTimestamp(reply, NtpPacket.ORIGINATE_OFFSET)
            val receive = NtpPacket.readTimestamp(reply, NtpPacket.RECEIVE_OFFSET)
            val transmit = NtpPacket.readTimestamp(reply, NtpPacket.TRANSMIT_OFFSET)
            if (reference == 0L || receive == 0L || transmit == 0L) return SntpVerdict.Rejected("zero timestamp")
            if (originate != requestTransmitTimestamp) return SntpVerdict.Rejected("reply does not answer this request")
            val clientSent = requestUtcMillis * 1_000
            val clientReceived = (requestUtcMillis + (receivedAtElapsedMillis - sentAtElapsedMillis)) * 1_000
            val serverReceived = NtpTime.toUnixMicros(receive)
            val serverSent = NtpTime.toUnixMicros(transmit)
            if (serverSent < serverReceived) return SntpVerdict.Rejected("server answered before it received")
            val roundTripMicros = (clientReceived - clientSent) - (serverSent - serverReceived)
            if (roundTripMicros <
                -NEGATIVE_ROUND_TRIP_TOLERANCE_MICROS
            ) {
                return SntpVerdict.Rejected("negative round trip")
            }
            val offsetMicros = ((serverReceived - clientSent) + (serverSent - clientReceived)) / 2
            return SntpVerdict.Valid(
                SntpSample(
                    offsetMillis = (offsetMicros / 1_000.0).roundToLong(),
                    roundTripMillis = (roundTripMicros.coerceAtLeast(0) / 1_000.0).roundToLong(),
                ),
            )
        }
    }
}

/** What one SNTP exchange produced. */
internal sealed interface SntpVerdict {
    data class Valid(
        val sample: SntpSample,
    ) : SntpVerdict

    data class Rejected(
        val reason: String,
    ) : SntpVerdict

    data class NoReply(
        val reason: String,
    ) : SntpVerdict
}

/** The 48-byte NTP packet layout. */
internal object NtpPacket {
    const val SIZE = 48
    const val REFERENCE_OFFSET = 16
    const val ORIGINATE_OFFSET = 24
    const val RECEIVE_OFFSET = 32
    const val TRANSMIT_OFFSET = 40

    // Leap indicator 0, version 4, mode 3 (client).
    private const val CLIENT_FIRST_BYTE = 0x23

    fun request(transmitTimestamp: Long): ByteArray =
        ByteArray(SIZE).also {
            it[0] = CLIENT_FIRST_BYTE.toByte()
            writeTimestamp(it, TRANSMIT_OFFSET, transmitTimestamp)
        }

    fun readTimestamp(
        bytes: ByteArray,
        offset: Int,
    ): Long {
        var value = 0L
        for (index in offset until offset + 8) {
            value = (value shl 8) or (bytes[index].toLong() and 0xFF)
        }
        return value
    }

    fun writeTimestamp(
        bytes: ByteArray,
        offset: Int,
        timestamp: Long,
    ) {
        for (index in 0 until 8) {
            bytes[offset + index] = (timestamp ushr (56 - 8 * index)).toByte()
        }
    }
}

/** NTP's 64-bit timestamps: seconds since 1900 in the high 32 bits, a binary fraction of a second in the low 32. */
internal object NtpTime {
    private const val SECONDS_FROM_1900_TO_1970 = 2_208_988_800L
    private const val ERA_SECONDS = 1L shl 32
    private const val LOW_32_BITS = 0xFFFFFFFFL
    private const val HIGH_BIT_OF_SECONDS = 0x80000000L

    // Fraction bits worth less than a millisecond (2^20 / 2^32 s is about 0.24 ms).
    private const val SUB_MILLISECOND_BITS = 0xFFFFFL

    /**
     * The timestamp for [unixMillis]. The bits below a millisecond carry no time on a phone, so they are random: that
     * makes each request unique (a reply must echo it back) and does not leak the phone's clock.
     */
    fun fromUnixMillis(
        unixMillis: Long,
        randomBits: Int,
    ): Long {
        val unixSeconds = Math.floorDiv(unixMillis, 1_000L)
        val millisecond = Math.floorMod(unixMillis, 1_000L)
        val seconds = (unixSeconds + SECONDS_FROM_1900_TO_1970) and LOW_32_BITS
        val fraction = ((millisecond shl 32) / 1_000L) and LOW_32_BITS
        val randomised = (fraction and SUB_MILLISECOND_BITS.inv()) or (randomBits.toLong() and SUB_MILLISECOND_BITS)
        return (seconds shl 32) or (randomised and LOW_32_BITS)
    }

    fun toUnixMicros(timestamp: Long): Long {
        val seconds = timestamp ushr 32
        val fraction = timestamp and LOW_32_BITS
        // RFC 4330 section 3: with the top bit clear the time is in the era that starts on 2036-02-07.
        val secondsSince1900 = if (seconds and HIGH_BIT_OF_SECONDS != 0L) seconds else seconds + ERA_SECONDS
        val unixSeconds = secondsSince1900 - SECONDS_FROM_1900_TO_1970
        return unixSeconds * 1_000_000 + ((fraction * 1_000_000) ushr 32)
    }
}

/** Blocking UDP I/O behind SNTP, injected so the client can be tested without a network. */
internal interface DatagramTransport {
    /** Resolves [host] and opens a socket connected to it; null when the host cannot be resolved. */
    fun connect(
        host: String,
        port: Int,
        replyTimeoutMillis: Int,
    ): DatagramConnection?
}

internal interface DatagramConnection : Closeable {
    fun send(bytes: ByteArray)

    /** Waits for one datagram from the connected peer; null on timeout. */
    fun receive(maximumBytes: Int): ByteArray?
}

internal object UdpTransport : DatagramTransport {
    override fun connect(
        host: String,
        port: Int,
        replyTimeoutMillis: Int,
    ): DatagramConnection? {
        val address =
            try {
                InetAddress.getByName(host)
            } catch (unknownHost: UnknownHostException) {
                return null
            }
        val socket = DatagramSocket()
        return try {
            socket.soTimeout = replyTimeoutMillis
            // A connected socket only accepts datagrams from the server it asked.
            socket.connect(address, port)
            UdpConnection(socket)
        } catch (error: IOException) {
            socket.close()
            null
        }
    }
}

private class UdpConnection(
    private val socket: DatagramSocket,
) : DatagramConnection {
    override fun send(bytes: ByteArray) {
        socket.send(DatagramPacket(bytes, bytes.size))
    }

    override fun receive(maximumBytes: Int): ByteArray? {
        val buffer = ByteArray(maximumBytes)
        val packet = DatagramPacket(buffer, buffer.size)
        return try {
            socket.receive(packet)
            buffer.copyOf(packet.length)
        } catch (timeout: SocketTimeoutException) {
            null
        }
    }

    override fun close() {
        socket.close()
    }
}
