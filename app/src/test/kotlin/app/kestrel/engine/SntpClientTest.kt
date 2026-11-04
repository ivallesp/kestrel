package app.kestrel.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class SntpClientTest {
    /** A phone whose clock runs [systemSkewMillis] fast, talking to an NTP server that keeps true time. */
    private class Network {
        var trueUtcMillis = BASE_UTC
        var elapsedMillis = BASE_ELAPSED
        var systemSkewMillis = 1_500L
        var oneWayDelayMillis = 20L
        var serverProcessingMillis = 1L
        var answers = true
        var changeReply: (ByteArray) -> Unit = {}
        var lastRequest: ByteArray? = null

        fun advance(millis: Long) {
            trueUtcMillis += millis
            elapsedMillis += millis
        }

        val transport =
            object : DatagramTransport {
                override fun connect(
                    host: String,
                    port: Int,
                    replyTimeoutMillis: Int,
                ): DatagramConnection {
                    assertEquals(123, port)
                    return object : DatagramConnection {
                        override fun send(bytes: ByteArray) {
                            lastRequest = bytes.copyOf()
                        }

                        override fun receive(maximumBytes: Int): ByteArray? {
                            if (!answers) return null
                            advance(oneWayDelayMillis)
                            val received = trueUtcMillis
                            advance(serverProcessingMillis)
                            val sent = trueUtcMillis
                            advance(oneWayDelayMillis)
                            val reply = ByteArray(NtpPacket.SIZE)
                            reply[0] = 0x24 // leap indicator 0, version 4, mode 4 (server)
                            reply[1] = 1
                            NtpPacket.writeTimestamp(
                                reply,
                                NtpPacket.REFERENCE_OFFSET,
                                NtpTime.fromUnixMillis(received - 1_000, 0),
                            )
                            System.arraycopy(
                                lastRequest!!,
                                NtpPacket.TRANSMIT_OFFSET,
                                reply,
                                NtpPacket.ORIGINATE_OFFSET,
                                8,
                            )
                            NtpPacket.writeTimestamp(
                                reply,
                                NtpPacket.RECEIVE_OFFSET,
                                NtpTime.fromUnixMillis(received, 0),
                            )
                            NtpPacket.writeTimestamp(reply, NtpPacket.TRANSMIT_OFFSET, NtpTime.fromUnixMillis(sent, 0))
                            changeReply(reply)
                            return reply
                        }

                        override fun close() = Unit
                    }
                }
            }

        fun client(): SntpClient =
            SntpClient(
                { trueUtcMillis + systemSkewMillis },
                { elapsedMillis },
                transport,
                Random(7),
                Dispatchers.Unconfined,
            )
    }

    private fun verdictWith(change: (ByteArray) -> Unit): SntpVerdict {
        val network = Network()
        network.changeReply = change
        return network.client().exchangeBlocking("time.google.com")
    }

    @Test
    fun validReplyGivesOffsetAndRoundTrip() {
        val network = Network()

        val verdict = network.client().exchangeBlocking("time.google.com")

        val sample = (verdict as SntpVerdict.Valid).sample
        assertEquals(-1_500L, sample.offsetMillis)
        assertEquals(40L, sample.roundTripMillis)
    }

    @Test
    fun requestIsAnNtpV4ClientPacketStampedWithThePhoneClock() {
        val network = Network()

        network.client().exchangeBlocking("time.google.com")

        val request = network.lastRequest!!
        assertEquals(48, request.size)
        assertEquals(0x23, request[0].toInt())
        val stamped = NtpTime.toUnixMicros(NtpPacket.readTimestamp(request, NtpPacket.TRANSMIT_OFFSET)) / 1_000
        assertTrue(abs(stamped - (BASE_UTC + 1_500)) <= 1)
    }

    @Test
    fun unsynchronisedServerIsRejected() {
        val verdict = verdictWith { it[0] = (0xC0 or 0x24).toByte() }
        assertTrue(verdict is SntpVerdict.Rejected)
    }

    @Test
    fun wrongModeIsRejected() {
        val verdict = verdictWith { it[0] = 0x23 }
        assertTrue(verdict is SntpVerdict.Rejected)
    }

    @Test
    fun badStratumIsRejected() {
        assertTrue(verdictWith { it[1] = 0 } is SntpVerdict.Rejected)
        assertTrue(verdictWith { it[1] = 16 } is SntpVerdict.Rejected)
    }

    @Test
    fun zeroTimestampsAreRejected() {
        assertTrue(verdictWith { NtpPacket.writeTimestamp(it, NtpPacket.TRANSMIT_OFFSET, 0) } is SntpVerdict.Rejected)
        assertTrue(verdictWith { NtpPacket.writeTimestamp(it, NtpPacket.RECEIVE_OFFSET, 0) } is SntpVerdict.Rejected)
        assertTrue(verdictWith { NtpPacket.writeTimestamp(it, NtpPacket.REFERENCE_OFFSET, 0) } is SntpVerdict.Rejected)
    }

    @Test
    fun replyToAnotherRequestIsRejected() {
        val verdict =
            verdictWith {
                it[NtpPacket.ORIGINATE_OFFSET + 7] =
                    (it[NtpPacket.ORIGINATE_OFFSET + 7] + 1).toByte()
            }
        assertTrue(verdict is SntpVerdict.Rejected)
    }

    @Test
    fun serverTimesOutOfOrderAreRejected() {
        val verdict =
            verdictWith {
                val received = NtpPacket.readTimestamp(it, NtpPacket.RECEIVE_OFFSET)
                NtpPacket.writeTimestamp(it, NtpPacket.TRANSMIT_OFFSET, received - (1L shl 32))
            }
        assertTrue(verdict is SntpVerdict.Rejected)
    }

    @Test
    fun shortReplyIsRejected() {
        val reply = ByteArray(20)
        val verdict = SntpClient.evaluateReply(reply, 1, BASE_UTC, BASE_ELAPSED, BASE_ELAPSED + 40)
        assertTrue(verdict is SntpVerdict.Rejected)
    }

    @Test
    fun missingReplyIsNoReply() {
        val network = Network()
        network.answers = false

        assertTrue(network.client().exchangeBlocking("time.google.com") is SntpVerdict.NoReply)
    }

    @Test
    fun queryReturnsTheSampleOrNull() =
        runTest {
            val network = Network()
            assertEquals(40L, network.client().query("time.google.com")?.roundTripMillis)

            network.changeReply = { it[1] = 0 }
            assertNull(network.client().query("time.google.com"))
        }

    @Test
    fun timestampsSurviveTheRoundTripIncludingTheNextEra() {
        for (unixMillis in listOf(BASE_UTC, BASE_UTC + 123, 2_085_978_496_000L + 5_000, 0L + 1_000_000_000_000L)) {
            val back = NtpTime.toUnixMicros(NtpTime.fromUnixMillis(unixMillis, Int.MAX_VALUE)) / 1_000
            assertEquals(unixMillis, back)
        }
    }
}
