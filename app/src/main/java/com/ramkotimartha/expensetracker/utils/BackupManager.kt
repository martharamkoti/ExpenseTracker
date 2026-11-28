package com.ramkotimartha.expensetracker.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.ramkotimartha.expensetracker.BuildConfig
import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.Transaction
import com.ramkotimartha.expensetracker.data.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BackupPayload(
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val appVersion: String,
    val tags: List<Tag>,
    val transactions: List<Transaction>
)

data class BackupFileItem(
    val uri: Uri,
    val displayName: String,
    val lastModifiedMillis: Long,
    val sizeBytes: Long
)

enum class RestoreMode {
    REPLACE_ALL,
    MERGE
}

/**
 * Handles `.mtbak` backup serialization, validation, directory URI persistence,
 * and Storage Access Framework (SAF) directory scanning without requiring storage permissions.
 */
object BackupManager {

    const val BACKUP_EXTENSION = ".mtbak"
    private const val PREFS_NAME = "backup_prefs"
    private const val KEY_DIRECTORY_URI = "backup_directory_uri"
    private const val APP_IDENTIFIER = "MoneyTrack"
    private const val CURRENT_SCHEMA_VERSION = 1

    private val FILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ENGLISH)

    fun getSavedDirectoryUri(context: Context): Uri? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_DIRECTORY_URI, null)?.let { Uri.parse(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun saveDirectoryUri(context: Context, treeUri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: Exception) {
            // Best-effort if URI came from test environment
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DIRECTORY_URI, treeUri.toString())
            .apply()
    }

    fun hasValidDirectoryAccess(context: Context, treeUri: Uri?): Boolean {
        if (treeUri == null) return false
        return try {
            val persisted = context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isReadPermission && it.isWritePermission
            }
            persisted
        } catch (_: Exception) {
            false
        }
    }

    fun formatReadableDirectoryName(treeUri: Uri?): String? {
        if (treeUri == null) return null
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri) // e.g. "primary:Documents/Backups"
            val decoded = URLDecoder.decode(docId, "UTF-8")
            when {
                decoded.startsWith("primary:") -> "Internal Storage/${decoded.removePrefix("primary:")}".trimEnd('/')
                decoded.contains(":") -> decoded.replaceFirst(":", "/")
                else -> decoded
            }
        } catch (_: Exception) {
            treeUri.lastPathSegment
        }
    }

    fun buildBackupFileName(now: LocalDateTime = LocalDateTime.now()): String {
        return "MoneyTrack_Backup_${now.format(FILE_TIMESTAMP_FORMAT)}$BACKUP_EXTENSION"
    }

    fun serializeBackup(
        tags: List<Tag>,
        transactions: List<Transaction>,
        exportedAtMillis: Long = System.currentTimeMillis()
    ): String {
        val root = JSONObject()
        root.put("app", APP_IDENTIFIER)
        root.put("schemaVersion", CURRENT_SCHEMA_VERSION)
        root.put("exportedAt", exportedAtMillis)
        root.put("appVersion", BuildConfig.VERSION_NAME)

        val tagsArray = JSONArray()
        for (tag in tags) {
            val obj = JSONObject()
            obj.put("id", tag.id)
            obj.put("name", tag.name)
            obj.put("type", tag.type.name)
            if (tag.iconKey != null) {
                obj.put("iconKey", tag.iconKey)
            }
            tagsArray.put(obj)
        }
        root.put("tags", tagsArray)

        val txArray = JSONArray()
        for (tx in transactions) {
            val obj = JSONObject()
            obj.put("id", tx.id)
            obj.put("amount", tx.amount)
            obj.put("timestamp", tx.timestamp)
            if (tx.note != null) {
                obj.put("note", tx.note)
            }
            obj.put("tagId", tx.tagId)
            obj.put("type", tx.type.name)
            txArray.put(obj)
        }
        root.put("transactions", txArray)

        return root.toString(2)
    }

    fun deserializeBackup(jsonContent: String): BackupPayload {
        val trimmed = jsonContent.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
            throw IllegalArgumentException("Invalid backup file format. Note: .csv and .pdf reports cannot be used to restore data.")
        }

        val root = try {
            JSONObject(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("Unreadable backup file. Please select a valid MoneyTrack (.mtbak) file.", e)
        }

        val appId = root.optString("app", "")
        if (appId != APP_IDENTIFIER) {
            throw IllegalArgumentException("This file is not a MoneyTrack backup (.mtbak) file.")
        }

        val schemaVersion = root.optInt("schemaVersion", 1)
        val exportedAt = root.optLong("exportedAt", 0L)
        val appVersion = root.optString("appVersion", "1.0")

        val tagsJson = root.getJSONArray("tags")
        val tags = ArrayList<Tag>(tagsJson.length())
        val validTagIds = HashSet<Long>()
        for (i in 0 until tagsJson.length()) {
            val obj = tagsJson.getJSONObject(i)
            val id = obj.getLong("id")
            val name = obj.getString("name").trim()
            val type = TransactionType.valueOf(obj.getString("type"))
            val iconKey = if (obj.has("iconKey") && !obj.isNull("iconKey")) {
                obj.getString("iconKey").takeIf { it.isNotBlank() }
            } else {
                null
            }
            if (name.isNotEmpty()) {
                tags.add(Tag(id = id, name = name, type = type, iconKey = iconKey))
                validTagIds.add(id)
            }
        }

        val txJson = root.getJSONArray("transactions")
        val transactions = ArrayList<Transaction>(txJson.length())
        for (i in 0 until txJson.length()) {
            val obj = txJson.getJSONObject(i)
            val id = obj.optLong("id", 0L)
            val amount = obj.getDouble("amount")
            val timestamp = obj.getLong("timestamp")
            val note = if (obj.has("note") && !obj.isNull("note")) {
                obj.getString("note").takeIf { it.isNotBlank() }
            } else {
                null
            }
            val tagId = obj.getLong("tagId")
            val type = TransactionType.valueOf(obj.getString("type"))
            if (amount > 0.0 && tagId in validTagIds) {
                transactions.add(
                    Transaction(
                        id = id,
                        amount = amount,
                        timestamp = timestamp,
                        note = note,
                        tagId = tagId,
                        type = type
                    )
                )
            }
        }

        return BackupPayload(
            schemaVersion = schemaVersion,
            exportedAtMillis = exportedAt,
            appVersion = appVersion,
            tags = tags,
            transactions = transactions
        )
    }

    suspend fun writeBackupToDirectory(
        context: Context,
        treeUri: Uri,
        tags: List<Tag>,
        transactions: List<Transaction>
    ): BackupFileItem = withContext(Dispatchers.IO) {
        val fileName = buildBackupFileName()
        val json = serializeBackup(tags, transactions)
        val bytes = json.toByteArray(Charsets.UTF_8)

        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val createdUri = DocumentsContract.createDocument(
            context.contentResolver,
            parentDocUri,
            "application/octet-stream",
            fileName
        ) ?: throw IllegalStateException("Could not create backup file in the selected directory.")

        context.contentResolver.openOutputStream(createdUri, "w")?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: throw IllegalStateException("Could not write to the backup file.")

        BackupFileItem(
            uri = createdUri,
            displayName = fileName,
            lastModifiedMillis = System.currentTimeMillis(),
            sizeBytes = bytes.size.toLong()
        )
    }

    suspend fun listBackupsInDirectory(
        context: Context,
        treeUri: Uri
    ): List<BackupFileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<BackupFileItem>()
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val mime = cursor.getString(mimeCol) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue

                    val name = cursor.getString(nameCol) ?: continue
                    val lower = name.lowercase(Locale.ROOT)
                    if (lower.endsWith(BACKUP_EXTENSION) ||
                        (lower.startsWith("moneytrack_backup") && lower.endsWith(".json"))
                    ) {
                        val docId = cursor.getString(idCol)
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val lastMod = if (cursor.isNull(modCol)) 0L else cursor.getLong(modCol)
                        val size = if (cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol)
                        results.add(
                            BackupFileItem(
                                uri = fileUri,
                                displayName = name,
                                lastModifiedMillis = lastMod,
                                sizeBytes = size
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            CrashlyticsLogger.log(e, "Failed to list backup files in directory")
        }
        results.sortedByDescending { it.lastModifiedMillis }
    }

    suspend fun readBackupFromUri(context: Context, fileUri: Uri): BackupPayload =
        withContext(Dispatchers.IO) {
            val content = context.contentResolver.openInputStream(fileUri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw IllegalStateException("Unable to open the selected backup file.")

            deserializeBackup(content)
        }

    fun formatTimestamp(epochMillis: Long): String {
        if (epochMillis <= 0L) return "Unknown date"
        val dt = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
        return dt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy · hh:mm a", Locale.ENGLISH))
    }
}
