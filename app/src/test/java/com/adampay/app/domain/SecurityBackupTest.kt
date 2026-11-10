package com.adampay.app.domain

import com.adampay.app.core.backup.BackupPlan
import com.adampay.app.core.backup.BackupWorker
import com.adampay.app.core.backup.DriveFile
import com.adampay.app.core.backup.Manifest
import com.adampay.app.core.backup.Snapshot
import com.adampay.app.core.backup.TableChunk
import com.adampay.app.domain.security.LockPolicy
import com.adampay.app.domain.security.LockTimeout
import com.adampay.app.domain.security.Pin
import com.adampay.app.domain.security.PinThrottle
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class SecurityBackupTest {

    /* ---------------- PIN ---------------- */

    @Test fun `pin is stored salted and only the right one matches`() {
        val a = Pin.hash("2580", iterations = 1000)
        val b = Pin.hash("2580", iterations = 1000)
        assertNotEquals(a, b) // different salt every time
        assertFalse(a.contains("2580"))
        assertTrue(Pin.matches("2580", a))
        assertTrue(Pin.matches("2580", b))
        assertFalse(Pin.matches("2581", a))
        assertFalse(Pin.matches("0852", a))
    }

    @Test fun `default hash is slow pbkdf2 and still verifies`() {
        val h = Pin.hash("1234")
        assertTrue(h.startsWith("pbkdf2\$120000\$"))
        assertTrue(Pin.matches("1234", h))
    }

    @Test fun `broken or missing stored pin never matches`() {
        for (bad in listOf(null, "", "1234", "pbkdf2\$x\$a\$b", "sha1\$1\$AAAA\$AAAA", "pbkdf2\$1000\$!!!\$AAAA")) assertFalse(Pin.matches("1234", bad))
    }

    @Test fun `only four digits are a valid pin`() {
        assertTrue(Pin.isValid("0000"))
        assertFalse(Pin.isValid("123"))
        assertFalse(Pin.isValid("12345"))
        assertFalse(Pin.isValid("12a4"))
        assertFalse(Pin.isValid("١٢٣٤")) // Arabic-Indic digits come from the keypad as 0–9 only
    }

    @Test fun `five free tries then a growing wait capped at 15 minutes`() {
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), (0..4).map(PinThrottle::waitAfter))
        assertEquals(30_000L, PinThrottle.waitAfter(5))
        assertEquals(60_000L, PinThrottle.waitAfter(6))
        assertEquals(120_000L, PinThrottle.waitAfter(7))
        assertEquals(15 * 60_000L, PinThrottle.waitAfter(20))
        assertEquals(15 * 60_000L, PinThrottle.waitAfter(Int.MAX_VALUE))
        assertEquals(4, PinThrottle.triesLeft(1))
        assertEquals(0, PinThrottle.triesLeft(9))
    }

    @Test fun `coming back locks only after the chosen time away`() {
        assertFalse(LockPolicy.shouldLock(LockTimeout.IMMEDIATELY, null, 10_000)) // never left
        assertTrue(LockPolicy.shouldLock(LockTimeout.IMMEDIATELY, 10_000, 10_000))
        assertFalse(LockPolicy.shouldLock(LockTimeout.ONE_MINUTE, 10_000, 69_999))
        assertTrue(LockPolicy.shouldLock(LockTimeout.ONE_MINUTE, 10_000, 70_000))
        assertFalse(LockPolicy.shouldLock(LockTimeout.FIVE_MINUTES, 0, 299_999))
        assertTrue(LockPolicy.shouldLock(LockTimeout.FIVE_MINUTES, 0, 300_000))
    }

    /* ---------------- Backup chunks ---------------- */

    private fun snap(rows: Int, extra: String = "x", at: Long = 1000) = Snapshot(
        dbVersion = 1, appVersion = "t", createdAt = at,
        tables = (0 until rows).chunked(Snapshot.CHUNK_ROWS).mapIndexed { i, r ->
            TableChunk("txn", i, listOf("id", "amount_fils", "note"), r.map { listOf(JsonPrimitive(it), JsonPrimitive(it * 250L), if (it == rows - 1) JsonPrimitive(extra) else JsonNull) })
        } + TableChunk("pocket", 0, listOf("id", "name_ar"), listOf(listOf(JsonPrimitive(1), JsonPrimitive("في جيبي")))),
        settings = Snapshot.encodeSettings(mapOf("language" to "MIXED", "haptics" to true, "accent_argb" to 4281316205L, "decimals" to 3, "f" to 0.5f, "s" to setOf("a"))),
    )

    @Test fun `encode then decode gives the same snapshot`() {
        val s = snap(1200)
        val e = BackupPlan.encode(s)
        val back = BackupPlan.decode(e.manifest, e.files)
        assertEquals(s.tables, back.tables)
        assertEquals(s.settings, back.settings)
        assertEquals(
            mapOf("language" to "MIXED", "haptics" to true, "accent_argb" to 4281316205L, "decimals" to 3, "f" to 0.5f, "s" to setOf("a")),
            Snapshot.decodeSettings(back.settings),
        )
        assertEquals(3 + 1 + 1, e.files.size) // 3 txn parts, pockets, settings
    }

    @Test fun `only the changed chunk gets a new name (incremental upload)`() {
        val a = BackupPlan.encode(snap(1200, "x", at = 1)).manifest
        val b = BackupPlan.encode(snap(1200, "y", at = 2)).manifest
        assertEquals(a.chunks.take(2), b.chunks.take(2))
        assertNotEquals(a.chunks[2], b.chunks[2])
        assertEquals(a.chunks[3], b.chunks[3])
        assertEquals(BackupPlan.refs(a), BackupPlan.refs(BackupPlan.encode(snap(1200, "x", at = 99)).manifest))
    }

    @Test fun `backup file names`() {
        assertEquals("m-1760000000000.json", BackupPlan.manifestName(1760000000000))
        assertEquals(1760000000000, BackupPlan.createdAt("m-1760000000000.json"))
        assertNull(BackupPlan.createdAt("c-abc.gz"))
        assertNull(BackupPlan.createdAt("m-x.json"))
    }

    /* ---------------- 3 GB rule ---------------- */

    private val now = Instant.parse("2026-10-17T21:00:00Z")
    private fun m(name: String, daysAgo: Long, vararg chunks: String) =
        name to Manifest(createdAt = now.minus(daysAgo, ChronoUnit.DAYS).toEpochMilli(), dbVersion = 1, appVersion = "", chunks = chunks.toList(), settings = "c-s.gz")
    private fun f(name: String, size: Long) = DriveFile(name, name, size, Instant.EPOCH)

    @Test fun `under the cap nothing is deleted`() {
        val manifests = mapOf(m("m-1.json", 90, "c-a.gz"), m("m-2.json", 1, "c-b.gz"))
        val files = listOf(f("m-1.json", 1), f("m-2.json", 1), f("c-a.gz", 50), f("c-b.gz", 50), f("c-s.gz", 1))
        assertTrue(BackupPlan.prune(files, manifests, now, cap = 1000).isEmpty())
    }

    @Test fun `over the cap old backups go oldest first and the newest always stays`() {
        val manifests = mapOf(m("m-1.json", 90, "c-a.gz"), m("m-2.json", 60, "c-b.gz"), m("m-3.json", 40, "c-c.gz"), m("m-4.json", 1, "c-c.gz", "c-d.gz"))
        val files = listOf(
            f("m-1.json", 1), f("m-2.json", 1), f("m-3.json", 1), f("m-4.json", 1), f("c-s.gz", 1),
            f("c-a.gz", 400), f("c-b.gz", 400), f("c-c.gz", 300), f("c-d.gz", 100), f("c-orphan.gz", 50),
        )
        assertEquals(setOf("c-orphan.gz", "m-1.json", "c-a.gz"), BackupPlan.prune(files, manifests, now, cap = 900).map { it.name }.toSet())
        assertEquals(
            setOf("c-orphan.gz", "m-1.json", "c-a.gz", "m-2.json", "c-b.gz", "m-3.json"),
            BackupPlan.prune(files, manifests, now, cap = 10).map { it.name }.toSet(),
        )
    }

    @Test fun `backups younger than a month are never pruned`() {
        val manifests = mapOf(m("m-1.json", 20, "c-a.gz"), m("m-2.json", 1, "c-b.gz"))
        val files = listOf(f("m-1.json", 1), f("m-2.json", 1), f("c-a.gz", 500), f("c-b.gz", 500), f("c-s.gz", 1))
        assertTrue(BackupPlan.prune(files, manifests, now, cap = 10).isEmpty())
    }

    @Test fun `nightly backup waits until 23 30`() {
        val at = LocalTime.of(23, 30)
        assertEquals(30, BackupWorker.delayUntil(LocalDateTime.of(2026, 10, 17, 23, 0), at))
        assertEquals(23 * 60 + 30, BackupWorker.delayUntil(LocalDateTime.of(2026, 10, 17, 0, 0), at))
        assertEquals(24 * 60, BackupWorker.delayUntil(LocalDateTime.of(2026, 10, 17, 23, 30), at))
    }
}
