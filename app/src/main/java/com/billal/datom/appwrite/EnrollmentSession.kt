package com.billal.datom.appwrite

import android.content.Context
import io.appwrite.Client
import io.appwrite.services.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Redeems an administrator-issued, short-lived Appwrite custom token.
 * No server API key is used. A session is not proof of hardware ownership
 * and does not grant permission to synchronize SMS.
 */
object EnrollmentSession {
    private fun account(context: Context): Account =
        Account(
            Client(context.applicationContext)
                .setEndpoint(AppwriteConfig.DEFAULT_ENDPOINT)
                .setProject(AppwriteConfig.DEFAULT_PROJECT_ID)
        )

    suspend fun currentUser(context: Context): String =
        withContext(Dispatchers.IO) { account(context).get().id }

    suspend fun redeem(context: Context, userId: String, secret: String): String =
        withContext(Dispatchers.IO) {
            require(userId.isNotBlank() && secret.isNotBlank())
            val account = account(context)
            account.createSession(userId = userId.trim(), secret = secret.trim())
            account.get().id
        }

    suspend fun disconnect(context: Context) = withContext(Dispatchers.IO) {
        account(context).deleteSession(sessionId = "current")
    }
}
