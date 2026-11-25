package com.billal.datom.appwrite

import android.content.Context
import android.content.SharedPreferences

object AppwriteConfig {
    const val DEFAULT_ENDPOINT = "https://sgp.cloud.appwrite.io/v1"
    const val DEFAULT_PROJECT_ID = "6b0600f60013dd532f05"
    const val DEFAULT_DATABASE_ID = "6b06f1f7003b65907da2"
    const val DEFAULT_TABLE_ID = "messages"
    const val TABLE_DEVICES = "devices"
    const val TABLE_CONVERSATIONS = "conversations"

    private const val PREFS_NAME = "appwrite_config"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_PROJECT_ID = "project_id"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_DATABASE_ID = "database_id"
    private const val KEY_TABLE_ID = "table_id"

    private var prefs: SharedPreferences? = null

    var endpoint = DEFAULT_ENDPOINT
        private set
    var projectId = DEFAULT_PROJECT_ID
        private set
    var apiKey = ""
        private set
    var databaseId = DEFAULT_DATABASE_ID
        private set
    var tableId = DEFAULT_TABLE_ID
        private set

    private fun SharedPreferences.stringOrDefault(key: String, default: String): String =
        getString(key, null)?.takeIf { it.isNotBlank() } ?: default

    fun load(context: Context) {
        val p = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        endpoint = p.stringOrDefault(KEY_ENDPOINT, DEFAULT_ENDPOINT)
        projectId = p.stringOrDefault(KEY_PROJECT_ID, DEFAULT_PROJECT_ID)
        apiKey = p.getString(KEY_API_KEY, "") ?: ""
        databaseId = p.stringOrDefault(KEY_DATABASE_ID, DEFAULT_DATABASE_ID)
        tableId = p.stringOrDefault(KEY_TABLE_ID, DEFAULT_TABLE_ID)
    }

    fun save(
        context: Context,
        endpoint: String,
        projectId: String,
        apiKey: String,
        databaseId: String,
        tableId: String,
    ) {
        val p = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        p.edit()
            .putString(KEY_ENDPOINT, endpoint.trim())
            .putString(KEY_PROJECT_ID, projectId.trim())
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_DATABASE_ID, databaseId.trim())
            .putString(KEY_TABLE_ID, tableId.trim())
            .apply()
        load(context)
    }

    fun isConfigured(): Boolean = apiKey.isNotBlank()
}
