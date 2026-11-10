package com.adampay.app.core.backup

import com.adampay.app.core.auth.GoogleAuth
import com.adampay.app.data.account.AccountStore
import com.adampay.app.domain.account.BackupStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** A file in the app's hidden Drive folder. */
data class DriveFile(val id: String, val name: String, val size: Long, val created: Instant)

/** One backup = a small manifest listing the chunk files it needs. Chunks are shared between backups. */
@Serializable
data class Manifest(
    val format: Int = 1,
    val createdAt: Long,
    val dbVersion: Int,
    val appVersion: String,
    val chunks: List<String>,
    val settings: String,
)

/** The pure part of the backup: naming, chunk encoding and the 3 GB rule. Unit-tested. */
object BackupPlan {
    const val CAP_BYTES = 3L * 1024 * 1024 * 1024
    val KEEP_AT_LEAST: Duration = Duration.ofDays(30)

    fun isManifest(name: String) = name.startsWith("m-") && name.endsWith(".json")
    fun isChunk(name: String) = name.startsWith("c-") && name.endsWith(".gz")
    fun manifestName(createdAt: Long) = "m-$createdAt.json"
    fun createdAt(name: String): Long? = if (isManifest(name)) name.removePrefix("m-").removeSuffix(".json").toLongOrNull() else null

    /** Content-addressed: the same table part with the same rows always gets the same name. */
    fun chunkName(json: ByteArray): String = "c-" + MessageDigest.getInstance("SHA-256").digest(json).joinToString("") { "%02x".format(it) }.take(40) + ".gz"

    fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(bytes) } }.toByteArray()
    fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }

    data class Encoded(val manifest: Manifest, val files: Map<String, ByteArray>)

    fun encode(s: Snapshot): Encoded {
        val files = LinkedHashMap<String, ByteArray>()
        val chunkNames = s.tables.map { t ->
            val raw = Snapshot.json.encodeToString(TableChunk.serializer(), t).toByteArray()
            chunkName(raw).also { files[it] = gzip(raw) }
        }
        val settingsRaw = Snapshot.json.encodeToString(SettingsChunk.serializer(), SettingsChunk(s.settings)).toByteArray()
        val settingsName = chunkName(settingsRaw).also { files[it] = gzip(settingsRaw) }
        return Encoded(Manifest(createdAt = s.createdAt, dbVersion = s.dbVersion, appVersion = s.appVersion, chunks = chunkNames, settings = settingsName), files)
    }

    fun decode(m: Manifest, files: Map<String, ByteArray>): Snapshot = Snapshot(
        dbVersion = m.dbVersion, appVersion = m.appVersion, createdAt = m.createdAt,
        tables = m.chunks.map { Snapshot.json.decodeFromString(TableChunk.serializer(), String(gunzip(files.getValue(it)))) },
        settings = Snapshot.json.decodeFromString(SettingsChunk.serializer(), String(gunzip(files.getValue(m.settings)))).values,
    )

    fun refs(m: Manifest): Set<String> = (m.chunks + m.settings).toSet()

    /**
     * The 3 GB rule: while over [cap], drop the oldest backup that is older than a month — never the
     * newest one, which always holds the whole history — and any chunk no remaining backup needs.
     * Under the cap nothing is deleted. Returns the files to delete.
     */
    fun prune(files: List<DriveFile>, manifests: Map<String, Manifest>, now: Instant, cap: Long = CAP_BYTES): List<DriveFile> {
        var total = files.sumOf { it.size }
        if (total <= cap) return emptyList()
        val byName = files.associateBy { it.name }
        val kept = files.filter { isManifest(it.name) && it.name in manifests }.sortedBy { manifests.getValue(it.name).createdAt }.toMutableList()
        val newest = kept.lastOrNull()
        val deleted = mutableListOf<DriveFile>()
        fun dropUnreferenced() {
            val needed = kept.flatMap { refs(manifests.getValue(it.name)) }.toSet()
            files.filter { isChunk(it.name) && it.name !in needed && it !in deleted }.forEach { deleted += it; total -= it.size }
        }
        // Leftovers of interrupted backups / unreadable manifests go first.
        files.filter { isManifest(it.name) && it.name !in manifests && it != newest }.forEach { deleted += it; total -= it.size }
        dropUnreferenced()
        while (total > cap) {
            val oldest = kept.firstOrNull() ?: break
            if (oldest == newest) break
            if (Duration.between(Instant.ofEpochMilli(manifests.getValue(oldest.name).createdAt), now) < KEEP_AT_LEAST) break
            kept.removeAt(0); deleted += oldest; total -= oldest.size
            dropUnreferenced()
        }
        return deleted.filter { byName.containsKey(it.name) }
    }
}

