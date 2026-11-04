package app.kestrel.ebay

import app.kestrel.model.Money
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream

/** Archived eBay pages and AUTO_REFRESH replies in src/test/resources/fixtures (Wayback raw captures). */
object Fixtures {
    /** Item page file names without ".html.gz", for example "20260427110748_127820098965". */
    fun itemPageNames(): List<String> =
        directory("item")
            .listFiles { file -> file.name.endsWith(".html.gz") }!!
            .map { it.name.removeSuffix(".html.gz") }
            .sorted()

    fun itemPage(name: String): String =
        GZIPInputStream(
            File(directory("item"), "$name.html.gz").inputStream(),
        ).use { it.readBytes().toString(Charsets.UTF_8) }

    fun autoRefreshFiles(): List<File> =
        directory("autorefresh")
            .listFiles { file -> file.name.endsWith(".json") }!!
            .sortedBy { it.name }

    fun autoRefresh(name: String): String = File(directory("autorefresh"), "$name.json").readText(Charsets.UTF_8)

    /** The capture time encoded in a fixture name ("20260427110806_..."). */
    fun captureMillis(name: String): Long =
        LocalDateTime.parse(name.take(14), CAPTURE_STAMP).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun directory(kind: String): File {
        val url =
            Fixtures::class.java.getResource("/fixtures/$kind") ?: error("fixtures/$kind is not on the test classpath")
        return File(url.toURI())
    }

    private val CAPTURE_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
}

fun utc(iso: String): Long = Instant.parse(iso).toEpochMilli()

fun gbp(amount: String): Money = Money.of(amount, "GBP")

fun usd(amount: String): Money = Money.of(amount, "USD")

fun eur(amount: String): Money = Money.of(amount, "EUR")
