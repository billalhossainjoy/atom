package com.billal.datom.sms

import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SmsMessage(
    val id: Long,
    val sender: String,
    val body: String,
    val date: Long,
)

object SmsStore {
    private val _messages = MutableStateFlow<List<SmsMessage>>(emptyList())
    val messages: StateFlow<List<SmsMessage>> = _messages.asStateFlow()

    fun addIncoming(sender: String, body: String) {
        val msg = SmsMessage(id = System.currentTimeMillis(), sender = sender, body = body, date = System.currentTimeMillis())
        _messages.value = listOf(msg) + _messages.value
    }

    fun replaceAll(messages: List<SmsMessage>) {
        _messages.value = messages
    }

    fun loadInbox(context: Context) {
        _messages.value = readInbox(context)
    }

    suspend fun refreshInbox(context: Context) {
        val inbox = withContext(Dispatchers.IO) {
            try {
                readInbox(context)
            } catch (_: SecurityException) {
                emptyList()
            }
        }
        _messages.value = inbox
    }

    fun readInbox(context: Context): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
            ),
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addrCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                messages += SmsMessage(
                    id = cursor.getLong(idCol),
                    sender = cursor.getString(addrCol).orEmpty(),
                    body = cursor.getString(bodyCol).orEmpty(),
                    date = cursor.getLong(dateCol),
                )
            }
        }
        return messages
    }
}
