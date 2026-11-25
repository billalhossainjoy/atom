package com.billal.datom.appwrite

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.billal.datom.sms.SmsMessage
import com.billal.datom.sms.SmsStore
import io.appwrite.Client
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.TablesDB
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/** Emits Unit whenever a validated internet connection becomes available. */
object NetworkMonitor {
    private val _online = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val online: SharedFlow<Unit> = _online.asSharedFlow()

    @Volatile
    private var registered = false

    fun start(context: Context) {
        if (registered) return
        registered = true
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _online.tryEmit(Unit)
            }
        })
    }
}

fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

object AppwriteSync {
    private const val TAG = "AppwriteSync"
    private const val PREFS_DEVICE = "device_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    @Volatile
    private var client: Client? = null
    @Volatile
    private var clientKey: String? = null

    private fun client(context: Context): Client {
        AppwriteConfig.load(context)
        val key = "${AppwriteConfig.endpoint}|${AppwriteConfig.projectId}|${AppwriteConfig.apiKey}"
        client?.takeIf { clientKey == key }?.let { return it }
        return synchronized(this) {
            client?.takeIf { clientKey == key }?.let { return it }
            Client(context.applicationContext).apply {
                setEndpoint(AppwriteConfig.endpoint)
                setProject(AppwriteConfig.projectId)
                addHeader("X-Appwrite-Key", AppwriteConfig.apiKey)
            }.also {
                client = it
                clientKey = key
            }
        }
    }

    /** Stable per-install device ID, persisted across app restarts. */
    fun deviceId(context: Context): String {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    private fun conversationRowId(address: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(address.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)
        return "conv_$digest"
    }

    private fun rowIdFor(sms: SmsMessage): String = "sms_${sms.id}"

    private fun SmsMessage.toRowData(deviceId: String): Map<String, Any?> = mapOf(
        "sender" to sender,
        "body" to body,
        "date" to Instant.ofEpochMilli(date).toString(),
        "deviceId" to deviceId,
        "conversationId" to conversationRowId(sender),
    )

    private fun Map<String, Any?>.toSmsMessage(rowId: String): SmsMessage? {
        val sender = this["sender"] as? String ?: return null
        val body = this["body"] as? String ?: return null
        val dateStr = this["date"] as? String ?: return null
        val epoch = runCatching { Instant.parse(dateStr).toEpochMilli() }.getOrNull() ?: return null
        val id = rowId.removePrefix("sms_").toLongOrNull() ?: rowId.hashCode().toLong()
        return SmsMessage(
            id = id,
            sender = sender,
            body = body,
            date = epoch,
        )
    }

    /** Register/update this device in the devices table. */
    private suspend fun registerDevice(context: Context, tables: TablesDB) {
        val id = deviceId(context)
        tables.upsertRow(
            databaseId = AppwriteConfig.databaseId,
            tableId = AppwriteConfig.TABLE_DEVICES,
            rowId = id,
            data = mapOf(
                "deviceId" to id,
                "name" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "platform" to "android",
                "lastSeen" to Instant.now().toString(),
            ),
        )
    }

    private suspend fun upsertConversation(
        tables: TablesDB,
        sender: String,
        body: String,
        dateIso: String,
    ) {
        tables.upsertRow(
            databaseId = AppwriteConfig.databaseId,
            tableId = AppwriteConfig.TABLE_CONVERSATIONS,
            rowId = conversationRowId(sender),
            data = mapOf(
                "address" to sender,
                "lastMessage" to body.take(500),
                "lastDate" to dateIso,
                "unreadCount" to 0,
            ),
        )
    }

    /** Returns true if the configured tables are reachable. */
    suspend fun testConnection(context: Context): Boolean = withContext(Dispatchers.IO) {
        AppwriteConfig.load(context)
        if (!AppwriteConfig.isConfigured()) return@withContext false
        return@withContext try {
            TablesDB(client(context)).listRows(
                databaseId = AppwriteConfig.databaseId,
                tableId = AppwriteConfig.tableId,
            )
            true
        } catch (e: AppwriteException) {
            Log.e(TAG, "testConnection failed: ${e.code} ${e.type}: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "testConnection failed", e)
            false
        }
    }

    /** Upload a single message (+ its conversation + device) to the cloud. */
    suspend fun upload(context: Context, sms: SmsMessage) = withContext(Dispatchers.IO) {
        AppwriteConfig.load(context)
        if (!AppwriteConfig.isConfigured()) return@withContext
        try {
            val tables = TablesDB(client(context))
            val deviceId = deviceId(context)
            val dateIso = Instant.ofEpochMilli(sms.date).toString()
            upsertConversation(tables, sms.sender, sms.body, dateIso)
            tables.upsertRow(
                databaseId = AppwriteConfig.databaseId,
                tableId = AppwriteConfig.tableId,
                rowId = rowIdFor(sms),
                data = sms.toRowData(deviceId),
            )
        } catch (e: AppwriteException) {
            Log.e(TAG, "upload failed: ${e.code} ${e.type}: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "upload failed", e)
        }
    }

    /**
     * Full sync: register this device, upload all local inbox messages,
     * then pull cloud rows and replace the in-memory store.
     */
    suspend fun syncAll(context: Context) = withContext(Dispatchers.IO) {
        AppwriteConfig.load(context)
        if (!AppwriteConfig.isConfigured()) return@withContext

        val tables = TablesDB(client(context))
        try {
            registerDevice(context, tables)
        } catch (e: Exception) {
            Log.e(TAG, "registerDevice failed", e)
        }

        val deviceId = deviceId(context)
        val local = runCatching { SmsStore.readInbox(context) }.getOrDefault(emptyList())

        // One conversation row per sender, using that sender's newest message
        val latestPerSender = local.groupBy { it.sender }
            .mapValues { (_, msgs) -> msgs.maxBy { it.date } }
        for ((_, sms) in latestPerSender) {
            try {
                upsertConversation(
                    tables, sms.sender, sms.body,
                    Instant.ofEpochMilli(sms.date).toString()
                )
            } catch (e: Exception) {
                Log.e(TAG, "conversation upsert failed", e)
            }
        }

        for (sms in local) {
            try {
                tables.upsertRow(
                    databaseId = AppwriteConfig.databaseId,
                    tableId = AppwriteConfig.tableId,
                    rowId = rowIdFor(sms),
                    data = sms.toRowData(deviceId),
                )
            } catch (e: Exception) {
                Log.e(TAG, "upload ${sms.id} failed", e)
            }
        }

        try {
            val result = tables.listRows(
                databaseId = AppwriteConfig.databaseId,
                tableId = AppwriteConfig.tableId,
            )
            val cloud = result.rows.mapNotNull { row ->
                row.data?.toSmsMessage(row.id.orEmpty())
            }
            if (cloud.isNotEmpty()) {
                SmsStore.replaceAll(
                    cloud.sortedByDescending { it.date }.take(100)
                )
            }
        } catch (e: AppwriteException) {
            Log.e(TAG, "fetch failed: ${e.code} ${e.type}: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "fetch failed", e)
        }
    }
}