@Serializable
data class SettingsChunk(val values: Map<String, SettingValue>)

/** Minimal Drive v3 REST client for the appDataFolder (only this app can see it). */
class DriveClient(
    private val http: OkHttpClient,
    private val token: String,
    private val api: String = API,
    private val uploadApi: String = UPLOAD,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun call(req: Request.Builder): ByteArray =
        http.newCall(req.header("Authorization", "Bearer $token").build()).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw DriveAuthException(r.code)
            if (!r.isSuccessful) throw IOException("Drive HTTP ${r.code}")
            r.body.bytes()
        }

    fun list(): List<DriveFile> {
        val out = mutableListOf<DriveFile>()
        var page: String? = null
        do {
            val url = "$api/files?spaces=appDataFolder&pageSize=1000&fields=nextPageToken,files(id,name,size,createdTime)" + (page?.let { "&pageToken=$it" } ?: "")
            val o = json.parseToJsonElement(String(call(Request.Builder().url(url)))).jsonObject
            o["files"]?.jsonArray?.forEach { f ->
                val fo = f.jsonObject
                out += DriveFile(
                    fo.s("id")!!, fo.s("name").orEmpty(), fo["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0,
                    fo.s("createdTime")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH,
                )
            }
            page = o.s("nextPageToken")
        } while (page != null)
        return out
    }

    fun upload(name: String, bytes: ByteArray, mime: String) {
        val meta = """{"name":"$name","parents":["appDataFolder"]}""".toRequestBody("application/json; charset=UTF-8".toMediaType())
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(meta).addPart(bytes.toRequestBody(mime.toMediaType())).build()
        call(Request.Builder().url("$uploadApi/files?uploadType=multipart&fields=id").post(body))
    }

    fun download(id: String): ByteArray = call(Request.Builder().url("$api/files/$id?alt=media"))

    fun delete(id: String) {
        http.newCall(Request.Builder().url("$api/files/$id").delete().header("Authorization", "Bearer $token").build()).execute().use { r ->
            if (!r.isSuccessful && r.code != 404) throw IOException("Drive HTTP ${r.code}")
        }
    }

    private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull

    companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    }
}

class DriveAuthException(val code: Int) : IOException("Drive auth $code")

/** What the backup screen shows while something runs. */
enum class BackupWork { BACKING_UP, RESTORING }

