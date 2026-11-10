package com.adampay.app.core.backup

import android.database.Cursor
import android.util.Base64
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.adampay.app.BuildConfig
import com.adampay.app.data.db.AdamDatabase
import com.adampay.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Up to [Snapshot.CHUNK_ROWS] rows of one table. Old chunks don't change, so backups only upload new ones. */
@Serializable
data class TableChunk(val table: String, val part: Int, val columns: List<String>, val rows: List<List<JsonElement>>)

/** Settings value with its type tag: b, i, l, f, d, s, ss (string set). */
@Serializable
data class SettingValue(val t: String, val v: JsonElement)

/** Everything the user has: every wallet table + settings. Split into chunks, it is the Drive backup. */
@Serializable
data class Snapshot(
    val format: Int = FORMAT,
    val app: String = APP,
    val dbVersion: Int,
    val appVersion: String = "",
    val createdAt: Long,
    val tables: List<TableChunk>,
    val settings: Map<String, SettingValue>,
) {
    companion object {
        const val FORMAT = 1
        const val APP = "adampay"
        const val CHUNK_ROWS = 500

        /** SQLite / Room bookkeeping, not data. */
        val SkipTables = setOf("android_metadata", "room_master_table", "sqlite_sequence")

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encodeSettings(raw: Map<String, Any>): Map<String, SettingValue> = raw.mapNotNull { (k, v) ->
            val sv = when (v) {
                is Boolean -> SettingValue("b", JsonPrimitive(v))
                is Int -> SettingValue("i", JsonPrimitive(v))
                is Long -> SettingValue("l", JsonPrimitive(v))
                is Float -> SettingValue("f", JsonPrimitive(v))
                is Double -> SettingValue("d", JsonPrimitive(v))
                is String -> SettingValue("s", JsonPrimitive(v))
                is Set<*> -> SettingValue("ss", JsonArray(v.map { JsonPrimitive(it.toString()) }))
                else -> null
            }
            sv?.let { k to it }
        }.toMap()

        fun decodeSettings(map: Map<String, SettingValue>): Map<String, Any> = map.mapNotNull { (k, sv) ->
            val v: Any? = runCatching {
                when (sv.t) {
                    "b" -> sv.v.jsonPrimitive.booleanOrNull
                    "i" -> sv.v.jsonPrimitive.longOrNull?.toInt()
                    "l" -> sv.v.jsonPrimitive.longOrNull
                    "f" -> sv.v.jsonPrimitive.doubleOrNull?.toFloat()
                    "d" -> sv.v.jsonPrimitive.doubleOrNull
                    "s" -> sv.v.jsonPrimitive.contentOrNull
                    "ss" -> sv.v.jsonArray.map { it.jsonPrimitive.content }.toSet()
                    else -> null
                }
            }.getOrNull()
            v?.let { k to it }
        }.toMap()

        /** One SQLite cell → JSON (blobs as {"b64": …}). */
        fun cell(c: Cursor, i: Int): JsonElement = when (c.getType(i)) {
            Cursor.FIELD_TYPE_NULL -> JsonNull
            Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(c.getLong(i))
            Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(c.getDouble(i))
            Cursor.FIELD_TYPE_BLOB -> JsonObject(mapOf("b64" to JsonPrimitive(Base64.encodeToString(c.getBlob(i), Base64.NO_WRAP))))
            else -> JsonPrimitive(c.getString(i))
        }

        /** JSON → bind argument for execSQL. */
        fun arg(e: JsonElement): Any? = when (e) {
            is JsonNull -> null
            is JsonObject -> e["b64"]?.jsonPrimitive?.content?.let { Base64.decode(it, Base64.NO_WRAP) }
            is JsonPrimitive -> when {
                e.isString -> e.content
                e.booleanOrNull != null -> if (e.booleanOrNull == true) 1L else 0L
                e.content.any { it == '.' || it == 'e' || it == 'E' } -> e.doubleOrNull
                else -> e.longOrNull ?: e.doubleOrNull
            }
            else -> e.toString()
        }
    }
}

