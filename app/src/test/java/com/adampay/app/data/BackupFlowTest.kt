package com.adampay.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adampay.app.core.auth.GoogleAuth
import com.adampay.app.core.backup.BackupEngine
import com.adampay.app.core.backup.DriveClient
import com.adampay.app.core.backup.Snapshot
import com.adampay.app.core.backup.SnapshotException
import com.adampay.app.core.backup.SnapshotStore
import com.adampay.app.core.security.AppLock
import com.adampay.app.data.account.AccountStore
import com.adampay.app.data.db.AdamDatabase
import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.db.TxnType
import com.adampay.app.data.settings.SettingsRepository
import com.adampay.app.data.wallet.WalletRepository
import com.adampay.app.domain.model.AppLanguage
import com.adampay.app.domain.security.LockTimeout
import com.adampay.app.domain.security.PinCheck
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The whole backup and restore against a fake Google Drive (an in-memory appDataFolder behind a local
 * HTTP server): real Room, real DataStore, real chunking and gzip, real multipart uploads.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class BackupFlowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Asia/Amman"))
    private lateinit var db: AdamDatabase
    private lateinit var server: MockWebServer
    private val drive = FakeDrive()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AdamDatabase::class.java).allowMainThreadQueries().build()
        server = MockWebServer().apply { dispatcher = drive; start() }
    }

    @After fun tearDown() {
        db.close()
        server.close()
    }

    @Test fun `back up, change, restore an older backup, then the newest on an empty phone`() = runBlocking {
        val http = OkHttpClient()
        val settings = SettingsRepository(context)
        val accounts = AccountStore(context)
        val snapshots = SnapshotStore(db, settings)
        val engine = BackupEngine(GoogleAuth(context, accounts, http), accounts, snapshots, http)
        val wallet = WalletRepository(db, clock)
        val client: suspend () -> DriveClient = {
            DriveClient(http, "test-token", server.url("/drive/v3").toString().trimEnd('/'), server.url("/upload/drive/v3").toString().trimEnd('/'))
        }

        wallet.ensureSeeded()
        val pocket = wallet.snapshot.first().pockets.first().id
        wallet.setOpeningBalances(mapOf(pocket to 20_000L))
        wallet.save(TxnEntity(type = TxnType.EXPENSE, amountFils = 350, fromPocketId = pocket, note = "Service", timestamp = clock.millis()))
        settings.setUserName("آدم")
        settings.setLanguage(AppLanguage.ENGLISH)
        settings.setOnboardingDone(true)

        // First backup uploads everything; a second one with no changes adds nothing.
        val first = engine.backup(client)
        assertTrue(first.newBackup)
        val filesAfterFirst = drive.files.size
        val again = engine.backup(client)
        assertFalse(again.newBackup)
        assertEquals(0L, again.uploadedBytes)
        assertEquals(filesAfterFirst, drive.files.size)
        assertTrue(drive.tokens.all { it == "Bearer test-token" })

        // A new expense: only the changed table part and a new manifest go up.
        Thread.sleep(5) // distinct manifest timestamps
        wallet.save(TxnEntity(type = TxnType.EXPENSE, amountFils = 1_250, fromPocketId = pocket, note = "Shawarma", timestamp = clock.millis()))
        settings.setUserName("Adam")
        val second = engine.backup(client)
        assertTrue(second.newBackup)
        assertTrue(second.uploadedBytes < first.uploadedBytes)
        val list = engine.list(client())
        assertEquals(2, list.size)
        assertTrue(list[0] > list[1])
        val status = accounts.current().status
        assertEquals(2, status.backups)
        assertEquals(drive.files.values.sumOf { it.second.size.toLong() }, status.usedBytes)

        // Restore the older one: the second expense and the new name are gone again.
        engine.restore(list[1], client)
        assertEquals(listOf("Service"), wallet.txns.first().filter { it.type == TxnType.EXPENSE }.map { it.note })
        assertEquals("آدم", settings.current().userName)
        assertEquals(20_000L - 350, wallet.snapshot.first().balances[pocket])

        // A new phone: nothing local, restore the newest.
        snapshots.clearAll()
        assertTrue(wallet.txns.first().isEmpty())
        assertFalse(settings.current().onboardingDone)
        engine.restore(null, client)
        assertEquals(setOf("Service", "Shawarma"), wallet.txns.first().filter { it.type == TxnType.EXPENSE }.map { it.note }.toSet())
        assertEquals(20_000L - 350 - 1_250, wallet.snapshot.first().balances[pocket])
        assertEquals("Adam", settings.current().userName)
        assertEquals(AppLanguage.ENGLISH, settings.current().language)
        assertTrue(settings.current().onboardingDone)
        assertEquals(3, wallet.snapshot.first().pockets.size)
    }

    @Test fun `room version and snapshot version agree, foreign or newer files are refused`() = runBlocking {
        assertEquals(SnapshotStore.DB_VERSION, db.openHelper.readableDatabase.version)
        val settings = SettingsRepository(context)
        val snapshots = SnapshotStore(db, settings)
        val wallet = WalletRepository(db, clock)
        wallet.ensureSeeded()
        val s = snapshots.take()
        assertTrue(s.tables.none { it.table in Snapshot.SkipTables })
        assertTrue(s.tables.any { it.table == "txn" } && s.tables.any { it.table == "pocket" })
        for (bad in listOf(s.copy(dbVersion = 99), s.copy(app = "hyperathlete"))) {
            try { snapshots.apply(bad.copy(tables = emptyList())); fail() } catch (_: SnapshotException) { }
        }
        assertEquals(3, wallet.snapshot.first().pockets.size)
    }

    @Test fun `app lock - wrong pins are counted, then a wait, the right pin resets`() = runBlocking {
        val lock = AppLock(context)
        lock.setPin("4821")
        assertFalse(lock.locked.value)
        assertTrue(lock.current().locks)
        val t = 1_000_000L
        repeat(4) { i -> assertEquals(PinCheck.Wrong(4 - i, 0), lock.check("0000", t)) }
        val fifth = lock.check("0000", t) as PinCheck.Wrong
        assertEquals(t + 30_000, fifth.waitUntil)
        // Even the right PIN waits.
        assertEquals(PinCheck.Wait(t + 30_000), lock.check("4821", t + 1_000))
        assertEquals(PinCheck.Ok, lock.check("4821", t + 30_000))
        assertEquals(0, lock.current().failures)

        lock.setTimeout(LockTimeout.FIVE_MINUTES)
        assertEquals(LockTimeout.FIVE_MINUTES, lock.current().timeout)
        lock.disable()
        assertFalse(lock.current().locks)
        assertEquals(null, lock.current().pinHash)
    }

    /** appDataFolder in memory: list, multipart upload, download, delete — the calls DriveClient makes. */
    private class FakeDrive : Dispatcher() {
        val files = linkedMapOf<String, Pair<String, ByteArray>>() // id → (name, bytes)
        val tokens = mutableListOf<String>()
        private var next = 1

        @Synchronized
        override fun dispatch(request: RecordedRequest): MockResponse {
            tokens += request.headers["Authorization"].orEmpty()
            val url = request.url
            val path = url.encodedPath
            return when {
                request.method == "GET" && path == "/drive/v3/files" -> {
                    val now = DateTimeFormatter.ISO_INSTANT.format(Instant.parse("2026-10-17T10:00:00Z"))
                    val items = files.entries.joinToString(",") { (id, f) ->
                        """{"id":"$id","name":"${f.first}","size":"${f.second.size}","createdTime":"$now"}"""
                    }
                    json("""{"files":[$items]}""")
                }
                request.method == "POST" && path == "/upload/drive/v3/files" -> {
                    val boundary = request.headers["Content-Type"]!!.substringAfter("boundary=").trim('"')
                    val raw = String(request.body!!.toByteArray(), Charsets.ISO_8859_1)
                    val parts = raw.split("--$boundary").map { it.removePrefix("\r\n") }.filter { it.contains("\r\n\r\n") }
                    val meta = parts[0].substringAfter("\r\n\r\n")
                    val name = Regex("\"name\":\"([^\"]+)\"").find(meta)!!.groupValues[1]
                    val data = parts[1].substringAfter("\r\n\r\n").removeSuffix("\r\n").toByteArray(Charsets.ISO_8859_1)
                    val id = "f${next++}"
                    files[id] = name to data
                    json("""{"id":"$id"}""")
                }
                request.method == "GET" && path.startsWith("/drive/v3/files/") -> {
                    val f = files[path.removePrefix("/drive/v3/files/")] ?: return MockResponse.Builder().code(404).build()
                    MockResponse.Builder().code(200).body(Buffer().write(f.second)).build()
                }
                request.method == "DELETE" -> { files.remove(path.removePrefix("/drive/v3/files/")); MockResponse.Builder().code(204).build() }
                else -> MockResponse.Builder().code(400).build()
            }
        }

        private fun json(s: String) = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(s).build()
    }
}
