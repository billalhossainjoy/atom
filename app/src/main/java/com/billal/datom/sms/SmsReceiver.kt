package com.billal.datom.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Read the system inbox instead of generating a second synthetic SMS ID.
                // The foreground content observer also refreshes after provider changes.
                SmsStore.refreshInbox(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