class SnapshotException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NOT_OURS, NEWER_APP }
}

/** Takes and applies [Snapshot]s straight from SQLite, so new tables in later phases are included automatically. */
@Singleton
class SnapshotStore @Inject constructor(
    private val db: AdamDatabase,
    private val settings: SettingsRepository,
) {
    val dbVersion: Int get() = DB_VERSION

    suspend fun take(): Snapshot = withContext(Dispatchers.IO) {
        val sql = db.openHelper.readableDatabase
        val chunks = tables(sql).flatMap { t -> readTable(sql, t) }
        Snapshot(
            dbVersion = DB_VERSION, appVersion = BuildConfig.VERSION_NAME, createdAt = System.currentTimeMillis(),
            tables = chunks, settings = Snapshot.encodeSettings(settings.rawSnapshot()),
        )
    }

    /** Replaces all history and settings with [s] (one transaction: all or nothing). */
    suspend fun apply(s: Snapshot) {
        if (s.app != Snapshot.APP || s.format > Snapshot.FORMAT) throw SnapshotException(SnapshotException.Reason.NOT_OURS)
        if (s.dbVersion > DB_VERSION) throw SnapshotException(SnapshotException.Reason.NEWER_APP)
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val sql = db.openHelper.writableDatabase
                sql.execSQL("PRAGMA defer_foreign_keys = ON")
                val existing = tables(sql)
                // Wipe everything that is backed up, then refill it from the snapshot.
                existing.forEach { sql.execSQL("DELETE FROM `$it`") }
                val byTable = s.tables.groupBy { it.table }
                for (t in existing) {
                    val parts = byTable[t] ?: continue
                    val have = columns(sql, t)
                    for (chunk in parts.sortedBy { it.part }) {
                        val idx = chunk.columns.withIndex().filter { it.value in have }
                        if (idx.isEmpty()) continue
                        val stmt = "INSERT OR REPLACE INTO `$t` (" + idx.joinToString(",") { "`${it.value}`" } + ") VALUES (" + idx.joinToString(",") { "?" } + ")"
                        for (row in chunk.rows) sql.execSQL(stmt, idx.map { Snapshot.arg(row.getOrElse(it.index) { JsonNull }) }.toTypedArray())
                    }
                }
            }
        }
        settings.restoreRaw(Snapshot.decodeSettings(s.settings))
    }

    /** Every table emptied and settings back to a fresh install (tests only; the app has no "clear data" button). */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val sql = db.openHelper.writableDatabase
                sql.execSQL("PRAGMA defer_foreign_keys = ON")
                tables(sql).forEach { runCatching { sql.execSQL("DELETE FROM `$it`") } }
            }
        }
        settings.clearAll()
    }

    private fun tables(sql: SupportSQLiteDatabase): List<String> =
        sql.query("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }.filter { it !in Snapshot.SkipTables && !it.startsWith("sqlite_") && !it.startsWith("room_") }

    private fun columns(sql: SupportSQLiteDatabase, table: String): Set<String> =
        sql.query("PRAGMA table_info(`$table`)").use { c ->
            val i = c.getColumnIndexOrThrow("name")
            buildSet { while (c.moveToNext()) add(c.getString(i)) }
        }

    private fun readTable(sql: SupportSQLiteDatabase, table: String): List<TableChunk> =
        sql.query("SELECT * FROM `$table` ORDER BY rowid").use { c ->
            val cols = c.columnNames.toList()
            val rows = buildList { while (c.moveToNext()) add(cols.indices.map { Snapshot.cell(c, it) }) }
            rows.chunked(Snapshot.CHUNK_ROWS).mapIndexed { i, r -> TableChunk(table, i, cols, r) }
                .ifEmpty { listOf(TableChunk(table, 0, cols, emptyList())) }
        }

    companion object {
        /** Keep in step with @Database(version = …). */
        const val DB_VERSION = 1
    }
}