/** Backup to / restore from Google Drive. */
@Singleton
class BackupEngine @Inject constructor(
    private val google: GoogleAuth,
    private val store: AccountStore,
    private val snapshots: SnapshotStore,
    private val http: OkHttpClient,
) {
    private val mutex = Mutex()
    private val _running = MutableStateFlow<BackupWork?>(null)
    val running: StateFlow<BackupWork?> = _running

    /** Uploads only the chunks Drive doesn't have yet, then a manifest (skipped when nothing changed). */
    suspend fun backup(): BackupResult = backup { DriveClient(http, google.token()) }

    /** [drive] is a parameter so tests can run the whole backup against a fake Drive server. */
    internal suspend fun backup(drive: suspend () -> DriveClient): BackupResult = mutex.withLock {
        _running.value = BackupWork.BACKING_UP
        try {
            val drive = drive()
            val encoded = BackupPlan.encode(snapshots.take())
            withContext(Dispatchers.IO) {
                var files = drive.list()
                val have = files.map { it.name }.toSet()
                var uploaded = 0L
                var created = false
                for ((name, bytes) in encoded.files) if (name !in have) { drive.upload(name, bytes, "application/gzip"); uploaded += bytes.size }
                val latest = files.filter { BackupPlan.isManifest(it.name) }.maxByOrNull { BackupPlan.createdAt(it.name) ?: 0 }
                val latestRefs = latest?.let { runCatching { BackupPlan.refs(readManifest(drive, it)) }.getOrNull() }
                // Nothing changed since the last backup: don't add another identical one.
                if (latestRefs != BackupPlan.refs(encoded.manifest)) {
                    val m = Snapshot.json.encodeToString(Manifest.serializer(), encoded.manifest).toByteArray()
                    drive.upload(BackupPlan.manifestName(encoded.manifest.createdAt), m, "application/json")
                    uploaded += m.size
                    created = true
                }
                files = drive.list()
                if (files.sumOf { it.size } > BackupPlan.CAP_BYTES) {
                    val manifests = files.filter { BackupPlan.isManifest(it.name) }
                        .mapNotNull { f -> runCatching { f.name to readManifest(drive, f) }.getOrNull() }.toMap()
                    val gone = BackupPlan.prune(files, manifests, Instant.now())
                    gone.forEach { runCatching { drive.delete(it.id) } }
                    files = files - gone.toSet()
                }
                store.setStatus(
                    BackupStatus(
                        lastAt = Instant.now(), lastUploadedBytes = uploaded, usedBytes = files.sumOf { it.size },
                        backups = files.count { BackupPlan.isManifest(it.name) }, lastError = null,
                    ),
                )
                BackupResult(uploaded, created)
            }
        } catch (e: Exception) {
            val old = store.current().status
            store.setStatus(old.copy(lastError = errorCode(e)))
            throw e
        } finally {
            _running.value = null
        }
    }

    /** Backups on Drive, newest first (their creation times). */
    suspend fun list(): List<Instant> = list(DriveClient(http, google.token()))

    internal suspend fun list(drive: DriveClient): List<Instant> = withContext(Dispatchers.IO) {
        drive.list().mapNotNull { BackupPlan.createdAt(it.name) }.sortedDescending().map(Instant::ofEpochMilli)
    }

    /** Replaces everything on this phone with the backup made at [at] (the newest when null). */
    suspend fun restore(at: Instant? = null) = restore(at) { DriveClient(http, google.token()) }

    internal suspend fun restore(at: Instant?, drive: suspend () -> DriveClient) = mutex.withLock {
        _running.value = BackupWork.RESTORING
        try {
            val drive = drive()
            val snapshot = withContext(Dispatchers.IO) {
                val files = drive.list()
                val manifests = files.filter { BackupPlan.createdAt(it.name) != null }
                val chosen = (if (at == null) manifests.maxByOrNull { BackupPlan.createdAt(it.name)!! }
                else manifests.firstOrNull { BackupPlan.createdAt(it.name) == at.toEpochMilli() }) ?: throw NoBackupException()
                val m = readManifest(drive, chosen)
                val byName = files.associateBy { it.name }
                val blobs = BackupPlan.refs(m).associateWith { n -> drive.download((byName[n] ?: throw IOException("missing $n")).id) }
                BackupPlan.decode(m, blobs)
            }
            snapshots.apply(snapshot)
            store.setStatus(store.current().status.copy(lastError = null))
        } finally {
            _running.value = null
        }
    }

    private fun readManifest(drive: DriveClient, f: DriveFile): Manifest =
        Snapshot.json.decodeFromString(Manifest.serializer(), String(drive.download(f.id)))

    companion object {
        fun errorCode(e: Throwable): String = when (e) {
            // 403 = the Drive API isn't switched on in the Google Cloud project (or the scope was refused).
            is DriveAuthException -> if (e.code == 403) "SETUP" else "SIGN_IN"
            is com.adampay.app.core.auth.NeedsSignInException -> "SIGN_IN"
            is com.google.android.gms.common.api.ApiException -> "SIGN_IN"
            is IOException -> "OFFLINE"
            else -> "FAILED"
        }
    }
}

data class BackupResult(val uploadedBytes: Long, val newBackup: Boolean)

class NoBackupException : Exception("no backup")
