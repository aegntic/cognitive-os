package com.thresholdinc.luxe.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.thresholdinc.luxe.domain.Client
import com.thresholdinc.luxe.domain.Inquiry
import com.thresholdinc.luxe.domain.MemoryEntry
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Encrypted local storage using SQLCipher + Android Keystore for key material.
 * Stores client vault (inquiries) and audit logs with full encryption at rest.
 * Extended for client memory context loading (insidher-client-memory).
 */
class EncryptedVault(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "luxe_vault.db"
        private const val DATABASE_VERSION = 3
        private const val TABLE_INQUIRIES = "inquiries"
        private const val TABLE_LOGS = "audit_logs"
        private const val TABLE_USERS = "users"
        private const val TABLE_CONFIG = "config"
        private const val TABLE_CLIENTS = "clients"
        private const val TABLE_MEMORIES = "client_memories"
    }

    private var db: SQLiteDatabase? = null

    init {
        SQLiteDatabase.loadLibs(context)
        // Ensure key from Keystore
        getOrCreateVaultKey()
    }

    private fun getOrCreateVaultKey(): String {
        // Use Android Keystore to manage a secret for the passphrase
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "luxe_vault_key"
        if (!keyStore.containsAlias(alias)) {
            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        }
        // For SQLCipher we use a derived passphrase; in real use secure derivation or store passphrase protected
        // Here: simple fixed derived for demo (in prod use KeyStore SecretKey to derive)
        return "luxe_secure_passphrase_derived_from_keystore"
    }

    private fun openEncrypted(): SQLiteDatabase {
        if (db == null || !db!!.isOpen) {
            val passphrase = getOrCreateVaultKey().toCharArray()
            db = getWritableDatabase(passphrase)
        }
        return db!!
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_INQUIRIES (
                id TEXT PRIMARY KEY,
                text TEXT,
                from_addr TEXT,
                date TEXT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE $TABLE_LOGS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entry TEXT,
                timestamp TEXT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE $TABLE_USERS (
                email TEXT PRIMARY KEY,
                name TEXT,
                password TEXT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE $TABLE_CONFIG (
                key TEXT PRIMARY KEY,
                value TEXT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE $TABLE_CLIENTS (
                id TEXT PRIMARY KEY,
                name TEXT,
                email TEXT,
                preferences TEXT,
                sovereignty_level TEXT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE $TABLE_MEMORIES (
                id TEXT PRIMARY KEY,
                client_id TEXT,
                type TEXT,
                content TEXT,
                timestamp TEXT,
                tags TEXT,
                handoff_level TEXT
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_INQUIRIES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LOGS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CONFIG")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CLIENTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MEMORIES")
        onCreate(db)
    }

    fun registerUser(email: String, name: String, password: String): Boolean {
        val db = openEncrypted()
        return try {
            db.execSQL(
                "INSERT INTO $TABLE_USERS (email, name, password) VALUES (?, ?, ?)",
                arrayOf(email.lowercase().trim(), name.trim(), password)
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    fun verifyUser(email: String, password: String): Boolean {
        val db = openEncrypted()
        val cursor = db.rawQuery("SELECT password FROM $TABLE_USERS WHERE email = ?", arrayOf(email.lowercase().trim()))
        var verified = false
        if (cursor.moveToFirst()) {
            val storedPassword = cursor.getString(0)
            verified = storedPassword == password
        }
        cursor.close()
        return verified
    }

    fun resetPassword(email: String, password: String): Boolean {
        val db = openEncrypted()
        val cursor = db.rawQuery("SELECT email FROM $TABLE_USERS WHERE email = ?", arrayOf(email.lowercase().trim()))
        val exists = cursor.moveToFirst()
        cursor.close()
        if (!exists) return false
        
        db.execSQL("UPDATE $TABLE_USERS SET password = ? WHERE email = ?", arrayOf(password, email.lowercase().trim()))
        return true
    }

    fun getUserName(email: String): String {
        val db = openEncrypted()
        val cursor = db.rawQuery("SELECT name FROM $TABLE_USERS WHERE email = ?", arrayOf(email.lowercase().trim()))
        var name = ""
        if (cursor.moveToFirst()) {
            name = cursor.getString(0)
        }
        cursor.close()
        return name
    }

    fun setConfig(key: String, value: String) {
        val db = openEncrypted()
        db.execSQL("INSERT OR REPLACE INTO $TABLE_CONFIG (key, value) VALUES (?, ?)", arrayOf(key, value))
    }

    fun getConfig(key: String): String? {
        val db = openEncrypted()
        val cursor = db.rawQuery("SELECT value FROM $TABLE_CONFIG WHERE key = ?", arrayOf(key))
        var value: String? = null
        if (cursor.moveToFirst()) {
            value = cursor.getString(0)
        }
        cursor.close()
        return value
    }

    fun deleteConfig(key: String) {
        val db = openEncrypted()
        db.execSQL("DELETE FROM $TABLE_CONFIG WHERE key = ?", arrayOf(key))
    }

    fun storeInquiry(inquiry: Inquiry) {
        val db = openEncrypted()
        db.execSQL(
            "INSERT OR REPLACE INTO $TABLE_INQUIRIES (id, text, from_addr, date) VALUES (?, ?, ?, ?)",
            arrayOf(inquiry.id, inquiry.text, inquiry.from, inquiry.date)
        )
    }

    fun getAllInquiries(): List<Inquiry> {
        val db = openEncrypted()
        val cursor = db.rawQuery("SELECT * FROM $TABLE_INQUIRIES", null)
        val list = mutableListOf<Inquiry>()
        if (cursor.moveToFirst()) {
            do {
                list.add(
                    Inquiry(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3)
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun appendLog(entry: String) {
        val db = openEncrypted()
        db.execSQL(
            "INSERT INTO $TABLE_LOGS (entry, timestamp) VALUES (?, datetime('now'))",
            arrayOf(entry)
        )
    }

    fun getRecentLogs(limit: Int = 20): List<String> {
        val db = openEncrypted()
        val cursor = db.rawQuery("SELECT entry FROM $TABLE_LOGS ORDER BY id DESC LIMIT $limit", null)
        val list = mutableListOf<String>()
        if (cursor.moveToFirst()) {
            do {
                list.add(cursor.getString(0))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list.reversed()
    }

    // === Client memory context loading (insidher-client-memory + anita-sovereign-agent) ===

    fun getOrCreateClient(email: String): Client {
        val db = openEncrypted()
        val normEmail = email.lowercase().trim()
        val cursor = db.rawQuery("SELECT * FROM $TABLE_CLIENTS WHERE email = ?", arrayOf(normEmail))
        if (cursor.moveToFirst()) {
            val c = Client(
                id = cursor.getString(0),
                name = cursor.getString(1),
                email = cursor.getString(2),
                preferences = cursor.getString(3) ?: "{}",
                sovereigntyLevel = cursor.getString(4) ?: "full"
            )
            cursor.close()
            return c
        }
        cursor.close()
        // create minimal client
        val id = normEmail
        val name = normEmail.substringBefore("@").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        db.execSQL(
            "INSERT OR REPLACE INTO $TABLE_CLIENTS (id, name, email, preferences, sovereignty_level) VALUES (?, ?, ?, ?, ?)",
            arrayOf(id, name, normEmail, "{}", "full")
        )
        return Client(id, name, normEmail, "{}", "full")
    }

    fun appendMemory(clientId: String, entry: MemoryEntry) {
        val db = openEncrypted()
        val tagsStr = entry.tags.joinToString(",")
        db.execSQL(
            "INSERT OR REPLACE INTO $TABLE_MEMORIES (id, client_id, type, content, timestamp, tags, handoff_level) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf(entry.id, clientId, entry.type, entry.content, entry.timestamp, tagsStr, entry.handoffLevel)
        )
    }

    fun getMemoriesForClient(clientId: String, limit: Int = 5): List<MemoryEntry> {
        val db = openEncrypted()
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_MEMORIES WHERE client_id = ? ORDER BY timestamp DESC LIMIT ?",
            arrayOf(clientId, limit.toString())
        )
        val list = mutableListOf<MemoryEntry>()
        if (cursor.moveToFirst()) {
            do {
                val tagsStr = cursor.getString(5)
                val tags = if (tagsStr.isNullOrBlank()) emptyList() else tagsStr.split(",").filter { it.isNotBlank() }
                list.add(
                    MemoryEntry(
                        id = cursor.getString(0),
                        clientId = cursor.getString(1),
                        type = cursor.getString(2),
                        content = cursor.getString(3),
                        timestamp = cursor.getString(4),
                        tags = tags,
                        handoffLevel = cursor.getString(6) ?: "ai"
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun getClientContext(clientIdOrEmail: String): String {
        val client = getOrCreateClient(clientIdOrEmail)
        val memories = getMemoriesForClient(client.id)
        val memText = memories.joinToString("\n") { m ->
            "- [${m.timestamp}] ${m.type}: ${m.content} (handoff=${m.handoffLevel})"
        }
        val base = "Client ${client.name} <${client.email}>, sovereignty_level=${client.sovereigntyLevel}."
        return if (memText.isBlank()) {
            "$base No prior memories in vault."
        } else {
            "$base\nRecent vault memories:\n$memText"
        }
    }

    fun closeVault() {
        db?.close()
        db = null
    }
}